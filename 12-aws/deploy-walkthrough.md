# Deploy Walkthrough — FlowGrid M5 on AWS (Week 8)

> **Goal:** FlowGrid running on **EC2 (Docker Compose: ui = nginx + React build, api = Spring Boot, redis)**
> + **RDS PostgreSQL 16** + **S3** report exports + **IAM instance role** + **CloudWatch** logs and alarms,
> built step by step with the AWS CLI, recorded in `DEPLOYMENT.md`, and **torn down** at the end.
> Steps 0–4 and 7 can be done in Week 7; the rest in Week 8. The CI/CD deploy job that automates
> step 8 is in [`13-cicd/pipeline-examples.md`](../13-cicd/pipeline-examples.md#3-deploy-job--flowgrid-to-ec2-with-health-gate-and-rollback).
> The same skeleton is reused for LedgerX (W13), ForgeCI (W19 — two instances, see [§14](#14-the-multi-service-case-forgeci-week-19)) and FlagForge (W23, [§15](#15-repeating-it-ledgerx-w13-and-flagforge-w23)).

← [CloudWatch](./cloudwatch.md) · Also read: [cost-safety.md](./cost-safety.md) **before** step 1.

```
laptop ──aws cli──► AWS APIs
   │
   └─docker push──► GHCR ◄──docker pull── EC2 (public subnet)
                                           ├─ ui (nginx) :80 ──/api/*──► api :8080 ──► RDS :5432 (private)
                                           │   serves React build           │
                                           ├─ redis :6379 (internal only) ◄─┘ catalog + low-stock cache
                                           ├─ IAM role ──► S3 reports/*
                                           └─ awslogs ──► CloudWatch /flowgrid/*
```

Time: 6–8 hours the first time. Keep a text file open and **paste every ID you create** into it — it
becomes FlowGrid's `DEPLOYMENT.md` (the docs set in [`18-projects/flowgrid/docs-and-resume.md`](../18-projects/flowgrid/docs-and-resume.md)).

---

## Step 0 — Prerequisites

- [ ] Budget + billing alarm exist ([cost-safety.md](./cost-safety.md))
- [ ] AWS CLI v2 installed, admin profile with MFA configured ([iam.md](./iam.md#6-setting-up-your-own-account-safely-do-this-once))
- [ ] FlowGrid runs locally with `docker compose up` and `mvn verify` is green
- [ ] Two images build: `api` (multi-stage Maven → JRE) and `ui` (multi-stage `npm ci && npm run build` → nginx with your `nginx.conf`), see [`11-docker/dockerfiles.md`](../11-docker/dockerfiles.md)
- [ ] `application-prod.yml` reads DB/Redis/S3/region/JWT secret from environment variables
- [ ] The app starts and serves reads when Redis is unavailable (M3 degrade-to-DB requirement)

```bash
export AWS_PROFILE=pw
export AWS_REGION=eu-west-1          # pick one region and stay in it
aws sts get-caller-identity          # confirm account + identity
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
MY_IP=$(curl -s https://checkip.amazonaws.com)
TAG='{Key=project,Value=flowgrid}'
```

---

## Step 1 — Push images to a registry

Build on your laptop and push to GitHub Container Registry (GHCR). Token: a GitHub PAT (classic)
with `write:packages` — from Week 8 onward the pipeline does this for you.

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin

# Apple Silicon laptops: build for the instance's architecture!
TAG=$(git rev-parse HEAD)
docker build --platform linux/amd64 -t ghcr.io/<github-user>/flowgrid-api:$TAG .
docker build --platform linux/amd64 -t ghcr.io/<github-user>/flowgrid-ui:$TAG ./frontend
docker push ghcr.io/<github-user>/flowgrid-api:$TAG
docker push ghcr.io/<github-user>/flowgrid-ui:$TAG
```

Packages are private by default. Either make them public (fine for a portfolio project — no secrets
in images!) or `docker login` on the instance with a **read-only** `read:packages` token.

---

## Step 2 — Network

```bash
VPC_ID=$(aws ec2 create-vpc --cidr-block 10.0.0.0/16 \
  --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=flowgrid-vpc},$TAG]" \
  --query Vpc.VpcId --output text)
aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-hostnames '{"Value":true}'

AZ_A=$(aws ec2 describe-availability-zones --query 'AvailabilityZones[0].ZoneName' --output text)
AZ_B=$(aws ec2 describe-availability-zones --query 'AvailabilityZones[1].ZoneName' --output text)

SUBNET_PUBLIC_A=$(aws ec2 create-subnet --vpc-id "$VPC_ID" --cidr-block 10.0.1.0/24 \
  --availability-zone "$AZ_A" --query Subnet.SubnetId --output text)
SUBNET_PRIVATE_A=$(aws ec2 create-subnet --vpc-id "$VPC_ID" --cidr-block 10.0.11.0/24 \
  --availability-zone "$AZ_A" --query Subnet.SubnetId --output text)
SUBNET_PRIVATE_B=$(aws ec2 create-subnet --vpc-id "$VPC_ID" --cidr-block 10.0.12.0/24 \
  --availability-zone "$AZ_B" --query Subnet.SubnetId --output text)

IGW_ID=$(aws ec2 create-internet-gateway --query InternetGateway.InternetGatewayId --output text)
aws ec2 attach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID"

RTB_PUBLIC=$(aws ec2 create-route-table --vpc-id "$VPC_ID" --query RouteTable.RouteTableId --output text)
aws ec2 create-route --route-table-id "$RTB_PUBLIC" \
  --destination-cidr-block 0.0.0.0/0 --gateway-id "$IGW_ID"
RTB_ASSOC=$(aws ec2 associate-route-table --route-table-id "$RTB_PUBLIC" \
  --subnet-id "$SUBNET_PUBLIC_A" --query AssociationId --output text)
aws ec2 modify-subnet-attribute --subnet-id "$SUBNET_PUBLIC_A" --map-public-ip-on-launch
```

Private subnets stay on the VPC's **main** route table (only the `local` route) → no internet path.

**Checkpoint:** in the console, VPC → Subnets: the public subnet's route table shows `0.0.0.0/0 → igw-…`.

---

## Step 3 — Security groups

```bash
SG_APP=$(aws ec2 create-security-group --group-name flowgrid-app \
  --description "FlowGrid EC2" --vpc-id "$VPC_ID" --query GroupId --output text)
SG_DB=$(aws ec2 create-security-group --group-name flowgrid-db \
  --description "FlowGrid RDS" --vpc-id "$VPC_ID" --query GroupId --output text)

aws ec2 authorize-security-group-ingress --group-id "$SG_APP" --protocol tcp --port 22  --cidr "$MY_IP/32"
aws ec2 authorize-security-group-ingress --group-id "$SG_APP" --protocol tcp --port 80  --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id "$SG_APP" --protocol tcp --port 443 --cidr 0.0.0.0/0

aws ec2 authorize-security-group-ingress --group-id "$SG_DB" \
  --ip-permissions "IpProtocol=tcp,FromPort=5432,ToPort=5432,UserIdGroupPairs=[{GroupId=$SG_APP}]"
```

---

## Step 4 — IAM role + instance profile

Create `trust-ec2.json` and `flowgrid-app-policy.json` exactly as in [iam.md §4](./iam.md#4-the-flowgrid-policies)
(with your account ID, region and bucket name), then:

```bash
aws iam create-role --role-name flowgrid-ec2-role \
  --assume-role-policy-document file://trust-ec2.json
aws iam put-role-policy --role-name flowgrid-ec2-role \
  --policy-name flowgrid-app-policy --policy-document file://flowgrid-app-policy.json
aws iam attach-role-policy --role-name flowgrid-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam create-instance-profile --instance-profile-name flowgrid-ec2-profile
aws iam add-role-to-instance-profile --instance-profile-name flowgrid-ec2-profile \
  --role-name flowgrid-ec2-role
sleep 10   # IAM is eventually consistent; run-instances may reject a brand-new profile
```

---

## Step 5 — S3 bucket and log groups

```bash
BUCKET=flowgrid-exports-$ACCOUNT_ID
aws s3api create-bucket --bucket "$BUCKET" \
  --create-bucket-configuration LocationConstraint="$AWS_REGION"
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

for g in api nginx; do
  aws logs create-log-group --log-group-name /flowgrid/$g
  aws logs put-retention-policy --log-group-name /flowgrid/$g --retention-in-days 7
done
```

---

## Step 6 — RDS PostgreSQL

```bash
aws rds create-db-subnet-group --db-subnet-group-name flowgrid-db-subnets \
  --db-subnet-group-description "FlowGrid private" \
  --subnet-ids "$SUBNET_PRIVATE_A" "$SUBNET_PRIVATE_B"

read -rs DB_MASTER_PASSWORD
aws rds create-db-instance --db-instance-identifier flowgrid-db \
  --engine postgres --engine-version 16 --db-instance-class db.t4g.micro \
  --allocated-storage 20 --storage-type gp3 --storage-encrypted \
  --db-name flowgrid --master-username pw_admin --master-user-password "$DB_MASTER_PASSWORD" \
  --db-subnet-group-name flowgrid-db-subnets --vpc-security-group-ids "$SG_DB" \
  --no-publicly-accessible --no-multi-az --backup-retention-period 1 \
  --tags Key=project,Value=flowgrid

# Launch EC2 (step 7) while this runs; then:
aws rds wait db-instance-available --db-instance-identifier flowgrid-db
DB_HOST=$(aws rds describe-db-instances --db-instance-identifier flowgrid-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)
```

Store the password in Parameter Store (encrypted, free standard tier):

```bash
aws ssm put-parameter --name /flowgrid/prod/db-password --type SecureString \
  --value "$DB_MASTER_PASSWORD"
```

(If you do this, add `ssm:GetParameter` on `arn:aws:ssm:REGION:ACCOUNT:parameter/flowgrid/prod/*`
to the role policy. For a first run, writing it into the instance's `.env` with `chmod 600` is acceptable.)

---

## Step 7 — EC2 instance

Use `user-data.sh` from [ec2.md §4](./ec2.md#4-user-data--install-docker-on-first-boot).

```bash
AMI_ID=$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)

aws ec2 create-key-pair --key-name pw-key --key-type ed25519 \
  --query KeyMaterial --output text > ~/.ssh/pw-key.pem && chmod 400 ~/.ssh/pw-key.pem

INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI_ID" --instance-type t3.small \
  --key-name pw-key --subnet-id "$SUBNET_PUBLIC_A" --security-group-ids "$SG_APP" \
  --iam-instance-profile Name=flowgrid-ec2-profile \
  --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":20,"VolumeType":"gp3"}}]' \
  --user-data file://user-data.sh \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=flowgrid-app},$TAG]" \
  --query 'Instances[0].InstanceId' --output text)

aws ec2 wait instance-status-ok --instance-ids "$INSTANCE_ID"
PUBLIC_IP=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
ssh -i ~/.ssh/pw-key.pem ec2-user@"$PUBLIC_IP" 'cloud-init status --wait && docker compose version'
```

---

## Step 8 — Configure and start the stack

On the instance (`ssh -i ~/.ssh/pw-key.pem ec2-user@$PUBLIC_IP`):

```bash
sudo mkdir -p /opt/flowgrid && sudo chown ec2-user: /opt/flowgrid && cd /opt/flowgrid
# copy compose.prod.yml (and deploy.sh from 13-cicd) from your laptop: scp -i ~/.ssh/pw-key.pem ... ec2-user@IP:/opt/flowgrid/
```

`/opt/flowgrid/.env` (chmod 600):

```bash
IMAGE_TAG=<git-sha-you-pushed>
SPRING_PROFILES_ACTIVE=prod
DB_HOST=flowgrid-db.xxxxxxxx.eu-west-1.rds.amazonaws.com
DB_USER=pw_admin
DB_PASSWORD=change-me
FLOWGRID_S3_BUCKET=flowgrid-exports-123456789012
AWS_REGION=eu-west-1
SPRING_DATA_REDIS_HOST=redis
JWT_SECRET=generate-with-openssl-rand-base64-48
```

`compose.prod.yml` (the production shape — no Postgres container, RDS replaces it):

```yaml
services:
  api:
    image: ghcr.io/<github-user>/flowgrid-api:${IMAGE_TAG}
    env_file: .env
    environment:
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=60"
    mem_limit: 900m
    depends_on: [redis]
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 15s
      timeout: 3s
      retries: 5
      start_period: 60s
    logging:
      driver: awslogs
      options: { awslogs-region: eu-west-1, awslogs-group: /flowgrid/api, awslogs-stream: api }

  redis:
    image: redis:7-alpine
    command: ["redis-server", "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru"]
    restart: unless-stopped            # no "ports:" → not reachable from outside the host

  ui:
    image: ghcr.io/<github-user>/flowgrid-ui:${IMAGE_TAG}   # nginx: React build + proxy /api → api:8080
    ports: ["80:80"]
    depends_on: [api]
    restart: unless-stopped
    logging:
      driver: awslogs
      options: { awslogs-region: eu-west-1, awslogs-group: /flowgrid/nginx, awslogs-stream: nginx }
```

(Healthcheck uses `wget`; if your runtime image lacks it, use `curl` or drop to a TCP check. `.env`
values are placed in the environment literally — no quotes needed.)

```bash
docker compose -f compose.prod.yml --env-file .env pull
docker compose -f compose.prod.yml --env-file .env up -d
docker compose -f compose.prod.yml ps
```

Flyway runs on API startup and creates the schema in RDS.

---

## Step 9 — Verify (write each result into the runbook)

```bash
# From the instance
aws sts get-caller-identity                        # assumed-role/flowgrid-ec2-role
nc -zv <rds-endpoint> 5432                          # "Connected" (sudo dnf install -y nmap-ncat)
curl -s localhost/actuator/health                   # {"status":"UP"} via nginx (if proxied)

# From your laptop (adapt paths to your API; these match a typical FlowGrid design)
curl -i http://$PUBLIC_IP/actuator/health
TOKEN=$(curl -s -X POST http://$PUBLIC_IP/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ops@example.com","password":"..."}' | jq -r .accessToken)
curl -s "http://$PUBLIC_IP/api/v1/inventory?warehouseId=1&page=0&size=20" -H "Authorization: Bearer $TOKEN"
KEY=$(uuidgen)
for i in 1 2; do                                   # same Idempotency-Key twice → same order, one reservation
  curl -s -X POST http://$PUBLIC_IP/api/v1/orders -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' \
    -d '{"lines":[{"skuId":1,"quantity":2}],"shippingPriority":"STANDARD"}'; echo
done
curl -s -X POST http://$PUBLIC_IP/api/v1/reports/inventory -H "Authorization: Bearer $TOKEN"
# → {"downloadUrl":"https://flowgrid-exports-...amazonaws.com/reports/inventory/...csv?X-Amz-...", ...}

aws logs tail /flowgrid/api --since 5m
aws s3 ls s3://$BUCKET/reports/ --recursive
```

- [ ] Health UP · login works · dashboard loads at `http://$PUBLIC_IP/` · repeated Idempotency-Key returns the same order · CSV in S3 · presigned URL downloads, then expires
- [ ] Run the k6 baseline from your laptop **or** from a second small instance in the same region, and record where it ran — network distance is part of the methodology

---

## Step 10 — Alarms

Create the SNS topic, CPU, status-check and API-error alarms from [cloudwatch.md §5](./cloudwatch.md#5-alarms--sns--email).
Test with `aws cloudwatch set-alarm-state` and confirm the email arrives.

---

## Step 11 — Break it on purpose (then fix)

| Break | Predict | Observe | Fix |
|---|---|---|---|
| `aws ec2 revoke-security-group-ingress --group-id $SG_DB --ip-permissions "IpProtocol=tcp,FromPort=5432,ToPort=5432,UserIdGroupPairs=[{GroupId=$SG_APP}]"` | Health DOWN, timeouts | `docker logs` → Hikari timeout | Re-authorize |
| Remove `s3:PutObject` from the policy | Export returns 500 | `AccessDenied` in `/flowgrid/api` | Restore policy |
| `docker compose stop redis` | Reads still work, slower; WARN logs | App degrades to DB (M3 requirement) — if it 500s instead, that's a bug to fix | Start Redis; check cache refills |
| Wrong `DB_PASSWORD` in `.env` + redeploy | App fails at startup | `FATAL: password authentication failed` | Fix `.env`; automated rollback is in the CI deploy job |
| Reboot instance | Stack returns by itself | `restart: unless-stopped` + docker enabled | — |

---

## Step 12 — Teardown (reverse order)

Resources depend on each other; deleting in the wrong order fails with `DependencyViolation`.

```bash
# 1. Compute
aws ec2 terminate-instances --instance-ids "$INSTANCE_ID"
aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID"
# (if you allocated one) aws ec2 release-address --allocation-id "$ALLOC_ID"
aws ec2 delete-key-pair --key-name pw-key

# 2. Database
aws rds delete-db-instance --db-instance-identifier flowgrid-db \
  --skip-final-snapshot --delete-automated-backups
aws rds wait db-instance-deleted --db-instance-identifier flowgrid-db
aws rds delete-db-subnet-group --db-subnet-group-name flowgrid-db-subnets
aws rds describe-db-snapshots --query 'DBSnapshots[].DBSnapshotIdentifier'   # should be empty

# 3. Storage, logs, parameters, alarms
aws s3 rm "s3://$BUCKET" --recursive
aws s3api delete-bucket --bucket "$BUCKET"
for g in api nginx; do aws logs delete-log-group --log-group-name /flowgrid/$g; done
aws ssm delete-parameter --name /flowgrid/prod/db-password
aws cloudwatch delete-alarms --alarm-names flowgrid-cpu-high flowgrid-status-check flowgrid-api-errors
aws sns delete-topic --topic-arn "$TOPIC_ARN"

# 4. IAM
aws iam remove-role-from-instance-profile --instance-profile-name flowgrid-ec2-profile \
  --role-name flowgrid-ec2-role
aws iam delete-instance-profile --instance-profile-name flowgrid-ec2-profile
aws iam delete-role-policy --role-name flowgrid-ec2-role --policy-name flowgrid-app-policy
aws iam detach-role-policy --role-name flowgrid-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
aws iam delete-role --role-name flowgrid-ec2-role

# 5. Network (sg-db references sg-app → delete sg-db first)
aws ec2 delete-security-group --group-id "$SG_DB"
aws ec2 delete-security-group --group-id "$SG_APP"
aws ec2 disassociate-route-table --association-id "$RTB_ASSOC"
aws ec2 delete-route-table --route-table-id "$RTB_PUBLIC"
aws ec2 detach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID"
aws ec2 delete-internet-gateway --internet-gateway-id "$IGW_ID"
for s in "$SUBNET_PUBLIC_A" "$SUBNET_PRIVATE_A" "$SUBNET_PRIVATE_B"; do
  aws ec2 delete-subnet --subnet-id "$s"; done
aws ec2 delete-vpc --vpc-id "$VPC_ID"

# 6. Verify nothing tagged remains (some resources linger in the index for a while)
aws resourcegroupstaggingapi get-resources --tag-filters Key=project,Values=flowgrid \
  --query 'ResourceTagMappingList[].ResourceARN'
```

If `delete-security-group` fails with `DependencyViolation`, an ENI still uses it (RDS takes a few
minutes to release) — wait and retry. Keep the **billing alarm and budget**; they cost nothing.

Then walk through the [teardown checklist](./cost-safety.md#teardown-checklist) in the console the next day.

---

## Step 13 — Evidence for interviews

- [ ] `DEPLOYMENT.md` in the FlowGrid repo: diagram, every step, verify commands, teardown, time taken
- [ ] Screenshots: dashboard running on AWS, CloudWatch log search, alarm email (the deployment will be gone — screenshots stay)
- [ ] ADR: "EC2 + Compose vs ECS/Fargate" and "Redis on EC2 vs ElastiCache" ([template](../18-projects/templates/adr.md))
- [ ] k6 baseline recorded with environment, load shape and methodology ([template](../18-projects/templates/benchmark-report.md)) — only measured numbers ever become résumé bullets
- [ ] 2-minute spoken explanation: request path, credentials path, what you'd change for production
      (ALB + HTTPS via ACM, Multi-AZ RDS, ElastiCache, ECS, Secrets Manager, CI deploy via OIDC)

---

## 14. The multi-service case: ForgeCI (Week 19)

ForgeCI is the first project with **two kinds of process**: the API (webhooks, UI, SSE logs) and
**workers** that start Docker containers to run untrusted build steps. That changes the deployment.

```
                 GitHub webhooks (HTTPS)
                          │
               ┌──────────▼───────────┐        sg-api: 80/443 from anywhere
               │ EC2 api              │        (+ 6379 from sg-worker only)
               │ ui + api + redis     │
               └───┬──────────▲───────┘
      5432 from    │          │ 6379 (queue BLMOVE, pub/sub logs, heartbeats)
      sg-api and   │          │
      sg-worker    ▼          │
            ┌──────────┐  ┌───┴──────────────────────┐   sg-worker: NO inbound
            │ RDS      │◄─┤ EC2 worker(s)            │   (SSM for shell access)
            └──────────┘  │ worker app + Docker      │──► outbound 443: GitHub, image registries
                          │ /var/run/docker.sock     │
                          │ build containers (temp)  │
                          └──────────────────────────┘
```

What changes compared with FlowGrid:

| Concern | Decision to make and document |
|---|---|
| **Why a separate worker instance** | Build steps are arbitrary code. Isolating them from the API host limits the blast radius and lets you size/scale workers independently |
| **Docker socket** | Mounting `/var/run/docker.sock` into the worker container gives it **root-equivalent control of that host**. Accept it only on a dedicated worker host that holds no secrets beyond what the worker needs |
| **Worker IAM role** | Minimal or none — no S3, no SSM parameters it doesn't need. Build containers can reach IMDS unless you block it: keep `HttpPutResponseHopLimit=1` on worker hosts so build containers **can't** fetch the instance role credentials (the opposite of the API host setting in [ec2.md §5](./ec2.md#5-imdsv2-and-docker)) |
| **Build container limits** | Per-job CPU/memory limits, timeout (M4), no `--privileged`, no host mounts except the workspace, remove containers in `finally` |
| **Network** | `sg-worker` has no inbound rules; outbound only what's needed. Redis on the API host allows 6379 **only from `sg-worker`**, with `requirepass`/ACL set; never public |
| **Secrets** | Workers get DB/Redis credentials via env from Parameter Store at boot; repository tokens are short-lived and scoped |
| **Scaling** | Adding a worker = launching another instance from the same user data; the queue distributes jobs; leases + heartbeats (M4) recover jobs from a worker that disappears |
| **Deploy order** | Migrations run by the API; deploy API first, then workers; workers drain (graceful shutdown) before replacement |

### SQS vs Redis for the job queue (you must be able to argue this)

| | Redis list + `BLMOVE` / Streams (ForgeCI M2 default) | Amazon SQS |
|---|---|---|
| Already in the stack | Yes (also used for pub/sub logs, limits) | New managed dependency; LocalStack or a real queue for dev/tests |
| Durability | Depends on Redis persistence (AOF) and the instance surviving | Managed, replicated across AZs |
| "Lease" semantics | You build it: processing list + lease expiry + reaper (M4) | Built in: **visibility timeout**; message reappears if not deleted |
| Dead-letter queue | You build it | Built in (redrive policy after N receives) |
| Delivery | At-least-once if implemented correctly | At-least-once (standard); FIFO queues for ordering/dedupe within limits |
| Latency | Sub-millisecond, blocking pop | Long polling (up to 20 s wait), tens of ms |
| Pub/sub for live logs | Yes (same Redis) | No — you'd still need Redis or SNS/another channel |
| Cost at this scale | Part of an instance you already pay for | Effectively free at hobby volume (per-request pricing) |

A defensible answer: *"I kept Redis because it already carried pub/sub and concurrency limits, and
implementing leases taught me exactly what SQS's visibility timeout does. In production with several
worker hosts I'd move the queue to SQS for managed durability and DLQs and keep Redis for pub/sub."*
Record it as an ADR.

Checklist for ForgeCI's deploy:

- [ ] Two security groups, worker with no inbound; Redis reachable only from `sg-worker`
- [ ] Worker hosts: hop limit 1, minimal role, Docker installed via user data, worker app as a container with the socket mounted (documented risk) or as a systemd service
- [ ] Kill a worker instance mid-build → job is re-queued after lease expiry and completes on another worker (screenshot + log excerpt in `failure-engineering` notes)
- [ ] `DEPLOYMENT.md` security section: Docker socket risk, IMDS, secrets on worker hosts, what you'd use at scale (ephemeral VMs per job, Firecracker-style microVMs, rootless/sandboxed runtimes — awareness only)

---

## 15. Repeating it: LedgerX (W13) and FlagForge (W23)

Reuse the scripts — record how long each redeploy takes.

| Project | Differences from FlowGrid |
|---|---|
| **LedgerX** | Money data: `--backup-retention-period 7`, deletion protection on while it's live, test a point-in-time restore once ([rds.md §7](./rds.md#7-backups-and-restore)); DB password in SSM Parameter Store, not `.env`; the app DB role cannot `UPDATE`/`DELETE` ledger entries (grants mirror the DB-level immutability). Redis optional (idempotency cache / rate limit) |
| **FlagForge** | SSE through nginx needs `proxy_buffering off;`, `proxy_read_timeout` above your heartbeat interval, and `proxy_http_version 1.1` on the stream location; Redis is on the hot path (config snapshots + pub/sub) — decide Redis-on-EC2 vs ElastiCache and write down why; run the SDK sample app against the deployed API and test SDK behaviour when the API is unreachable (stale-if-error) |

