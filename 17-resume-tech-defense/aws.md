# AWS — Résumé Tech Defense

> **Goal:** defend "AWS" with truthful past context and current, hands-on knowledge of the five
> services this roadmap uses — **IAM, EC2, S3, RDS, CloudWatch** — plus enough VPC/security-group
> knowledge to deploy FlowGrid safely and cheaply — and then LedgerX, ForgeCI and FlagForge faster each time.
>
> **Honesty rule:** AWS is the résumé item interviewers most often probe for inflated claims.
> If past work was "deployed to an EC2 box someone else set up" or "used S3 buckets via the SDK",
> say exactly that. Never claim you designed infrastructure you didn't. Your depth claim comes
> from FlowGrid — the first deployment you did yourself in Week 8 — and from the three deployments
> that repeated and extended it (LedgerX W13, ForgeCI W19, FlagForge W23).

---

## Evidence in my projects

| Project | AWS evidence |
|---|---|
| **FlowGrid** M5 (W8) — first deploy | EC2 instance running the Docker Compose stack (api, redis, nginx serving the React dashboard); **RDS PostgreSQL** in private subnets (SG allows 5432 only from the EC2 SG); **S3** bucket for report exports (pre-signed download URLs); **IAM instance role** with least-privilege policy (only `s3:PutObject/GetObject` on `arn:aws:s3:::flowgrid-reports-*/*`, CloudWatch Logs write); **CloudWatch** log group via `awslogs` driver + alarm on 5xx / CPU; GitHub Actions deploys via OIDC-assumed role (no long-lived keys), images in GHCR |
| **LedgerX** M5 (W13) — second deploy | Same EC2 + RDS pattern rebuilt from the runbook in a fraction of the time; budget alarm + teardown script from day one |
| **ForgeCI** M6 (W19) — multi-service | api + N workers; worker EC2 with the Docker socket (security notes: socket access = root on the host, so workers get their own instance and SG); SQS considered vs Redis for the queue, decision recorded |
| **FlagForge** M4 (W23) — fourth deploy | Eval endpoint + SSE propagation behind nginx; Redis snapshot cache on EC2 (ElastiCache only if cheaper); p99 latency benchmarks recorded on the deployed instance |
| **Polish** (W24–26) | Runbooks per project, cost review, final deploy check, teardown |

## Where to learn it in this repo

- [`../12-aws/README.md`](../12-aws/README.md)
- [`../12-aws/iam.md`](../12-aws/iam.md) · [`../12-aws/ec2.md`](../12-aws/ec2.md) · [`../12-aws/s3.md`](../12-aws/s3.md) · [`../12-aws/rds.md`](../12-aws/rds.md) · [`../12-aws/cloudwatch.md`](../12-aws/cloudwatch.md)
- [`../12-aws/deploy-walkthrough.md`](../12-aws/deploy-walkthrough.md) — FlowGrid deploy step by step (the runbook you repeat for each later project)
- [`../12-aws/cost-safety.md`](../12-aws/cost-safety.md) — budgets, teardown, avoiding surprise bills

---

## 1. Beginner questions

<details><summary><b>B1. What are regions and availability zones?</b></summary>

A region is a geographic area (e.g. `eu-west-1`) with multiple isolated AZs (separate data centres with independent power/network, low-latency links). Deploying across AZs gives high availability; RDS Multi-AZ keeps a synchronous standby in another AZ.
</details>

<details><summary><b>B2. What is IAM? Users vs roles vs policies.</b></summary>

Identity and Access Management. **User**: long-lived identity (human; avoid access keys). **Role**: identity assumed temporarily, gives short-lived credentials (EC2 instance profile, CI via OIDC). **Policy**: JSON document of `Effect/Action/Resource/Condition`. Default deny; explicit deny wins.
</details>

<details><summary><b>B3. What is EC2?</b></summary>

Virtual machines. You pick an AMI, instance type (e.g. `t3.small`), subnet, security group, key pair or SSM access, and attach EBS volumes. You're responsible for the OS and everything above it.
</details>

<details><summary><b>B4. What is S3?</b></summary>

Object storage: buckets containing objects addressed by key; 11 nines durability; strongly consistent read-after-write. Block Public Access on by default; access via IAM/bucket policies; pre-signed URLs for temporary access; storage classes and lifecycle rules for cost.
</details>

<details><summary><b>B5. What is RDS, and what does it manage for you?</b></summary>

Managed relational databases (PostgreSQL, MySQL, ...). AWS handles provisioning, patching, automated backups + point-in-time restore, Multi-AZ failover, read replicas, monitoring. You still own schema, queries, indexes, parameter tuning, and connection management.
</details>

<details><summary><b>B6. What is CloudWatch?</b></summary>

Metrics (built-in e.g. EC2 CPU, RDS connections; custom metrics), Logs (log groups/streams, Logs Insights queries), Alarms (threshold on a metric → SNS notification/action), dashboards.
</details>

<details><summary><b>B7. Security group vs NACL?</b></summary>

Security group: stateful, instance/ENI-level, allow rules only; return traffic automatically allowed. NACL: stateless, subnet-level, allow + deny, ordered rules. Typical: SGs do the work; NACLs left default.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Explain least privilege with your FlowGrid policy.</b></summary>

```json
{ "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:PutObject","s3:GetObject"],
      "Resource": "arn:aws:s3:::flowgrid-reports-prod/*" },
    { "Effect": "Allow", "Action": ["logs:CreateLogStream","logs:PutLogEvents"],
      "Resource": "arn:aws:logs:*:*:log-group:/flowgrid/*:*" } ] }
```
Attached to the EC2 instance role; the app uses the default credential provider chain — no keys in env files.
</details>

<details><summary><b>I2. Public vs private subnet; how does RDS stay private?</b></summary>

Public subnet has a route to an Internet Gateway. RDS goes in private subnets with "publicly accessible = false"; its SG allows 5432 **only from the app's security group** (SG reference, not CIDR). Instances in private subnets reach the internet via NAT gateway (costs money — FlowGrid keeps EC2 public-with-tight-SG to avoid NAT cost, documented as a trade-off).
</details>

<details><summary><b>I3. How do you handle secrets on AWS?</b></summary>

Secrets Manager (rotation, RDS integration) or SSM Parameter Store SecureString (cheaper). Instance role grants `GetParameter` on specific paths; app reads at startup. Never bake secrets into images or commit `.env`.
</details>

<details><summary><b>I4. How does a pre-signed S3 URL work?</b></summary>

Server signs a URL with its credentials (SigV4) for one operation on one key with an expiry. Client downloads directly from S3 — API doesn't stream the file; bucket stays private. Expiry ≤ credential lifetime.
</details>

<details><summary><b>I5. RDS backups, Multi-AZ, read replicas — differences?</b></summary>

Automated backups + PITR: recovery from mistakes. Multi-AZ: synchronous standby for **availability** (automatic failover, same endpoint), not for read scaling. Read replicas: asynchronous, for **read scaling**, replica lag, separate endpoint.
</details>

<details><summary><b>I6. How do you get application logs into CloudWatch?</b></summary>

Docker `awslogs` log driver per service (`awslogs-group=/flowgrid/api`), or the CloudWatch agent tailing files. Structured JSON logs → Logs Insights queries: `fields @timestamp, level, requestId | filter status >= 500 | stats count() by bin(5m)`.
</details>

<details><summary><b>I7. How should CI deploy to AWS without storing access keys?</b></summary>

GitHub Actions OIDC: `permissions: id-token: write`, `aws-actions/configure-aws-credentials` with `role-to-assume`; the role's trust policy restricts `sub` to `repo:<owner>/<repo>:ref:refs/heads/main`. Short-lived credentials per run.
</details>

<details><summary><b>I8. EC2 vs ECS/Fargate vs Elastic Beanstalk vs Lambda for FlowGrid?</b></summary>

EC2+Compose: simplest mental model, cheapest for one box, but you patch the OS and there's no auto-healing. ECS/Fargate: managed container orchestration, rolling deploys, per-task IAM roles — next step. Beanstalk: PaaS convenience. Lambda: great for spiky, short tasks; poor fit for a Redis-connected API with scheduled low-stock alerts (FlowGrid) or long-running Docker-executing workers (ForgeCI) without redesign.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Your résumé lists AWS. Which services, and what did <i>you</i> configure?"</b></summary>

Past: truthful (e.g. "used S3 via SDK and deployed to existing EC2 instances; infra was owned by ops"). Now: "I deployed FlowGrid myself in Week 8 — EC2, RDS Postgres, S3, IAM role, CloudWatch alarm — and repeated the deploy for LedgerX, ForgeCI (api + workers on separate instances) and FlagForge; here's the diagram and the policy JSON."
Follow-up: "What would break if the AZ went down?"
</details>

<details><summary><b>R2. "How would you deploy this architecture on AWS?"</b></summary>

Route/DNS → EC2 (SG: 443 from world, 22 closed—use SSM) running nginx + api + redis in Compose (ForgeCI adds N worker instances) → RDS Postgres in private subnets (SG from app SG) → S3 for exports via instance role → CloudWatch logs/alarms → GitHub Actions builds images, deploys via OIDC role. Next steps: ALB + ASG or ECS, ElastiCache for Redis, Multi-AZ RDS.
</details>

<details><summary><b>R3. "The app on EC2 can't connect to RDS. Walk me through it."</b></summary>

DNS resolves endpoint? → SG on RDS allows 5432 from app SG? → same VPC / routing? → RDS status available? → credentials/DB name → SSL requirement (`sslmode`) → `nc -zv host 5432` from the instance → max connections.
</details>

<details><summary><b>R4. "How do you keep costs under control?"</b></summary>

Budget + alarm, smallest instance class, single-AZ for dev, stop/teardown scripts, no NAT gateway where avoidable, S3 lifecycle rules, delete unattached EBS/Elastic IPs, check Cost Explorer weekly. Reference `cost-safety.md` notes.
</details>

<details><summary><b>R5. "What's the shared responsibility model?"</b></summary>

AWS secures the cloud (hardware, facilities, hypervisor, managed-service internals); you secure what's in it (IAM, SG rules, OS patching on EC2, data encryption choices, app code). Managed services shift more to AWS (RDS patches the DB engine).
</details>

<details><summary><b>R6. "How would you know FlowGrid is down on AWS?"</b></summary>

CloudWatch alarm on EC2 status check + custom health endpoint check (Route 53 health check or external), alarm on 5xx rate from logs metric filter, RDS CPU/free storage alarms → SNS email. Say why a check from *outside* the instance (Route 53 health check or a tiny external probe) beats the app reporting on itself.
</details>

## 4. Practical tasks (doable live)

1. Write an IAM policy allowing read-only access to one S3 prefix.
2. Draw the FlowGrid VPC: subnets, SGs, arrows with ports. Then add ForgeCI's worker instance.
3. Use the AWS CLI: `aws s3 cp`, `aws s3 presign`, `aws sts get-caller-identity`, `aws logs tail /flowgrid/api --follow`.
4. Write a CloudWatch Logs Insights query counting errors by endpoint.
5. Explain the trust policy for a GitHub OIDC deploy role.

## 5. Debugging questions

<details><summary><b>D1. <code>AccessDenied</code> when the app uploads to S3.</b></summary>

Check identity (`aws sts get-caller-identity` on the box), instance profile attached, policy resource ARN (`bucket/*` for objects vs `bucket` for `ListBucket`), bucket policy explicit deny, KMS key permissions if SSE-KMS, region.
</details>

<details><summary><b>D2. Site unreachable after deploy, instance is "running".</b></summary>

SG inbound 80/443? Public IP changed after stop/start (use Elastic IP)? Containers up (`docker compose ps`, logs)? nginx listening on 0.0.0.0? Status checks passing?
</details>

<details><summary><b>D3. RDS "too many connections".</b></summary>

Each app instance's Hikari pool × instances + worker + migrations > `max_connections`. Reduce pool size, check leaks (`pg_stat_activity`), consider RDS Proxy.
</details>

<details><summary><b>D4. No logs in CloudWatch.</b></summary>

`awslogs` driver config, log group exists or `awslogs-create-group`, instance role has `logs:PutLogEvents`, correct region.
</details>

## 6. Architecture questions

<details><summary><b>A1. How would you make FlowGrid highly available?</b></summary>

ALB across two AZs, ASG (or ECS service) of stateless API containers, the scheduled low-stock alert job guarded by a distributed lock (or moved to a single worker instance), ElastiCache Redis, RDS Multi-AZ, S3 already regional. Cost roughly ×3; justify by SLA.
</details>

<details><summary><b>A2. Where does FlowGrid's scheduled low-stock alert job run if you scale the API to 3 instances?</b></summary>

Separate worker service (1 replica) or distributed lock (ShedLock / Redis lock / `SELECT … FOR UPDATE SKIP LOCKED` claim) so alerts aren't sent 3 times. ForgeCI avoids the problem by design: workers *pull* jobs from the Redis queue, so N workers share the work instead of duplicating it.
</details>

## 7. Common mistakes

- Using root account or long-lived access keys in code/CI.
- `"Action": "*"`, `"Resource": "*"` policies.
- Opening 22 or 5432 to `0.0.0.0/0`.
- Public S3 buckets for private data.
- Forgetting resources running → surprise bill.
- Assuming Multi-AZ = read scaling.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Region / AZ | Geographic area / isolated data centre(s) within it |
| VPC | Your private network in AWS |
| Subnet (public/private) | IP range; public = route to IGW |
| Security group | Stateful virtual firewall per ENI |
| IAM role | Assumable identity with temporary credentials |
| Instance profile | Container passing a role to EC2 |
| ARN | Amazon Resource Name |
| AMI | Machine image for EC2 |
| EBS | Block storage volumes for EC2 |
| Multi-AZ | Synchronous standby for failover |
| Pre-signed URL | Time-limited signed S3 URL |
| Log group / metric filter | CloudWatch log container / log → metric |
| OIDC federation | CI assumes a role without stored keys |

## 9. When to use it

- You need managed Postgres with backups, object storage, and a place to run containers.
- The company is on AWS (most common cloud in job postings).

## 10. When NOT to use it

- A hobby app that a PaaS (Render/Fly/Heroku-like) runs for less effort.
- When you can't commit to cost monitoring and teardown.
- Adding 10 managed services to a project that needs 3.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| EC2 + Compose | Simple, cheap, familiar | Manual patching, single point of failure |
| ECS/Fargate | Managed, scalable | More concepts, higher cost |
| RDS vs Postgres in a container | Backups, PITR, failover | $$, less control |
| Self-hosted Redis vs ElastiCache | Cheap | No managed failover |

## 12. How it interacts with the rest of my stack

- **Spring Boot:** `SPRING_DATASOURCE_URL` → RDS endpoint; AWS SDK v2 `S3Client` with default credentials chain; logs to stdout → CloudWatch.
- **Postgres:** RDS runs the same Flyway migrations on startup (or a dedicated migration step in CD).
- **Docker:** images pulled from registry onto EC2; Compose defines services.
- **Redis:** container on the same host (documented as non-HA).
- **CI/CD:** GitHub Actions OIDC → deploy role → SSM `send-command` or SSH to pull and restart.
- **React:** built bundle served by nginx on EC2.

## 13. One small hands-on exercise

**S3 export with least privilege (≤ 2 hours, ≤ $1).**

- [ ] Create a private bucket with Block Public Access on and a 7-day lifecycle rule.
- [ ] Create a role with only `PutObject`/`GetObject` on `bucket/exports/*`; attach to an EC2 instance.
- [ ] From the instance, a Spring Boot endpoint uploads a CSV and returns a 5-minute pre-signed URL.
- [ ] Prove `ListBucket` and writes outside `exports/` are denied.
- [ ] Tear everything down; screenshot the empty billing dashboard next day.

## 14. Mastery checklist

- [ ] Draw FlowGrid's AWS architecture from memory with ports and SGs (and ForgeCI's api + worker variant)
- [ ] Write a least-privilege policy and a trust policy by hand
- [ ] Explain Multi-AZ vs read replica vs backup
- [ ] Debug EC2→RDS connectivity step by step
- [ ] Explain OIDC deploys without stored keys
- [ ] Explain cost controls and my teardown script
- [ ] Truthful 60-second answer on past AWS exposure + FlowGrid bridge
