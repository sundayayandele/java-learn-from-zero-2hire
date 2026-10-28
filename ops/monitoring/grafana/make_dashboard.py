"""Generate grafana/dashboards/itlearning-status.json (run: python3 ops/monitoring/grafana/make_dashboard.py)."""
import json
import pathlib

DS = {"type": "prometheus", "uid": "prometheus"}
panels = []
pid = 0


def panel(kind, title, x, y, w, h, targets, unit=None, mappings=None, thresholds=None, minv=None, maxv=None, extra=None):
    global pid
    pid += 1
    p = {"id": pid, "type": kind, "title": title, "datasource": DS,
         "gridPos": {"x": x, "y": y, "w": w, "h": h},
         "targets": [{"refId": chr(65 + i), "datasource": DS, "expr": e, "legendFormat": lf, **({"instant": True} if kind in ("stat", "bargauge") else {})}
                     for i, (e, lf) in enumerate(targets)],
         "fieldConfig": {"defaults": {}, "overrides": []}, "options": {}}
    d = p["fieldConfig"]["defaults"]
    if unit:
        d["unit"] = unit
    if mappings:
        d["mappings"] = mappings
    if thresholds:
        d["thresholds"] = {"mode": "absolute", "steps": thresholds}
        d["color"] = {"mode": "thresholds"}
    if minv is not None:
        d["min"] = minv
    if maxv is not None:
        d["max"] = maxv
    if kind == "stat":
        p["options"] = {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                        "colorMode": "background", "graphMode": "none", "textMode": "value_and_name", "orientation": "horizontal"}
    if kind == "bargauge":
        p["options"] = {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                        "displayMode": "gradient", "orientation": "horizontal", "showUnfilled": True}
    if kind == "timeseries":
        p["options"] = {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                        "tooltip": {"mode": "multi", "sort": "desc"}}
    if extra:
        p.update(extra)
    panels.append(p)


UPDOWN = [{"type": "value", "options": {"0": {"text": "DOWN", "color": "red", "index": 0},
                                        "1": {"text": "UP", "color": "green", "index": 1}}}]
RG = [{"color": "red", "value": None}, {"color": "green", "value": 1}]

panel("stat", "Websites (checked from the Moodle server)", 0, 0, 12, 5,
      [('probe_success{job="blackbox-http"}', "{{instance}}")], mappings=UPDOWN, thresholds=RG)
panel("stat", "Mail ports", 12, 0, 6, 5, [('probe_success{job="blackbox-tcp"}', "{{instance}}")],
      mappings=UPDOWN, thresholds=RG)
panel("stat", "Scrape targets", 18, 0, 6, 5, [("up", "{{job}} {{instance}}")], mappings=UPDOWN, thresholds=RG)
panel("stat", "Certificate days left", 0, 5, 8, 5,
      [('(probe_ssl_earliest_cert_expiry{job="blackbox-http"} - time()) / 86400', "{{instance}}")], unit="d",
      thresholds=[{"color": "red", "value": None}, {"color": "orange", "value": 14}, {"color": "green", "value": 30}],
      extra={"options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                         "colorMode": "background", "graphMode": "none", "textMode": "value_and_name",
                         "orientation": "horizontal"}})
panel("timeseries", "Response time", 8, 5, 16, 5,
      [('probe_duration_seconds{job="blackbox-http"}', "{{instance}}")], unit="s")
USE = [{"color": "green", "value": None}, {"color": "orange", "value": 75}, {"color": "red", "value": 90}]
panel("bargauge", "CPU busy (5 min)", 0, 10, 8, 6,
      [('100 * (1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])))', "{{instance}}")],
      unit="percent", thresholds=USE, minv=0, maxv=100)
panel("bargauge", "Memory in use", 8, 10, 8, 6,
      [("100 * (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)", "{{instance}}")],
      unit="percent", thresholds=USE, minv=0, maxv=100)
panel("bargauge", "Root disk used", 16, 10, 8, 6,
      [('100 * (1 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})', "{{instance}}")],
      unit="percent", thresholds=USE, minv=0, maxv=100)
panel("timeseries", "CPU busy", 0, 16, 12, 8,
      [('100 * (1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])))', "{{instance}}")],
      unit="percent", minv=0, maxv=100)
panel("timeseries", "Network traffic out", 12, 16, 12, 8,
      [('sum by (instance) (rate(node_network_transmit_bytes_total{device!~"lo|veth.*|docker.*|br-.*"}[5m]))', "{{instance}}")],
      unit="Bps")
panel("timeseries", "Container memory (Moodle server)", 0, 24, 12, 8,
      [('container_memory_working_set_bytes{name!=""}', "{{name}}")], unit="bytes")
panel("timeseries", "Container CPU (Moodle server)", 12, 24, 12, 8,
      [('rate(container_cpu_usage_seconds_total{name!=""}[5m])', "{{name}}")], unit="short")

# Latency percentiles (p50/p90/p99) from real traffic and from the outside checks.
PAGE = 'moodle_http_response_time_seconds_hist_bucket{kind="page"}'
panel("timeseries", "Moodle page load time (real requests, PHP pages)", 0, 32, 12, 8,
      [(f"histogram_quantile({q}, sum by (le) (rate({PAGE}[5m])))", f"p{int(q * 100)}") for q in (0.5, 0.9, 0.99)],
      unit="s")
panel("timeseries", "p90 response time by kind of request", 12, 32, 12, 8,
      [("histogram_quantile(0.9, sum by (le, kind) (rate(moodle_http_response_time_seconds_hist_bucket[5m])))", "{{kind}}")],
      unit="s")
CHECK = 'caddy_http_request_duration_seconds_bucket{instance="jobe-vps", method="POST"}'
panel("timeseries", "Code-check time on Jobe (POST /runs)", 0, 40, 12, 8,
      [(f"histogram_quantile({q}, sum by (le) (rate({CHECK}[5m])))", f"p{int(q * 100)}") for q in (0.5, 0.9)],
      unit="s", extra={"description": "Caddy's largest bucket is 10 s, so a value of 10 means 10 s or more."})
panel("timeseries", "Website check time, p90 over 30 min", 12, 40, 12, 8,
      [('quantile_over_time(0.9, probe_duration_seconds{job="blackbox-http"}[30m])', "{{instance}}")], unit="s")
panel("timeseries", "itlearning.tech requests per minute by kind", 0, 48, 12, 8,
      [("sum by (kind) (rate(moodle_http_response_count_total[5m])) * 60", "{{kind}}")], unit="short")
panel("timeseries", "Error responses per minute (5xx)", 12, 48, 12, 8,
      [('sum by (status) (rate(moodle_http_response_count_total{status=~"5.."}[5m])) * 60', "itlearning.tech {{status}}"),
       ('sum by (code) (rate(caddy_http_request_duration_seconds_count{instance="jobe-vps", code=~"5.."}[5m])) * 60', "Jobe {{code}}")],
      unit="short")
panel("timeseries", "CPU and memory, p90 over 1 h", 0, 56, 24, 8,
      [('quantile_over_time(0.9, (100 * (1 - avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m]))))[1h:1m])', "CPU {{instance}}"),
       ("quantile_over_time(0.9, (100 * (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))[1h:1m])", "memory {{instance}}")],
      unit="percent", minv=0, maxv=100)

dashboard = {"uid": "itl-status", "title": "ITLearning status", "tags": ["itlearning"], "timezone": "browser",
             "schemaVersion": 39, "version": 1, "refresh": "1m", "time": {"from": "now-6h", "to": "now"},
             "panels": panels, "templating": {"list": []}, "annotations": {"list": []}}
path = pathlib.Path(__file__).resolve().parent / "dashboards" / "itlearning-status.json"
with open(path, "w") as f:
    json.dump(dashboard, f, indent=2)
    f.write("\n")
print("panels:", len(panels))
