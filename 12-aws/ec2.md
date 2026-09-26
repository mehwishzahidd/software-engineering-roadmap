# EC2 + a Minimal VPC

> **Practical use:** one virtual machine running PulseWatch's Docker Compose stack, in a public
> subnet, reachable on 80/443, SSH only from your IP (or no SSH at all via Session Manager).
> **Interview use:** security group vs NACL, public vs private subnet, AMI, user data, "how would you scale this?"

← [IAM](./iam.md) · Next: [S3](./s3.md)

---

## 1. EC2 vocabulary

| Term | Meaning | P4 choice |
|---|---|---|
| **AMI** | Machine image: OS + preinstalled software; region-specific ID | Amazon Linux 2023 (latest, looked up via SSM parameter) |
| **Instance type** | CPU/RAM family + size: `t3.micro` = burstable, 2 vCPU, 1 GiB | `t3.small` (2 GiB) is comfortable; `t3.micro` works with tight JVM limits |
| **Burstable (t-family)** | Earns CPU credits when idle, spends when busy; "unlimited" mode can bill extra | Watch `CPUCreditBalance` metric |
| **Key pair** | SSH public key AWS puts on the instance; you keep the private `.pem` | `pw-key` (or none if using SSM) |
| **Security group (SG)** | Stateful virtual firewall on the network interface; **allow rules only** | `sg-app` |
| **EBS volume** | Network block storage = the disk. Survives stop; deleted on terminate by default (root) | 20 GiB gp3 |
| **User data** | Script run once at first boot (as root) via cloud-init | Install Docker + Compose |
| **Elastic IP** | Static public IPv4 you own until released | Optional; costs money (all public IPv4 is billed hourly since 2024) |
| **Instance profile** | Attaches an IAM role | `pulsewatch-ec2-profile` |
| **Stop vs terminate** | Stop = VM off, disk kept, (EBS still billed). Terminate = gone | Terminate at teardown |

Instance families at a glance: `t` burstable general, `m` general, `c` compute, `r` memory,
`g`/`p` GPU. Suffix `g` = Graviton (ARM, cheaper) — your images must be built for `arm64` then.

---

## 2. Minimal VPC — just enough networking

| Concept | Meaning |
|---|---|
| **VPC** | Isolated private network in one region, e.g. `10.0.0.0/16` (65,536 addresses) |
| **Subnet** | A slice of the VPC CIDR **in one Availability Zone**, e.g. `10.0.1.0/24` (256 addresses, 251 usable — AWS reserves 5) |
| **Internet Gateway (IGW)** | Attached to the VPC; lets resources with public IPs reach/be reached from the internet |
| **Route table** | Rules "destination CIDR → target". Every subnet is associated with exactly one |
| **Public subnet** | Its route table has `0.0.0.0/0 → igw-…`. That's the whole definition |
| **Private subnet** | No route to the IGW. Instances can't be reached from the internet |
| **NAT Gateway** | Lets private instances make *outbound* calls. **~\$30+/month + data** — P4 avoids it |
| **DB subnet group** | RDS needs subnets in **≥ 2 AZs**, even for single-AZ instances |

```
VPC 10.0.0.0/16
├── public-a   10.0.1.0/24   (AZ a)  rtb-public: 10.0.0.0/16→local, 0.0.0.0/0→igw
├── private-a  10.0.11.0/24  (AZ a)  rtb-private: 10.0.0.0/16→local
└── private-b  10.0.12.0/24  (AZ b)  rtb-private
```

The `local` route is automatic — everything inside the VPC can route to everything else; **security
groups** decide what's actually allowed.

The default VPC (every region has one, all subnets public) is fine for a first EC2 experiment. For
P4 build your own so you can explain every piece. Full CLI in [deploy-walkthrough.md](./deploy-walkthrough.md#step-2--network).

### Security group vs Network ACL

| | Security group | Network ACL |
|---|---|---|
| Attached to | Network interface (instance, RDS, …) | Subnet |
| Stateful? | **Yes** — return traffic automatically allowed | **No** — must allow ephemeral return ports (1024–65535) explicitly |
| Rules | Allow only | Allow **and** Deny, numbered, first match wins |
| Can reference | Other security groups (`sg-db` allows `sg-app`) | CIDRs only |
| Default | New SG: deny all in, allow all out | Default NACL: allow all |
| Typical use | Your main tool | Coarse subnet-wide blocks (e.g. deny a malicious CIDR) |

P4 leaves NACLs at default and uses SGs:

| SG | Inbound | Why |
|---|---|---|
| `sg-app` | TCP 22 from `MY_IP/32` (or none with SSM) | Admin access |
| `sg-app` | TCP 80, 443 from `0.0.0.0/0` | Public status page + API through nginx |
| `sg-db` | TCP 5432 **from `sg-app`** | Only the app can reach Postgres |

Note that the API port (8080) and Redis (6379) are **not** opened — nginx on 80 proxies to the API
over the Compose network; Redis is internal only. Exposing Redis to the internet is a classic breach.

---

## 3. Launching an instance (CLI)

```bash
# Latest Amazon Linux 2023 AMI for this region (x86_64)
AMI_ID=$(aws ssm get-parameter \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)

# Key pair (skip if you'll use Session Manager only)
aws ec2 create-key-pair --key-name pw-key --key-type ed25519 \
  --query KeyMaterial --output text > ~/.ssh/pw-key.pem
chmod 400 ~/.ssh/pw-key.pem

aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.small \
  --key-name pw-key \
  --subnet-id "$SUBNET_PUBLIC_A" \
  --security-group-ids "$SG_APP" \
  --associate-public-ip-address \
  --iam-instance-profile Name=pulsewatch-ec2-profile \
  --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":20,"VolumeType":"gp3","DeleteOnTermination":true}}]' \
  --user-data file://user-data.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=pulsewatch-app},{Key=project,Value=pulsewatch}]' \
  --query 'Instances[0].InstanceId' --output text
```

Check free-tier-eligible types in your account/region (terms vary by account age):

```bash
aws ec2 describe-instance-types \
  --filters Name=free-tier-eligible,Values=true \
  --query 'InstanceTypes[].InstanceType' --output text
```

---

## 4. User data — install Docker on first boot

`user-data.sh` (Amazon Linux 2023):

```bash
#!/bin/bash
set -euxo pipefail
dnf update -y
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose v2 plugin (not packaged in AL2023 repos)
ARCH=$(uname -m)   # x86_64 or aarch64
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Small swap file helps a 1–2 GiB instance survive JVM + Redis
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

- Logs of user data: `/var/log/cloud-init-output.log` — **first place to look** when "Docker isn't installed".
- User data runs **once**, as root, at first boot. Changing it later requires a stop/start and does not re-run by default.
- Ubuntu alternative: follow Docker's official apt repository instructions (docs.docker.com/engine/install/ubuntu) — `docker-compose-plugin` is packaged there.

Verify after SSH:

```bash
ssh -i ~/.ssh/pw-key.pem ec2-user@<public-ip>
docker version && docker compose version
cloud-init status --wait
```

No SSH? Session Manager (role needs `AmazonSSMManagedInstanceCore`, and the Session Manager plugin locally):

```bash
aws ssm start-session --target i-0abc123def4567890
```

---

## 5. IMDSv2 and Docker

- **IMDSv2** requires a session token (PUT) before metadata reads. It blocks SSRF attacks that trick
  an app into fetching `http://169.254.169.254/...` credentials. Always `HttpTokens=required`.
- Each network hop decrements the token response's TTL. A container on the default bridge network
  is 2 hops away → set `HttpPutResponseHopLimit=2` or the AWS SDK *inside the container* silently
  fails to get credentials.
- The Docker `awslogs` log driver runs in the **daemon on the host**, so it uses hop 1 and works either way.

```bash
aws ec2 modify-instance-metadata-options --instance-id "$INSTANCE_ID" \
  --http-tokens required --http-put-response-hop-limit 2
```

---

## 6. Elastic IP — do you need one?

A normal public IP changes on every **stop/start** (not on reboot). Options:

| Option | Cost | When |
|---|---|---|
| Auto-assigned public IP | Billed hourly like any public IPv4 | Demo; you update DNS/README after restarts |
| Elastic IP | Same hourly price while attached; **also billed if unattached** | You want a stable IP/DNS A record |
| ALB + DNS name | ALB hourly + LCU (not free) | Multiple instances; real production |

```bash
ALLOC_ID=$(aws ec2 allocate-address --domain vpc --query AllocationId --output text)
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$ALLOC_ID"
# teardown:
aws ec2 disassociate-address --association-id <assoc-id>
aws ec2 release-address --allocation-id "$ALLOC_ID"
```

---

## 7. Running the app

On the instance, the stack from [`11-docker/compose.md`](../11-docker/compose.md) runs with a
production override that drops the local Postgres (RDS replaces it). Keep secrets out of the repo:

```bash
# /opt/pulsewatch/.env   (chmod 600, owned by ec2-user; never committed)
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/pulsewatch
SPRING_DATASOURCE_USERNAME=pulsewatch_app
SPRING_DATASOURCE_PASSWORD=...
PULSEWATCH_S3_BUCKET=pulsewatch-exports-123456789012
AWS_REGION=eu-west-1
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60
```

Better (and a good interview talking point): store the DB password in **SSM Parameter Store
SecureString** or **Secrets Manager** and fetch it at boot with the instance role.

```bash
aws ssm get-parameter --name /pulsewatch/prod/db-password --with-decryption \
  --query Parameter.Value --output text
```

Restart on reboot: `restart: unless-stopped` on each Compose service and `systemctl enable docker`.

---

## 8. Break → Debug

| Break | Symptom | Debug |
|---|---|---|
| Remove port 80 rule from `sg-app` | Browser hangs (timeout, not "refused") | SG = silent drop; `curl -v --max-time 5 http://ip` |
| App binds `127.0.0.1:8080` inside container | Connection refused from nginx | `docker compose ps`, `ss -tlnp` on host, bind `0.0.0.0` |
| Route table missing `0.0.0.0/0 → igw` | SSH times out despite SG allowing 22 | VPC console → subnet → route table |
| JVM without memory limits on 1 GiB | Container `OOMKilled`, exit code 137 | `docker inspect --format '{{.State.OOMKilled}}'`, `dmesg` |
| Hop limit 1 | SDK: "Unable to load credentials" only inside container | Section 5 |

"Timeout vs refused" is a great interview nugget: **timeout** usually = firewall/SG/route dropping
packets; **connection refused** = packet arrived but nothing is listening on that port.

---

## 9. Interview questions

<details><summary>What makes a subnet public?</summary>

Its associated route table has a route for `0.0.0.0/0` to an Internet Gateway. Instances also need
a public IP to be reachable. Private subnets have no IGW route.
</details>

<details><summary>Security group vs NACL?</summary>

SGs are stateful allow-lists on network interfaces and can reference other SGs. NACLs are stateless,
subnet-level, ordered allow/deny rules. Stateless means return traffic on ephemeral ports must be
explicitly allowed.
</details>

<details><summary>How would you scale P4 beyond one instance?</summary>

Make the API stateless (already JWT + Redis), bake an AMI or move to ECS/Fargate, put an ALB in
front, run instances in an Auto Scaling Group across two AZs. The scheduler worker needs leader
election or partitioned monitors so checks aren't duplicated. Redis → ElastiCache. RDS → Multi-AZ,
add a read replica for the status page.
</details>

<details><summary>Stop vs terminate vs reboot?</summary>

Reboot keeps the public IP and disk. Stop keeps EBS (still billed) but releases the non-Elastic
public IP and the host. Terminate destroys the instance and (by default) its root volume.
</details>

<details><summary>Why did you run Postgres on RDS but Redis in Compose?</summary>

Postgres holds durable data needing backups and point-in-time recovery; RDS gives that without
running it myself. Redis in P4 holds cache and rate-limit counters that can be rebuilt, so losing it
is acceptable and ElastiCache would add cost. I'd move it for production.
</details>
