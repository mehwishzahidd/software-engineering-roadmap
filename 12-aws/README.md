# ☁️ 12 — AWS (Weeks 7–8, repeated W13 · W19 · W23)

> **Scope is deliberately capped.** Five core services — **IAM, EC2, S3, RDS, CloudWatch** — plus
> just enough **VPC / security groups** to deploy [FlowGrid](../18-projects/flowgrid/README.md), and two
> "considered" services for later projects (**SQS** for ForgeCI, **ElastiCache** only if cheaper than Redis on EC2).
> No certification chasing. The goal: you can *deploy, secure, observe and tear down* a real
> Spring Boot system and defend every decision in an interview.

| When | What | Project |
|---|---|---|
| **[Week 7](../weeks/week-07/README.md)** | Account safety, IAM, EC2 + minimal VPC, Docker on EC2 | FlowGrid M4 (prep) |
| **[Week 8](../weeks/week-08/README.md)** | RDS, S3, CloudWatch, full deploy + teardown | **FlowGrid M5** — first deploy |
| [Week 13](../weeks/week-13/README.md) | Repeat faster; stricter DB settings (backups, deletion protection) | LedgerX M5 |
| [Week 19](../weeks/week-19/README.md) | Multi-service: API instance + **worker instance with Docker socket**; SQS vs Redis decision | ForgeCI M6 |
| [Week 23](../weeks/week-23/README.md) | Repeat; SSE through nginx; SDK sample app pointing at the deployed API | FlagForge M4 |

Each repetition should take noticeably less time — record the duration in each project's
`DEPLOYMENT.md`. CI/CD into this environment: [`13-cicd/`](../13-cicd/README.md) (W8, W18).

---

## Contents

| # | File | What you'll be able to do | Time (first pass) |
|---|---|---|---:|
| 1 | [`cost-safety.md`](./cost-safety.md) | **Read first.** Budget + billing alarm, root lockdown, teardown checklist, leaked-key response | 1 h |
| 2 | [`iam.md`](./iam.md) | Admin user with MFA, least-privilege policies, instance role for EC2 | 2.5 h |
| 3 | [`ec2.md`](./ec2.md) | Launch an instance in a minimal VPC, SSH/SSM in, install Docker, run Compose | 3 h |
| 4 | [`s3.md`](./s3.md) | Private bucket, upload from Java (SDK v2), presigned URLs | 2 h |
| 5 | [`rds.md`](./rds.md) | PostgreSQL 16 in a private subnet reachable only from the app's SG | 2 h |
| 6 | [`cloudwatch.md`](./cloudwatch.md) | Container logs, metrics, alarms (CPU, errors, billing) | 2 h |
| 7 | [`deploy-walkthrough.md`](./deploy-walkthrough.md) | Deploy FlowGrid end-to-end with the AWS CLI, tear it down; notes for the ForgeCI multi-service case | 6–8 h |

---

## Mental model: the one diagram to remember

```
                        Internet
                           │
                  ┌────────▼─────────┐
                  │ Internet Gateway │
                  └────────┬─────────┘
   VPC 10.0.0.0/16         │
  ┌────────────────────────┼─────────────────────────────────────────┐
  │  Public subnet 10.0.1.0/24 (route 0.0.0.0/0 → IGW)               │
  │   ┌──────────────────────────────────────────┐                   │
  │   │ EC2 (sg-app: 22 from MY_IP, 80/443 any)  │── IAM role ──► S3 │
  │   │  docker compose: ui (nginx + React),     │── awslogs ──► CW  │
  │   │  api (Spring Boot), redis                │                   │
  │   └───────────────┬──────────────────────────┘                   │
  │                   │ 5432 (sg-db allows only sg-app)              │
  │  Private subnets 10.0.11.0/24 + 10.0.12.0/24 (no IGW route)      │
  │   ┌───────────────▼──────────────┐                               │
  │   │ RDS PostgreSQL 16            │                               │
  │   └──────────────────────────────┘                               │
  └──────────────────────────────────────────────────────────────────┘
```

Every file in this folder expands one box or arrow in that diagram. ForgeCI (W19) adds a second
EC2 box — the worker — in its own security group ([deploy-walkthrough.md §14](./deploy-walkthrough.md#14-the-multi-service-case-forgeci-week-19)).

---

## Service cheat sheet

| Service | One-line definition | FlowGrid uses it for | Interview one-liner |
|---|---|---|---|
| **IAM** | Who can do what to which resource | EC2 instance role with S3 + CloudWatch permissions only | "Least privilege; roles over long-lived keys; never use root." |
| **VPC** | Your private network in a region | Public subnet for EC2, private subnets for RDS | "Public subnet = has a route to an Internet Gateway." |
| **EC2** | A virtual machine | Runs Docker Compose (ui, api, redis) | "Security groups are stateful allow-lists attached to ENIs." |
| **S3** | Object storage, key → bytes | Inventory/order report CSV exports, presigned download links | "Block Public Access on; share with presigned URLs." |
| **RDS** | Managed relational DB | PostgreSQL 16 for warehouses, inventory, orders, reservations | "Automated backups, PITR, patching — I don't manage the OS." |
| **CloudWatch** | Logs, metrics, alarms | Container logs, CPU/error alarms, billing alarm | "Metric → alarm → SNS → email." |

### Alternatives — know they exist and when (don't learn them now)

| Service | What it is | When you'd choose it over EC2 + Compose |
|---|---|---|
| **ECS on Fargate** | Run containers without managing VMs; tasks defined in JSON | Several services, autoscaling, zero-downtime rolling deploys; the natural "next step" for FlowGrid/FlagForge. **Not** for ForgeCI's worker as designed — Fargate gives no Docker socket |
| **Elastic Beanstalk** | PaaS wrapper that provisions EC2/ALB/ASG for you | Quick demos; teams without ops experience. Less common in new designs |
| **Lambda** | Functions run per event, billed per ms | Spiky/event-driven work (S3 upload trigger, a cron job). Poor fit for long-running APIs with warm connection pools, SSE, or container-executing workers |
| **App Runner / Lightsail** | Simplified container/VM hosting | Hobby projects wanting a fixed monthly price |
| **ElastiCache** | Managed Redis | When Redis must survive instance loss, or several instances share it; cost is the trade-off |
| **SQS** | Managed message queue (at-least-once, visibility timeout, DLQ) | ForgeCI job queue if you want managed durability — compared in the walkthrough |
| **ALB** | Layer-7 load balancer | Multiple instances or ECS tasks, TLS with ACM certificates |

---

## Learn → Build → Break → Debug → Explain

| Step | Week 7–8 activity |
|---|---|
| Learn | Each file here + the matching AWS docs user guide (docs.aws.amazon.com) |
| Build | Follow [`deploy-walkthrough.md`](./deploy-walkthrough.md) with FlowGrid |
| Break | Remove the SG rule 5432 → API health DOWN with a connection timeout; detach the instance role → S3 export fails `AccessDenied`; stop Redis → app must degrade to DB reads (FlowGrid M3 requirement) |
| Debug | `aws sts get-caller-identity`, `docker compose logs`, CloudWatch Logs Insights, `nc -zv <rds-endpoint> 5432` |
| Explain | Draw the diagram from memory and narrate a request: browser → nginx → api → Redis/RDS |
| Review | Run the [teardown checklist](./cost-safety.md#teardown-checklist); confirm the forecast stops growing |

---

## Checklist (Week 8)

- [ ] Root account has MFA; no root access keys exist
- [ ] Budget with email alerts and a billing alarm in `us-east-1`
- [ ] Admin IAM user (or IAM Identity Center user) with MFA; CLI configured with a named profile
- [ ] Minimal VPC: 1 public + 2 private subnets, IGW, route tables
- [ ] EC2 running FlowGrid's Compose stack with an **instance profile** (no keys on the box)
- [ ] RDS PostgreSQL reachable only from the EC2 security group
- [ ] S3 bucket with Block Public Access; report export works; presigned URL expires
- [ ] Container logs in CloudWatch Logs; CPU + error + billing alarms → SNS email (confirmed)
- [ ] Every resource ID and command recorded in FlowGrid's `DEPLOYMENT.md`
- [ ] Torn down (or consciously kept running with a known daily cost and a budget alert)

---

## Interview questions this folder prepares you for

1. What is the difference between an IAM user, group, role and policy?
2. How does your application on EC2 get AWS credentials without storing keys?
3. Security group vs NACL — which is stateful?
4. What makes a subnet "public"? Why is your database in a private subnet?
5. How would you let a user download a private report for 10 minutes?
6. What does RDS manage for you that a Postgres container on EC2 doesn't?
7. How do you know your service is down at 3 a.m.?
8. What would you do if you accidentally pushed AWS keys to GitHub?
9. How would you scale FlowGrid beyond one EC2 instance?
10. Why does ForgeCI's worker run on its own instance, and what are the risks of mounting the Docker socket?
11. Why did you (or didn't you) use SQS instead of Redis for ForgeCI's queue?
12. Why not Lambda / Kubernetes? (Honest answer: scope, cost, long-running processes, and what each project needed.)

Deeper résumé-style drilling: [`17-resume-tech-defense/aws.md`](../17-resume-tech-defense/aws.md).

---

## Resources

- AWS docs (docs.aws.amazon.com): IAM User Guide, EC2 User Guide, S3 User Guide, RDS User Guide, CloudWatch User Guide, SQS Developer Guide, AWS CLI v2 Command Reference
- AWS SDK for Java 2.x Developer Guide
- AWS Well-Architected Framework — Security and Cost Optimization pillars (skim)
- AWS Free Tier page — **read the current terms yourself**; they changed in 2025 and differ per account age
