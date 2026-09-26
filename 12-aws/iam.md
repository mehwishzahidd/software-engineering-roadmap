# IAM — Identity and Access Management

> **Practical use:** give FlowGrid on EC2 permission to write to *one* S3 bucket and *its own*
> CloudWatch log group — nothing else — without a single access key on disk.
> **Interview use:** "users vs roles", "least privilege", "how does your app get credentials?"

← [AWS README](./README.md) · Next: [EC2](./ec2.md)

---

## 1. Vocabulary

| Term | What it is | Example (FlowGrid) |
|---|---|---|
| **Root user** | The email you signed up with. Can do *everything*, including close the account. | Used once: enable MFA, create admin, then lock away |
| **IAM user** | A long-lived identity for a human (or legacy app) with a password and/or access keys | `saeed-admin` for you |
| **IAM group** | A collection of users; attach policies to the group, not to each user | `Admins`, `ReadOnly` |
| **IAM role** | An identity with **no long-lived credentials**. Someone/something *assumes* it and gets temporary credentials (STS) | `flowgrid-ec2-role` |
| **Policy** | JSON document of `Allow`/`Deny` statements | `flowgrid-app-policy` |
| **Trust policy** | The policy *on a role* saying who may assume it | "EC2 service may assume this role" |
| **Instance profile** | Container that attaches a role to an EC2 instance | `flowgrid-ec2-profile` |
| **Principal** | The entity making a request (user, role session, service) | `arn:aws:sts::123…:assumed-role/flowgrid-ec2-role/i-0abc…` |
| **ARN** | Amazon Resource Name — globally unique ID | `arn:aws:s3:::flowgrid-exports-123456789012` |
| **STS** | Security Token Service — issues temporary credentials | Behind every role assumption |

**Modern alternative for humans:** *IAM Identity Center* (SSO) gives short-lived credentials via
`aws configure sso` / `aws sso login`. For a single personal account an IAM user with MFA is
acceptable; Identity Center is the better practice and worth knowing exists.

---

## 2. Policy evaluation — the rules

1. By default everything is **implicitly denied**.
2. An explicit **Allow** in any applicable policy grants access…
3. …unless an explicit **Deny** exists anywhere. **Explicit Deny always wins.**
4. (Also in play at companies: Organizations SCPs, permission boundaries, resource policies. For a
   single account you mostly see identity policies + S3 bucket policies.)

```
request ──► explicit Deny? ──yes──► DENIED
                 │no
                 ▼
            explicit Allow? ──no──► DENIED (implicit)
                 │yes
                 ▼
              ALLOWED
```

---

## 3. Anatomy of a policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ExportsReadWrite",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::flowgrid-exports-123456789012/reports/*"
    },
    {
      "Sid": "ExportsList",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::flowgrid-exports-123456789012",
      "Condition": { "StringLike": { "s3:prefix": ["reports/*"] } }
    }
  ]
}
```

| Field | Notes |
|---|---|
| `Version` | Always `"2012-10-17"` (it's the policy-language version, not a date you change) |
| `Effect` | `Allow` or `Deny` |
| `Action` | `service:Operation`, wildcards allowed (`s3:Get*`). Avoid `"*"` |
| `Resource` | ARNs. Note: `ListBucket` applies to the **bucket** ARN; `GetObject`/`PutObject` to **object** ARNs (`bucket/*`). Mixing these up is the #1 S3 policy bug |
| `Condition` | Extra constraints: source IP, MFA present, prefix, tags, VPC endpoint |

---

## 4. The FlowGrid policies

### 4.1 Trust policy — "EC2 may assume this role"

`trust-ec2.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

### 4.2 Permissions policy — least privilege for the app

`flowgrid-app-policy.json` (replace account ID, region, bucket):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3ReportExports",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::flowgrid-exports-123456789012/reports/*"
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
      "Resource": "arn:aws:logs:eu-west-1:123456789012:log-group:/flowgrid/*:*"
    },
    {
      "Sid": "CustomMetrics",
      "Effect": "Allow",
      "Action": "cloudwatch:PutMetricData",
      "Resource": "*",
      "Condition": { "StringEquals": { "cloudwatch:namespace": "FlowGrid" } }
    }
  ]
}
```

Why these and nothing more:
- The app writes and reads report CSVs → `PutObject`, `GetObject` on one prefix. Presigned GET URLs
  are signed *with the role's credentials*, so the role needs `GetObject` too.
- The Docker `awslogs` driver creates streams and pushes events. The log group itself is created
  by you (so the role does not need `logs:CreateLogGroup`).
- `PutMetricData` does not support resource-level ARNs → `"*"` constrained by namespace condition.
- **Not included:** `s3:DeleteObject`, `s3:*`, `iam:*`, `ec2:*`. If it's not needed, it's not granted.

### 4.3 Create it with the CLI

```bash
aws iam create-role \
  --role-name flowgrid-ec2-role \
  --assume-role-policy-document file://trust-ec2.json

aws iam put-role-policy \
  --role-name flowgrid-ec2-role \
  --policy-name flowgrid-app-policy \
  --policy-document file://flowgrid-app-policy.json

# Optional: allow Session Manager (shell without SSH/port 22)
aws iam attach-role-policy \
  --role-name flowgrid-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

aws iam create-instance-profile --instance-profile-name flowgrid-ec2-profile
aws iam add-role-to-instance-profile \
  --instance-profile-name flowgrid-ec2-profile \
  --role-name flowgrid-ec2-role
```

`put-role-policy` = **inline** policy (lives and dies with the role). `create-policy` +
`attach-role-policy` = **managed** policy (reusable, versioned). Either is fine here; managed is
preferred when the same permissions are shared.

---

## 5. How the app actually gets credentials

```
Spring Boot (AWS SDK v2)
  └─ DefaultCredentialsProvider chain:
       1. Java system properties (aws.accessKeyId…)
       2. Environment variables (AWS_ACCESS_KEY_ID…)
       3. Web identity token (EKS / OIDC)
       4. ~/.aws/credentials profile
       5. Container credentials (ECS task role)
       6. EC2 Instance Metadata Service (IMDSv2) ◄── FlowGrid on EC2 lands here
```

- The instance metadata service at `169.254.169.254` returns **temporary** credentials for the
  instance role, rotated automatically before expiry.
- Your code does nothing special: `S3Client.builder().region(Region.EU_WEST_1).build()`.
- Containers on the instance can reach IMDS too. With **IMDSv2** and the default hop limit of 1,
  containers on a bridge network *cannot*; set the hop limit to 2 (see [ec2.md](./ec2.md#5-imdsv2-and-docker)).
- **Break it:** set `AWS_ACCESS_KEY_ID=bogus` in the container env → the chain stops at step 2
  and you get `InvalidAccessKeyId`. This is why you never mix mechanisms.

Verify identity from the instance:

```bash
aws sts get-caller-identity
# "Arn": "arn:aws:sts::123456789012:assumed-role/flowgrid-ec2-role/i-0abc..."
```

---

## 6. Setting up your own account safely (do this once)

- [ ] Sign in as root → **enable MFA** (authenticator app or hardware key)
- [ ] Confirm root has **no access keys** (IAM → Security credentials). If any exist, delete them
- [ ] Create group `Admins` with the AWS-managed `AdministratorAccess` policy
- [ ] Create user `yourname-admin`, add to `Admins`, enable console password + **MFA**
- [ ] For the CLI, either `aws configure sso` (Identity Center) **or** create one access key for
      the admin user, store it only in `~/.aws/credentials` under a named profile, and rotate it
- [ ] Sign out of root. Use root only for account-level tasks (billing settings, closing account)
- [ ] Enable "IAM user/role access to billing information" so your admin can see Billing

```bash
aws configure --profile pw          # region eu-west-1, output json
export AWS_PROFILE=pw
aws sts get-caller-identity          # always run this before doing anything destructive
```

Require MFA for sensitive actions (example condition you may see in real companies):

```json
{
  "Effect": "Deny",
  "NotAction": ["iam:CreateVirtualMFADevice", "iam:EnableMFADevice", "sts:GetSessionToken"],
  "Resource": "*",
  "Condition": { "BoolIfExists": { "aws:MultiFactorAuthPresent": "false" } }
}
```

---

## 7. Least privilege — a working method

1. Start from **what the code calls** (grep for `s3Client.`, log driver, metrics).
2. Write a policy for exactly those actions on exactly those ARNs.
3. Run the app; read `AccessDenied` messages — they name the missing action and resource.
4. Add only that; repeat.
5. Later, use **IAM Access Analyzer** / "last accessed" data to remove unused permissions.

Anti-patterns to name in interviews: `"Action": "*"`, `AdministratorAccess` on an app role, access
keys baked into Docker images or `application.yml`, one shared IAM user for the whole team, keys in
GitHub Actions secrets when OIDC is available (see [`13-cicd/github-actions.md`](../13-cicd/github-actions.md#oidc-to-aws)).

---

## 8. Break → Debug drills

| Break | Expected symptom | Debug |
|---|---|---|
| Remove `s3:PutObject` from policy | `S3Exception: Access Denied (Status Code: 403)` on export | Read the error; `aws iam get-role-policy …` |
| Resource `…:bucket` instead of `…:bucket/*` for PutObject | Same 403 even though action is allowed | Object vs bucket ARN |
| Detach instance profile | `Unable to load credentials from any of the providers in the chain` | `curl` IMDSv2 token + `/latest/meta-data/iam/` |
| Add an explicit `Deny s3:*` | 403 even with Allow | Explicit Deny wins |

IMDSv2 manual check:

```bash
TOKEN=$(curl -sX PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/iam/security-credentials/
```

---

## 9. Interview questions

<details><summary>User vs role?</summary>

A user has long-lived credentials (password, access keys) tied to one identity. A role has no
permanent credentials; a trusted principal assumes it and receives temporary credentials from STS
that expire (typically 1 h for instance roles, auto-refreshed). Apps and services should use roles.
</details>

<details><summary>How does your Spring Boot app on EC2 authenticate to S3?</summary>

An instance profile attaches `flowgrid-ec2-role`. The AWS SDK's default credentials chain reaches
the EC2 metadata endpoint (IMDSv2) and gets temporary credentials. No keys exist in code, env vars
or the image. The role's policy only allows Get/Put on the `reports/` prefix of one bucket.
</details>

<details><summary>What is least privilege and how did you apply it?</summary>

Grant only the actions and resources needed. For FlowGrid: two S3 actions on one prefix, three Logs
actions on one log-group pattern, `PutMetricData` restricted to one namespace. Nothing for IAM, EC2
or deletes.
</details>

<details><summary>Identity policy vs resource policy?</summary>

Identity policies attach to users/groups/roles ("what can I do"). Resource policies attach to a
resource such as an S3 bucket or SQS queue and include a `Principal` ("who can touch me"). Both are
evaluated; an explicit Deny in either wins. Cross-account access usually needs both.
</details>

<details><summary>Why never use root access keys?</summary>

Root cannot be restricted by IAM policies, so a leaked root key is a full account takeover
(including billing and closing the account). Root should have MFA, no keys, and be used only for
the handful of tasks that require it.
</details>

<details><summary>What is the trust policy on a role?</summary>

A resource-based policy on the role defining who may call `sts:AssumeRole` on it — e.g. the
`ec2.amazonaws.com` service, another account, or a GitHub OIDC provider with conditions on the repo.
</details>
