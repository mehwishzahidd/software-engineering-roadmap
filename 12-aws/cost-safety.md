# Cost & Safety — Read This First

> AWS will happily bill you for anything you forget. This file keeps each deploy week (W8 FlowGrid, W13 LedgerX, W19 ForgeCI, W23 FlagForge) at a few dollars at
> most and tells you exactly what to do if a credential leaks.
> **Interview use:** "What would you do if you committed an AWS key?" is a real question — and a great
> signal of maturity when answered calmly and in order.

← [AWS README](./README.md)

---

## 1. Free tier — caveats, not promises

AWS changed its free tier in mid-2025: accounts created after that date get a **credit-based free
plan** for a limited period, while older accounts keep the legacy "12 months free" offers for their
remaining time. Details differ per account, and they change. **Read the current AWS Free Tier page
and your Billing console → Free Tier / Credits page yourself** rather than trusting any tutorial,
including this one.

Things that commonly surprise people, regardless of free tier:

| Surprise | Why it costs money | Avoid by |
|---|---|---|
| **Public IPv4 addresses** | Billed per hour for every public IPv4 (in-use or idle Elastic IP) since 2024 | One instance, release EIPs, tear down |
| **NAT Gateway** | Hourly charge + per-GB processing — easily \$30+/month idle | None of the project deploys use one; private subnets only hold RDS |
| **RDS left running** | Hourly instance + storage; free allowance (if any) covers only specific small classes, single-AZ | Delete after the week or stop it (auto-restarts after 7 days!) |
| **RDS snapshots** | Manual/final snapshots billed per GB-month forever | `--skip-final-snapshot` or delete snapshots |
| **Multi-AZ / larger classes** | Double or more the price | Stay `db.t4g.micro`, single-AZ |
| **EBS volumes & snapshots** | Remain after instance *stop*; unattached volumes still billed | Terminate with `DeleteOnTermination=true` |
| **t-family "unlimited" credits** | Sustained CPU beyond baseline billed extra | Watch `CPUCreditBalance`; don't run load tests for hours |
| **CloudWatch Logs** | Ingestion per GB; retention "never expire" by default | INFO level, 7-day retention |
| **Secrets Manager** | Per secret per month | SSM Parameter Store SecureString (standard tier is free) |
| **Data transfer out** | Per GB to the internet | Don't host large files publicly |
| **Resources in another region** | You forget they exist | Stay in one region; check the **Billing → Bills** page by region |
| **ALB / ECS / EKS experiments** | Hourly charges even when idle | Out of scope for this roadmap; if you try, tear down same day |

Rough order of magnitude for FlowGrid running 24h/day (on-demand, verify on the pricing pages): a `t3.small`
+ `db.t4g.micro` + one public IPv4 is roughly **\$1–1.50 per day**. Running for a week ≈ the cost of a
lunch. Forgetting it for three months is not. ForgeCI adds a second (worker) instance → roughly
double the EC2 part.

---

## 2. Budgets and alarms

Do both on day 1 — they're free.

### AWS Budgets (forecast-aware)

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

cat > budget.json <<'EOF'
{
  "BudgetName": "monthly-10-usd",
  "BudgetLimit": { "Amount": "10", "Unit": "USD" },
  "TimeUnit": "MONTHLY",
  "BudgetType": "COST"
}
EOF

cat > notifications.json <<'EOF'
[
  {
    "Notification": { "NotificationType": "ACTUAL", "ComparisonOperator": "GREATER_THAN",
                      "Threshold": 50, "ThresholdType": "PERCENTAGE" },
    "Subscribers": [ { "SubscriptionType": "EMAIL", "Address": "you@example.com" } ]
  },
  {
    "Notification": { "NotificationType": "FORECASTED", "ComparisonOperator": "GREATER_THAN",
                      "Threshold": 100, "ThresholdType": "PERCENTAGE" },
    "Subscribers": [ { "SubscriptionType": "EMAIL", "Address": "you@example.com" } ]
  }
]
EOF

aws budgets create-budget --account-id "$ACCOUNT_ID" \
  --budget file://budget.json --notifications-with-subscribers file://notifications.json
```

Or in the console: Billing → Budgets → "Use a template" → **Zero spend budget** (alerts on the first
cent) plus a monthly cost budget.

### CloudWatch billing alarm

In `us-east-1` only; full commands in [cloudwatch.md §6](./cloudwatch.md#6-billing-alarm-do-this-on-day-1).

### Also enable

- [ ] **Cost Anomaly Detection** (Billing → Cost Anomaly Detection) with an email subscription
- [ ] Free Tier usage alerts (Billing preferences)
- [ ] Tag every resource `project=flowgrid` so Cost Explorer can group by tag (activate the tag as a cost allocation tag)

---

## 3. Account security baseline

- [ ] Root: MFA on, **no access keys**, strong unique password in a password manager
- [ ] Daily work as an admin IAM/Identity Center user with MFA
- [ ] No access keys on EC2 — instance roles only
- [ ] No access keys in GitHub Actions if avoidable — use OIDC ([`13-cicd/github-actions.md`](../13-cicd/github-actions.md#oidc-to-aws))
- [ ] `~/.aws/credentials` never inside a project directory; `.gitignore` covers `.env`, `*.pem`
- [ ] A secret scanner in your workflow: GitHub **secret scanning + push protection** (enable in repo settings), optionally `gitleaks` as a pre-commit hook
- [ ] SSH (22) open only to your IP — or closed entirely with Session Manager
- [ ] RDS not publicly accessible; S3 Block Public Access on
- [ ] Alternate contacts + a correct account email (AWS emails you about abuse and leaks)

---

## 4. Leaked-credential response

Scenario: you pushed an access key to a public GitHub repo. Bots scrape GitHub within **minutes**
and start crypto-mining instances in every region. AWS may also detect it and apply a quarantine
policy (`AWSCompromisedKeyQuarantine…`) and email you — don't count on that.

Do these **in order**, immediately:

1. **Deactivate the key** (stops the bleeding, reversible):
   ```bash
   aws iam update-access-key --user-name <user> --access-key-id AKIA... --status Inactive
   ```
2. **Create a replacement** only if something legitimately needs one, then **delete** the leaked key:
   ```bash
   aws iam delete-access-key --user-name <user> --access-key-id AKIA...
   ```
3. **Find what the attacker did** — CloudTrail Event history (90 days, free), filter by
   *AWS access key* = the leaked key ID, **check every region**:
   ```bash
   aws cloudtrail lookup-events \
     --lookup-attributes AttributeKey=AccessKeyId,AttributeValue=AKIA... \
     --max-results 50 --region us-east-1
   ```
   Look for `RunInstances`, `CreateUser`, `CreateAccessKey`, `AttachUserPolicy`, `CreateLoginProfile`, `PutBucketPolicy`.
4. **Remove anything they created**: instances in all regions, new IAM users/keys/roles (attackers
   create backdoors), Lambda functions, key pairs, spot requests.
   ```bash
   for r in $(aws ec2 describe-regions --query 'Regions[].RegionName' --output text); do
     echo "== $r"; aws ec2 describe-instances --region "$r" \
       --query 'Reservations[].Instances[].[InstanceId,State.Name,InstanceType]' --output text
   done
   ```
5. **Rotate everything related**: any other secrets in the same repo/commit (DB passwords, JWT secret, tokens).
6. **Clean the repository** — but understand: rewriting history (`git filter-repo`, BFG) does **not**
   un-leak it. Forks, clones and caches exist. Rotation (steps 1–2) is what actually fixes it.
7. **Open an AWS Support case** (Basic support is free) if charges were incurred — AWS often
   works with you on fraudulent usage once you've secured the account.
8. **Post-mortem**: how did it get committed? Add push protection, pre-commit scanning, move to
   roles/OIDC so there's no key to leak.

Interview version (30 seconds): *"Deactivate the key immediately, check CloudTrail across all regions
for what it was used for, delete anything created including IAM backdoors, rotate every other secret
in that commit, then fix the root cause — ideally by removing long-lived keys entirely with roles
and OIDC. Rewriting Git history isn't remediation because the key is already copied."*

---

## Teardown checklist

Run the CLI teardown in [deploy-walkthrough.md §12](./deploy-walkthrough.md#step-12--teardown-reverse-order),
then verify in the console **in your region** (and glance at others):

- [ ] EC2 → Instances: none running/stopped for the project
- [ ] EC2 → Volumes: no `available` (unattached) volumes
- [ ] EC2 → Snapshots / AMIs: none you created
- [ ] EC2 → Elastic IPs: none allocated
- [ ] EC2 → Key pairs / Security groups: project ones deleted
- [ ] VPC → NAT gateways: none; VPCs: only the default
- [ ] RDS → Databases: none; Snapshots (manual **and** system): none
- [ ] S3: project bucket gone
- [ ] CloudWatch → Log groups / Alarms: project ones gone (keep billing alarm)
- [ ] IAM → Roles: `flowgrid-ec2-role` gone; Users: no unused access keys
- [ ] Systems Manager → Parameter Store: project parameters gone
- [ ] Billing → Bills: next day, current-month charges not increasing; forecast ≈ \$0
- [ ] `aws resourcegroupstaggingapi get-resources --tag-filters Key=project,Values=flowgrid` returns empty

If you want a project live during applications (nice for a portfolio), decide consciously: keep only EC2 +
RDS small, set a budget alert at your chosen monthly limit, and note the monthly cost in the runbook.
Screenshots + a recorded demo are an acceptable, free alternative.

---

## Interview questions

<details><summary>How did you keep your AWS costs under control?</summary>

Budget with actual + forecast alerts, a CloudWatch billing alarm, everything tagged, smallest
instance classes, no NAT gateway, log retention set, and a scripted teardown I verified in the
billing console. I know which items bill even when idle (public IPv4, RDS, EBS, snapshots).
</details>

<details><summary>What do you do if an access key leaks?</summary>

Deactivate → delete/rotate → CloudTrail review in all regions → remove attacker-created resources and
IAM backdoors → rotate other secrets → contact AWS support if charges → root-cause fix (push
protection, no long-lived keys).
</details>
