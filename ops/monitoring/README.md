# Monitoring and alerting for itlearning.tech

← [Home](../../README.md) · [Moodle blueprint](../../lms/moodle/BLUEPRINT.md) · [CodeRunner and Jobe](../../lms/moodle/CODERUNNER.md)

Prometheus, Grafana and four exporters run on the Moodle server (the "Moodle VPS") and watch both servers. Everything listens on `127.0.0.1` only, so the stack opens no ports and the firewall is unchanged.

| Component | What it watches |
| --- | --- |
| Prometheus v3.15 | Stores the metrics (30 days, at most 5 GB) |
| Grafana 12.4 | Dashboards and alerting |
| node-exporter v1.12 | CPU, memory, disk and network of the Moodle VPS; a second copy on the Jobe VPS is read through `https://jobe.itlearning.tech/node-metrics/` (Caddy, Moodle server's IP only) |
| cAdvisor v0.60 (`ghcr.io/google/cadvisor`; tuned to CPU, memory and network every 60 s, because the defaults spiked to 80% of a CPU on this 2-CPU server) | CPU and memory of each container on the Moodle VPS (Moodle, the database, mail, Ollama, …) |
| blackbox-exporter v0.29 | From outside: `https://itlearning.tech`, the Jobe API, mail ports 25, 587 and 993, and certificate expiry |

Together they used about 0.2 GB of RAM after start-up (each container has a memory limit; together the limits allow 1.3 GB).

## Open Grafana

```bash
ssh -L 3000:127.0.0.1:3000 itlearning
```

Then browse <http://localhost:3000>, user `admin`. The password was generated on the first install and is only on the server:

```bash
ssh itlearning grep GRAFANA /opt/monitoring/.env
```

Dashboards are in the **ITLearning** folder:

- **ITLearning status**: one page showing the websites and mail ports up or down, response times, certificate days left, CPU, memory and disk per server, and memory and CPU per container.
- **Node Exporter Full**: every host metric in detail. It's the community dashboard 1860 by rfmoz (revision 45, LGPL-3.0); choose the server with the *Host* drop-down.

To publish Grafana at a web address instead of using the tunnel, add a DNS record and a proxy host in Nginx Proxy Manager pointing to `127.0.0.1:3000` with a certificate. Grafana's own login protects it, but keep the admin password strong.

## Alerts

Alert emails go to **admin@itlearning.tech**, sent by Grafana through this server's own mail server (port 25, no login, local delivery; Grafana greets the mail server as `itlearning.tech`, because Postfix rejects the bare server name). A test alert was delivered on 28 October 2026. Alerts are grouped by alert and repeat every 4 hours while firing.

| Alert | Fires when | Severity |
| --- | --- | --- |
| Monitoring target down | Prometheus can't scrape a server or exporter for 5 min | critical |
| Website down | itlearning.tech or the Jobe API isn't answering 200 for 3 min | critical |
| Mail port down | Port 25, 587 or 993 refuses connections for 5 min | warning |
| TLS certificate expires soon | Under 14 days left, for 1 h | warning |
| Disk almost full | Root disk over 85% for 15 min | warning |
| Memory almost exhausted | Over 90% in use for 10 min | warning |
| CPU busy for a long time | Over 90% for 15 min (on the Jobe VPS: heavy quiz use or abuse) | warning |

To add a second recipient (for example a personal address or a phone app), go to *Alerting → Contact points* in Grafana. Rules, contact point and policy are provisioned from [`grafana/provisioning/alerting/alerting.yml`](grafana/provisioning/alerting/alerting.yml); change them there, so the server and the repository stay in step.

Monitoring runs on the Moodle VPS, so if that whole server goes down, nothing on it can send an alert. A free external uptime checker on `https://itlearning.tech` (for example UptimeRobot) covers that case.

## Install or update

```bash
scp -r ops/monitoring itlearning:/tmp/ && ssh itlearning sh /tmp/monitoring/setup-monitoring.sh
```

The script copies the files to `/opt/monitoring` and keeps the existing `.env`. It then starts the containers and checks that every scrape target is up and the alert rules and dashboards are loaded. The Jobe side (node-exporter and its Caddy route) is part of [`scripts/moodle/jobe/setup-jobe.sh`](../../scripts/moodle/jobe/setup-jobe.sh).
