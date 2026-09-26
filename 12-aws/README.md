# ☁️ 12 — AWS (Week 21)

> **Scope is deliberately capped.** Five core services — **IAM, EC2, S3, RDS, CloudWatch** — plus
> just enough **VPC / security groups** to deploy [PulseWatch (P4)](../18-projects/p4-pulsewatch/README.md).
> No certification chasing. The goal is that you can *deploy, secure, observe and tear down* a real
> Spring Boot system and defend every decision in an interview.

Week in the plan: **[Week 21](../weeks/week-21/README.md)** (P4 milestone M3: running on AWS).
CI/CD into this environment follows in [Week 22](../weeks/week-22/README.md) → [`13-cicd/`](../13-cicd/README.md).

---

## Contents

| # | File | What you'll be able to do | Time |
|---|---|---|---:|
| 1 | [`cost-safety.md`](./cost-safety.md) | **Read first.** Set a budget + billing alarm, lock down root, know the teardown checklist | 1 h |
| 2 | [`iam.md`](./iam.md) | Create an admin user with MFA, write least-privilege policies, attach a role to EC2 | 2.5 h |
| 3 | [`ec2.md`](./ec2.md) | Launch an instance in a minimal VPC, SSH in, install Docker, run Compose | 3 h |
| 4 | [`s3.md`](./s3.md) | Create a private bucket, upload from Java (SDK v2), hand out presigned URLs | 2 h |
| 5 | [`rds.md`](./rds.md) | Run PostgreSQL 16 in a private subnet reachable only from your EC2 SG | 2 h |
| 6 | [`cloudwatch.md`](./cloudwatch.md) | Ship container logs, read metrics, create alarms (CPU, 5xx, billing) | 2 h |
| 7 | [`deploy-walkthrough.md`](./deploy-walkthrough.md) | Deploy P4 end-to-end with the AWS CLI, then tear it all down | 5–6 h |

Total ≈ 18 h — the whole AWS budget for Week 21.

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
  │   │  docker compose: api, worker, redis,     │── awslogs ──► CW  │
  │   │  nginx/frontend                          │                   │
  │   └───────────────┬──────────────────────────┘                   │
  │                   │ 5432 (sg-db allows only sg-app)              │
  │  Private subnets 10.0.11.0/24 + 10.0.12.0/24 (no IGW route)      │
  │   ┌───────────────▼──────────────┐                               │
  │   │ RDS PostgreSQL 16            │                               │
  │   └──────────────────────────────┘                               │
  └──────────────────────────────────────────────────────────────────┘
```

Every file in this folder expands one box or arrow in that diagram.

---

## Service cheat sheet

| Service | One-line definition | P4 uses it for | Interview one-liner |
|---|---|---|---|
| **IAM** | Who can do what to which resource | EC2 instance role with S3 + CloudWatch permissions only | "Least privilege; roles over long-lived keys; never use root." |
| **VPC** | Your private network in a region | Public subnet for EC2, private subnets for RDS | "Public subnet = has a route to an Internet Gateway." |
| **EC2** | A virtual machine | Runs Docker Compose (api, worker, redis) | "Security groups are stateful allow-lists attached to ENIs." |
| **S3** | Object storage, key → bytes | CSV report exports, presigned download links | "Block Public Access on; share with presigned URLs." |
| **RDS** | Managed relational DB | PostgreSQL 16 for monitors & check results | "Automated backups, patching, Multi-AZ — I don't manage the OS." |
| **CloudWatch** | Logs, metrics, alarms | Container logs, CPU alarm, billing alarm | "Metric → alarm → SNS → email." |

### Alternatives — know they exist and when (don't learn them now)

| Service | What it is | When you'd choose it over EC2 + Compose |
|---|---|---|
| **ECS on Fargate** | Run containers without managing VMs; tasks defined in JSON | Several services, autoscaling, zero-downtime rolling deploys; the "next step" for P4 |
| **Elastic Beanstalk** | PaaS wrapper that provisions EC2/ALB/ASG for you | Quick demos; teams without ops experience. Less common in new designs |
| **Lambda** | Functions run per event, billed per ms | Spiky/event-driven work (S3 upload trigger, cron job). Poor fit for a long-running scheduler with warm connection pools |
| **App Runner / Lightsail** | Simplified container/VM hosting | Hobby projects wanting fixed monthly price |
| **ElastiCache** | Managed Redis | When Redis must survive instance loss; P4 keeps Redis in Compose to save cost |
| **ALB** | Layer-7 load balancer | Multiple EC2 instances or ECS tasks, TLS termination with ACM certs |

---

## Learn → Build → Break → Debug → Explain

| Step | Week 21 activity |
|---|---|
| Learn | Read each file here + the linked AWS docs page for that service (docs.aws.amazon.com) |
| Build | Follow [`deploy-walkthrough.md`](./deploy-walkthrough.md) with P4 |
| Break | Remove the SG rule 5432 → watch the API fail with a connection timeout; detach the instance role → watch the S3 export fail with `AccessDenied` |
| Debug | `aws sts get-caller-identity`, `docker logs`, CloudWatch Logs Insights, `nc -zv <rds-endpoint> 5432` |
| Explain | Draw the diagram above from memory and narrate a request from browser → EC2 → RDS |
| Review | Run the [teardown checklist](./cost-safety.md#teardown-checklist); confirm \$0 forecast next day |

---

## Checklist for the week

- [ ] Root account has MFA; no root access keys exist
- [ ] Budget with email alert at \$5 and \$10 created; billing alarm exists in `us-east-1`
- [ ] Admin IAM user (or IAM Identity Center user) with MFA; CLI configured with a named profile
- [ ] Minimal VPC: 1 public + 2 private subnets, IGW, route tables
- [ ] EC2 running Docker Compose for P4 with an **instance profile** (no keys on the box)
- [ ] RDS PostgreSQL reachable only from the EC2 security group
- [ ] S3 bucket with Block Public Access; CSV export works; presigned URL expires
- [ ] Container logs visible in CloudWatch Logs; CPU alarm + billing alarm → SNS email
- [ ] Wrote down every resource ID in the P4 runbook
- [ ] Tore everything down (or consciously kept it running and know the daily cost)

---

## Interview questions this folder prepares you for

1. What is the difference between an IAM user, group, role and policy?
2. How does your application on EC2 get AWS credentials without storing keys?
3. Security group vs NACL — which is stateful?
4. What makes a subnet "public"? Why is your database in a private subnet?
5. How would you share a private S3 file with a user for 10 minutes?
6. What does RDS manage for you that a Postgres container on EC2 doesn't?
7. How do you know your service is down at 3 a.m.?
8. What would you do if you accidentally pushed AWS keys to GitHub?
9. How would you scale P4 beyond one EC2 instance? (→ ALB + ASG or ECS; RDS read replica; ElastiCache)
10. Why didn't you use Lambda / Kubernetes? (Honest answer: scope, cost, and one long-running scheduler.)

Deeper résumé-style drilling lives in [`17-resume-tech-defense/aws.md`](../17-resume-tech-defense/aws.md).

---

## Resources

- AWS docs (docs.aws.amazon.com): IAM User Guide, EC2 User Guide, S3 User Guide, RDS User Guide, CloudWatch User Guide, AWS CLI v2 Command Reference
- AWS SDK for Java 2.x Developer Guide
- AWS Well-Architected Framework — Security and Cost Optimization pillars (skim)
- AWS Free Tier page — **read the current terms yourself**; they changed in 2025 and differ per account age
