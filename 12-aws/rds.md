# RDS for PostgreSQL

> **Practical use:** FlowGrid's `warehouses`, `skus`, `inventory_levels`, `orders` and `reservations` tables live in a managed
> PostgreSQL 16 instance in **private subnets**, reachable **only** from the app's security group.
> **Interview use:** "RDS vs Postgres in a container", "how do backups work?", "how is your DB secured?"

← [S3](./s3.md) · Next: [CloudWatch](./cloudwatch.md)

---

## 1. What RDS does (and doesn't) do for you

| RDS manages | You still own |
|---|---|
| Provisioning, OS, Postgres installation | Schema, migrations (Flyway), indexes |
| Minor-version patching (maintenance window) | Query performance (`EXPLAIN ANALYZE`) |
| Automated daily snapshots + transaction logs → **point-in-time restore** | Deciding retention, testing restores |
| Multi-AZ synchronous standby + automatic failover (if enabled) | Connection pool sizing, retry on failover |
| Read replicas (async) | Routing reads to replicas |
| Storage autoscaling, metrics in CloudWatch | Users/roles/grants inside Postgres |
| Encryption at rest (KMS) | Enforcing TLS from the client |

You get **no SSH** to the host and no superuser; the master user has the `rds_superuser` role.

---

## 2. Network placement

```
sg-app (EC2) ──TCP 5432──► sg-db (RDS)
                            inbound: 5432 from source-group sg-app  (NOT 0.0.0.0/0, NOT your IP)
```

- **DB subnet group** = private subnets in **at least two AZs** (required even for single-AZ).
- `--no-publicly-accessible` → endpoint resolves to a private IP only.
- To run `psql` from your laptop: SSH tunnel through EC2 (below), not a public DB.

```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name flowgrid-db-subnets \
  --db-subnet-group-description "FlowGrid private subnets" \
  --subnet-ids "$SUBNET_PRIVATE_A" "$SUBNET_PRIVATE_B"

aws ec2 authorize-security-group-ingress --group-id "$SG_DB" \
  --ip-permissions "IpProtocol=tcp,FromPort=5432,ToPort=5432,UserIdGroupPairs=[{GroupId=$SG_APP}]"
```

---

## 3. Create the instance

```bash
read -rs DB_MASTER_PASSWORD   # type it; never put it in shell history or a script

aws rds create-db-instance \
  --db-instance-identifier flowgrid-db \
  --engine postgres \
  --engine-version 16 \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --storage-type gp3 \
  --storage-encrypted \
  --db-name flowgrid \
  --master-username pw_admin \
  --master-user-password "$DB_MASTER_PASSWORD" \
  --db-subnet-group-name flowgrid-db-subnets \
  --vpc-security-group-ids "$SG_DB" \
  --no-publicly-accessible \
  --no-multi-az \
  --backup-retention-period 1 \
  --preferred-backup-window 03:00-04:00 \
  --auto-minor-version-upgrade \
  --tags Key=project,Value=flowgrid

aws rds wait db-instance-available --db-instance-identifier flowgrid-db   # ~5–10 min

aws rds describe-db-instances --db-instance-identifier flowgrid-db \
  --query 'DBInstances[0].Endpoint.Address' --output text
```

Choices to be able to justify:

| Option | FlowGrid value | Production value | Why |
|---|---|---|---|
| Instance class | `db.t4g.micro` | sized by load | Cheapest; check free-tier eligibility for *your* account |
| Multi-AZ | off | **on** | Standby in another AZ, ~60–120 s failover; doubles cost |
| Backup retention | 1 day | 7–35 days | Enables point-in-time restore within window |
| Deletion protection | off (easy teardown) | **on** | Prevents accidental `delete-db-instance` |
| `--manage-master-user-password` | optional | yes | Stores/rotates master password in Secrets Manager (small monthly cost) |

---

## 4. Users inside Postgres — don't run the app as master

Connect through an SSH tunnel from your laptop:

```bash
ssh -i ~/.ssh/pw-key.pem -N -L 15432:<rds-endpoint>:5432 ec2-user@<ec2-public-ip> &
psql "host=localhost port=15432 dbname=flowgrid user=pw_admin sslmode=require"
```

```sql
-- Owner role runs Flyway migrations; app role only reads/writes data
CREATE ROLE flowgrid_migrator LOGIN PASSWORD '...';
CREATE ROLE flowgrid_app      LOGIN PASSWORD '...';

GRANT CONNECT ON DATABASE flowgrid TO flowgrid_app, flowgrid_migrator;
CREATE SCHEMA app AUTHORIZATION flowgrid_migrator;
GRANT USAGE ON SCHEMA app TO flowgrid_app;

-- Tables Flyway creates later automatically grant DML to the app role
ALTER DEFAULT PRIVILEGES FOR ROLE flowgrid_migrator IN SCHEMA app
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowgrid_app;
ALTER DEFAULT PRIVILEGES FOR ROLE flowgrid_migrator IN SCHEMA app
  GRANT USAGE, SELECT ON SEQUENCES TO flowgrid_app;
```

Simpler acceptable variant for FlowGrid: one app user that owns the schema. Say so honestly if asked, and
explain what you'd split in production.

---

## 5. Connecting from Spring Boot

`application-prod.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/flowgrid?sslmode=require&currentSchema=app
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10        # (pool × app instances) must fit under max_connections
      connection-timeout: 5000     # fail fast (ms) instead of hanging 30 s
      max-lifetime: 1500000        # < server/network idle limits
  flyway:
    enabled: true
    user: ${FLYWAY_USER:${DB_USER}}
    password: ${FLYWAY_PASSWORD:${DB_PASSWORD}}
    schemas: app
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate           # Flyway owns the schema; Hibernate only checks it
```

- **TLS:** RDS for PostgreSQL 15+ sets `rds.force_ssl=1` by default, so non-TLS connections fail.
  `sslmode=require` encrypts; `sslmode=verify-full&sslrootcert=/path/global-bundle.pem` also
  verifies the server identity using the RDS CA bundle (stronger; mention it).
- **Failover:** the endpoint DNS name flips to the standby; Hikari will drop broken connections and
  reconnect. Code should tolerate a transient `SQLException` on the next request.
- **Pool math:** `max_connections` on a micro instance is on the order of ~100 and derived from
  instance memory. `(api pool + worker pool) × instances` must stay well under it — this starts to matter for ForgeCI (api + N workers).

---

## 6. Parameter groups — awareness level

- Server settings (`log_min_duration_statement`, `shared_buffers`, `max_connections`,
  `rds.force_ssl`) live in a **DB parameter group**. The default group is read-only; create a custom
  one to change anything.
- Parameters are **static** (need reboot) or **dynamic** (apply immediately).

```bash
aws rds create-db-parameter-group --db-parameter-group-name flowgrid-pg16 \
  --db-parameter-group-family postgres16 --description "FlowGrid PG16"

aws rds modify-db-parameter-group --db-parameter-group-name flowgrid-pg16 \
  --parameters "ParameterName=log_min_duration_statement,ParameterValue=500,ApplyMethod=immediate"

aws rds modify-db-instance --db-instance-identifier flowgrid-db \
  --db-parameter-group-name flowgrid-pg16 --apply-immediately
```

Logging slow queries (> 500 ms) feeds directly into the Week 8 k6 baseline and `EXPLAIN` work on
FlowGrid's hot queries (inventory by SKU and warehouse, orders by status and date) — see [`04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md).

---

## 7. Backups and restore

| Mechanism | What it is | Restore gives you |
|---|---|---|
| Automated backups | Daily snapshot + WAL archived every ~5 min | **New instance** at any second within retention |
| Manual snapshot | On-demand; kept until you delete it (billed) | New instance from that snapshot |
| `pg_dump` | Logical export you control | Portable SQL/custom file |

Restores always create a **new** instance with a new endpoint — your app config must change. Test a
restore once; "backups you've never restored are hopes, not backups."

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier flowgrid-db \
  --target-db-instance-identifier flowgrid-db-restored \
  --restore-time 2026-09-20T10:15:00Z \
  --db-subnet-group-name flowgrid-db-subnets --vpc-security-group-ids "$SG_DB" \
  --no-publicly-accessible
```

Teardown:

```bash
aws rds delete-db-instance --db-instance-identifier flowgrid-db \
  --skip-final-snapshot --delete-automated-backups
# or keep data: --final-db-snapshot-identifier flowgrid-final (snapshot storage is billed)
```

---

## 8. Break → Debug

| Break | Symptom | Diagnosis |
|---|---|---|
| Remove SG rule 5432 from `sg-app` | Hikari: `Connection is not available, request timed out` / `SocketTimeoutException` | From EC2: `nc -zv <endpoint> 5432` times out |
| Wrong password | `FATAL: password authentication failed for user` | Fast, explicit failure — network is fine |
| `sslmode=disable` | `no pg_hba.conf entry for host ... no encryption` | `rds.force_ssl=1` |
| Pool size 200 | `FATAL: remaining connection slots are reserved` | Check `max_connections`, `pg_stat_activity` |
| `ddl-auto: validate` with missing migration | App fails at startup: `Schema-validation: missing table` | Flyway history table |

---

## 9. Interview questions

<details><summary>Why RDS instead of Postgres in Docker on the same EC2?</summary>

Durable data needs backups, PITR, patching and failover. RDS provides those without me operating
them, and it survives the app instance being replaced. Trade-offs: cost, less control (no superuser
or OS access), and restores create new endpoints.
</details>

<details><summary>Multi-AZ vs read replica?</summary>

Multi-AZ = synchronous standby for **availability**; you can't read from the classic standby; auto
failover. Read replica = asynchronous copy for **read scaling**; can lag; manual promotion. Different
problems.
</details>

<details><summary>How is your database secured?</summary>

Private subnets, not publicly accessible, SG allowing 5432 only from the app SG, TLS enforced,
encryption at rest, app runs as a non-owner role, credentials from env/Parameter Store not the repo,
automated backups.
</details>

<details><summary>How would you restore data deleted 2 hours ago?</summary>

Point-in-time restore to a new instance at a time just before the deletion, then copy the affected
rows back (or cut over). Verify, then delete the temporary instance.
</details>
