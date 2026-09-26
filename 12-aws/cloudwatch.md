# CloudWatch — Logs, Metrics, Alarms

> **Practical use:** FlowGrid container logs land in CloudWatch Logs, you can search them with Logs
> Insights, and an alarm emails you when CPU is pegged, the instance fails a status check, the app
> logs errors, or your bill crosses \$10.
> **Interview use:** "How do you know your service is broken?", "Where do your logs go?", "metrics vs logs?"

← [RDS](./rds.md) · Next: [Deploy walkthrough](./deploy-walkthrough.md)

---

## 1. Model

| Concept | Meaning |
|---|---|
| **Log group** | Container for logs of one app/component: `/flowgrid/api`, `/flowgrid/nginx` (ForgeCI later adds `/forgeci/worker`). Has a **retention** setting (default: never expire — costs grow forever) |
| **Log stream** | Sequence of events from one source (one container / instance) inside a group |
| **Metric** | Time series identified by namespace + name + dimensions: `AWS/EC2 · CPUUtilization · InstanceId=i-…` |
| **Namespace** | `AWS/EC2`, `AWS/RDS`, `AWS/Billing`, or your own `FlowGrid` |
| **Metric filter** | Turns log lines matching a pattern into a metric (e.g. count of `ERROR`) |
| **Alarm** | Watches a metric: OK / ALARM / INSUFFICIENT_DATA; triggers actions on state change |
| **SNS topic** | Pub/sub channel; alarms publish, your email subscribes |
| **Logs Insights** | Query language over log groups |

Default EC2 metrics (every 5 min, free): CPU, network, disk I/O on instance store, status checks,
CPU credits. **Not included: memory and disk-space usage** — those need the CloudWatch agent.
RDS publishes CPU, `FreeableMemory`, `FreeStorageSpace`, `DatabaseConnections`, read/write latency.

---

## 2. Getting container logs into CloudWatch

### Option A (default for all four projects): Docker `awslogs` log driver

Create groups first, with retention:

```bash
for g in api nginx; do
  aws logs create-log-group --log-group-name /flowgrid/$g
  aws logs put-retention-policy --log-group-name /flowgrid/$g --retention-in-days 7
done
```

Compose (production override `compose.prod.yml`):

```yaml
x-awslogs: &awslogs
  driver: awslogs
  options:
    awslogs-region: eu-west-1
    awslogs-create-group: "false"

services:
  api:
    logging:
      <<: *awslogs
      options:
        awslogs-region: eu-west-1
        awslogs-group: /flowgrid/api
        awslogs-stream: api
  nginx:
    logging:
      driver: awslogs
      options:
        awslogs-region: eu-west-1
        awslogs-group: /flowgrid/nginx
        awslogs-stream: nginx
```

> YAML merge (`<<:`) is shallow: the `options` map above replaces the anchor's `options` entirely,
> which is why region is repeated. When in doubt, write each block out in full like `nginx`.

- The driver runs inside the **Docker daemon on the host** and uses the instance role (needs
  `logs:CreateLogStream`, `logs:PutLogEvents` — see [iam.md](./iam.md#42-permissions-policy--least-privilege-for-the-app)).
- With `awslogs`, `docker logs` still works on recent Docker versions (dual logging), but don't rely
  on local logs surviving.
- Spring Boot should log **JSON to stdout** (Spring Boot 3.4+ has structured logging:
  `logging.structured.format.console=ecs` or `logstash`), so Insights can query fields.
  See [`05-spring-boot/06-logging-actuator.md`](../05-spring-boot/06-logging-actuator.md).

### Option B: CloudWatch agent

Use when you also want **memory/disk metrics** or to tail files on disk.

```bash
sudo dnf install -y amazon-cloudwatch-agent
# role also needs the AWS-managed policy CloudWatchAgentServerPolicy
```

`/opt/aws/amazon-cloudwatch-agent/etc/config.json`:

```json
{
  "agent": { "metrics_collection_interval": 60 },
  "metrics": {
    "namespace": "FlowGrid/Host",
    "append_dimensions": { "InstanceId": "${aws:InstanceId}" },
    "metrics_collected": {
      "mem":  { "measurement": ["mem_used_percent"] },
      "disk": { "measurement": ["used_percent"], "resources": ["/"] }
    }
  },
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          { "file_path": "/var/log/cloud-init-output.log",
            "log_group_name": "/flowgrid/host", "log_stream_name": "{instance_id}-cloud-init" }
        ]
      }
    }
  }
}
```

```bash
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/config.json
```

---

## 3. Querying logs

CLI tail (great during a deploy):

```bash
aws logs tail /flowgrid/api --follow --since 10m
aws logs tail /flowgrid/api --since 1h --filter-pattern ERROR
```

Logs Insights (console → Logs Insights, select `/flowgrid/api`):

```
fields @timestamp, level, message, requestId
| filter level = "ERROR"
| sort @timestamp desc
| limit 50
```

Latency / status breakdown if you log `status` and `durationMs` per request:

```
filter ispresent(durationMs)
| stats count(*) as requests, avg(durationMs) as avgMs, pct(durationMs, 95) as p95
  by bin(5m)
```

Follow one request across services by the `requestId` you put in MDC (the MDC request-ID habit from FlowGrid M1/M5):

```
fields @timestamp, @logStream, message
| filter requestId = "8c1f0e7a-..."
| sort @timestamp asc
```

---

## 4. Metrics from logs: count errors

```bash
aws logs put-metric-filter \
  --log-group-name /flowgrid/api \
  --filter-name api-error-count \
  --filter-pattern '{ $.level = "ERROR" }' \
  --metric-transformations \
    metricName=ApiErrors,metricNamespace=FlowGrid,metricValue=1,defaultValue=0
```

For non-JSON logs the pattern would be a term like `ERROR`. Your app can also publish business
metrics (e.g. `ChecksFailed`) — via `PutMetricData`, or Micrometer's CloudWatch registry
(`micrometer-registry-cloudwatch2`). The projects expose Micrometer metrics via Actuator; exporting them to
CloudWatch is optional.

---

## 5. Alarms → SNS → email

```bash
TOPIC_ARN=$(aws sns create-topic --name flowgrid-alerts --query TopicArn --output text)
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol email --notification-endpoint you@example.com
# click the confirmation link in your inbox — unconfirmed subscriptions receive nothing
```

CPU high for 10 minutes:

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name flowgrid-cpu-high \
  --namespace AWS/EC2 --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
  --statistic Average --period 300 --evaluation-periods 2 \
  --threshold 80 --comparison-operator GreaterThanThreshold \
  --alarm-actions "$TOPIC_ARN" --ok-actions "$TOPIC_ARN"
```

Instance status check failed → notify **and** auto-recover:

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name flowgrid-status-check \
  --namespace AWS/EC2 --metric-name StatusCheckFailed_System \
  --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
  --statistic Maximum --period 60 --evaluation-periods 2 \
  --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold \
  --alarm-actions "$TOPIC_ARN" "arn:aws:automate:eu-west-1:ec2:recover"
```

Application errors (from the metric filter above):

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name flowgrid-api-errors \
  --namespace FlowGrid --metric-name ApiErrors \
  --statistic Sum --period 300 --evaluation-periods 1 \
  --threshold 5 --comparison-operator GreaterThanOrEqualToThreshold \
  --treat-missing-data notBreaching \
  --alarm-actions "$TOPIC_ARN"
```

RDS storage almost full (`FreeStorageSpace` is in **bytes**):

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name flowgrid-db-storage-low \
  --namespace AWS/RDS --metric-name FreeStorageSpace \
  --dimensions Name=DBInstanceIdentifier,Value=flowgrid-db \
  --statistic Minimum --period 300 --evaluation-periods 1 \
  --threshold 2000000000 --comparison-operator LessThanThreshold \
  --alarm-actions "$TOPIC_ARN"
```

Test an alarm without breaking anything:

```bash
aws cloudwatch set-alarm-state --alarm-name flowgrid-cpu-high \
  --state-value ALARM --state-reason "manual test"
```

---

## 6. Billing alarm (do this on day 1)

- Billing metrics exist **only in `us-east-1`**, regardless of where your resources are.
- Enable first: Billing console → Billing preferences → **Receive CloudWatch billing alerts**.
- `EstimatedCharges` updates a few times a day → use a 6-hour period.

```bash
BILLING_TOPIC=$(aws sns create-topic --name billing-alerts --region us-east-1 \
  --query TopicArn --output text)
aws sns subscribe --region us-east-1 --topic-arn "$BILLING_TOPIC" \
  --protocol email --notification-endpoint you@example.com

aws cloudwatch put-metric-alarm --region us-east-1 \
  --alarm-name billing-over-10-usd \
  --namespace AWS/Billing --metric-name EstimatedCharges \
  --dimensions Name=Currency,Value=USD \
  --statistic Maximum --period 21600 --evaluation-periods 1 \
  --threshold 10 --comparison-operator GreaterThanThreshold \
  --alarm-actions "$BILLING_TOPIC"
```

Also create an **AWS Budget** (forecast-based alerts) — see [cost-safety.md](./cost-safety.md#2-budgets-and-alarms).

---

## 7. Cost notes

- CloudWatch has an always-free allowance (a small number of custom metrics and alarms, a few GB of
  log ingestion/storage). Beyond that, **log ingestion per GB** is the usual surprise. DEBUG SQL logging,
  or ForgeCI workers shipping full build logs through CloudWatch instead of Postgres, adds up — keep prod at INFO.
- Always set **retention** on log groups.
- Alarms on high-resolution (10 s) metrics cost more; 60–300 s periods are fine for these projects.

---

## 8. Metrics vs logs vs traces (interview framing)

| Signal | Answers | FlowGrid example |
|---|---|---|
| **Metrics** | "Is something wrong? How much?" Cheap, aggregated, alertable | CPU, request rate, p95 order-creation latency, reservation conflicts/min |
| **Logs** | "What exactly happened for this request?" | Stack trace with `requestId` |
| **Traces** | "Where did the time go across services?" | (Awareness: OpenTelemetry, AWS X-Ray) |

Alert on **symptoms users feel** (error rate, latency, health check failing) rather than every cause.

---

## 9. Interview questions

<details><summary>How would you find out why a request failed yesterday at 14:02?</summary>

Get the request ID (from the client error body or response header), then Logs Insights over the
api (and, for ForgeCI, worker) log groups filtered by that ID, sorted by timestamp, and read the stack trace.
Correlate with metrics at that minute (DB connections, CPU, deploy time).
</details>

<details><summary>Why aren't memory metrics shown for EC2 by default?</summary>

The hypervisor can see CPU/network/disk I/O, but memory usage is only known inside the guest OS. The
CloudWatch agent running in the OS publishes `mem_used_percent`.
</details>

<details><summary>What alarms would you set for FlowGrid?</summary>

Billing, EC2 status check (with recover), CPU sustained high, API error count (metric filter), RDS
free storage and connections, and an external health check of `/actuator/health` from outside AWS
(Route 53 health checks or any external uptime checker) — the box can't report its own death.
</details>
