"""Builders for the three diagram types whose shape is shared by every capstone (infra, cloud, technical).

The logical and solution diagrams are capstone-specific and are written by hand in capstones.py.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from model import Diagram, Edge, Group, Node


@dataclass
class Profile:
    slug: str
    title: str
    app: str                                   # container/image name
    summary: str
    actors: list[tuple[str, str]]              # (id, label) people or client systems
    externals: list[tuple[str, str, str]] = field(default_factory=list)   # (id, label, protocol)
    job: str | None = None                     # scheduled/batch job description, if any
    redis: bool = False
    mongo: bool = False
    files: bool = False                        # object storage for uploaded/imported files
    controllers: list[str] = field(default_factory=list)
    services: list[str] = field(default_factory=list)
    repositories: list[str] = field(default_factory=list)


def infra(p: Profile) -> Diagram:
    vps = [Node("fw", "Firewall (ufw)\n22 / 80 / 443 only", "infra"),
           Node("caddy", "Caddy reverse proxy\nTLS (Let's Encrypt)", "infra"),
           Node("app", f"{p.app} container\nSpring Boot 4 · JVM 21\n1 GB, MaxRAMPercentage=75", "service"),
           Node("pg", "PostgreSQL 17 container\nDocker volume", "db")]
    if p.redis:
        vps.append(Node("redis", "Redis 8 container", "store"))
    if p.mongo:
        vps.append(Node("mongo", "MongoDB 8 container", "store"))
    if p.files:
        vps.append(Node("vol", "File volume\n(uploads / imports)", "store"))
    if p.job:
        vps.append(Node("job", f"In-app scheduler\n{p.job}", "job"))
    vps.append(Node("backup", "Cron: pg_dump daily\n14-day retention", "job"))
    columns = [
        Group("Internet", [Node("users", "Users and client apps", "actor"), Node("dev", "Developer", "actor")]),
        Group("GitHub (CI/CD)", [Node("gha", "GitHub Actions\nbuild · test · image", "infra"),
                                 Node("ghcr", "GHCR\ncontainer registry", "store")]),
        Group("VPS: Ubuntu 24.04 LTS · 2 vCPU / 4 GB / 40 GB SSD", vps),
        Group("Off-site", [Node("offsite", "Backup storage\n(object storage / other host)", "store")]),
    ]
    edges = [Edge("users", "fw", "HTTPS 443"), Edge("fw", "caddy"), Edge("caddy", "app", "HTTP 8080 (internal network)"),
             Edge("app", "pg", "JDBC 5432"), Edge("dev", "gha", "git push / PR"), Edge("gha", "ghcr", "push image:sha"),
             Edge("gha", "app", "SSH: compose pull && up", True), Edge("ghcr", "app", "docker pull", True),
             Edge("backup", "pg", "pg_dump"), Edge("backup", "offsite", "copy", True)]
    if p.redis:
        edges.append(Edge("app", "redis", "RESP 6379"))
    if p.mongo:
        edges.append(Edge("app", "mongo", "27017"))
    if p.files:
        edges.append(Edge("app", "vol", "read / write"))
    if p.job:
        edges.append(Edge("job", "app", "triggers", True))
    if p.externals:
        columns.append(Group("External systems", [Node(i, l, "ext") for i, l, _ in p.externals]))
        edges += [Edge("app", i, proto) for i, _, proto in p.externals]
    expl = (f"**Infrastructure architecture** shows *where things run* on the cheapest production-like target, a single "
            f"VPS (for example SSDNodes). Only Caddy is reachable from the internet; it terminates TLS and forwards to "
            f"`{p.app}` on Docker's internal network, so the database is never exposed (Docker port publishing bypasses "
            f"`ufw`, so nothing else publishes ports). Images are built once by GitHub Actions, stored in GHCR by commit "
            f"SHA, and pulled onto the server; backups leave the machine daily. Sizing and every command are in the "
            f"[deployment guide](../../../deploy/README.md#3-deploy-to-a-vps).")
    return Diagram("infra", f"{p.title}: infrastructure architecture (VPS)", expl, columns, edges)


def cloud(p: Profile) -> Diagram:
    data = [Node("rds", "Amazon RDS PostgreSQL 17\nprivate subnets · backups · Multi-AZ in prod", "db")]
    if p.redis:
        data.append(Node("cache", "ElastiCache (Redis/Valkey)", "store"))
    if p.mongo:
        data.append(Node("docdb", "MongoDB Atlas on AWS\n(or Amazon DocumentDB)", "store"))
    if p.files:
        data.append(Node("s3", "Amazon S3\nuploaded and imported files", "store"))
    platform = [Node("ecr", "Amazon ECR\nimages by commit SHA", "store"),
                Node("sm", "AWS Secrets Manager\nDB credentials (rotated)", "infra"),
                Node("cw", "CloudWatch\nlogs · metrics · alarms", "infra")]
    if p.job:
        platform.append(Node("sched", f"EventBridge Scheduler\n{p.job}", "job"))
    columns = [
        Group("Users", [Node("users", "Users and client apps", "actor")]),
        Group("AWS: Edge", [Node("alb", "ECS Express Mode\nHTTPS Application Load Balancer", "infra")]),
        Group("AWS: Compute (VPC)", [Node("ecs", f"ECS Fargate service\n{p.app}\n0.5–1 vCPU / 1–2 GB, 1–2 tasks", "service")]),
        Group("AWS: Data (private)", data),
        Group("AWS: Platform services", platform),
        Group("CI/CD", [Node("ci", "GitHub Actions / CodePipeline\n(OIDC, no stored keys)", "infra")]),
    ]
    edges = [Edge("users", "alb", "HTTPS"), Edge("alb", "ecs", "HTTP 8080 · readiness probe"),
             Edge("ecs", "rds", "JDBC + TLS"), Edge("ecs", "sm", "secret injected at start", True),
             Edge("ecs", "cw", "logs, metrics", True), Edge("ci", "ecr", "push image"), Edge("ecr", "ecs", "pull", True)]
    if p.redis:
        edges.append(Edge("ecs", "cache", "TLS 6379"))
    if p.mongo:
        edges.append(Edge("ecs", "docdb", "TLS 27017"))
    if p.files:
        edges.append(Edge("ecs", "s3", "IAM role · HTTPS"))
    if p.job:
        edges.append(Edge("sched", "alb", "calls admin endpoint", True))
    if p.externals:
        columns.append(Group("External systems", [Node(i, l, "ext") for i, l, _ in p.externals]))
        edges += [Edge("ecs", i, f"{proto} (egress)") for i, _, proto in p.externals]
    services = ["Amazon RDS for PostgreSQL | Azure Database for PostgreSQL Flexible Server | Cloud SQL for PostgreSQL",
                "ECS Express Mode (Fargate) | Azure Container Apps | Cloud Run",
                "Amazon ECR | Azure Container Registry | Artifact Registry",
                "AWS Secrets Manager | Azure Key Vault | Secret Manager",
                "CloudWatch | Azure Monitor / Log Analytics | Cloud Logging & Monitoring"]
    if p.job:
        services.append("EventBridge Scheduler | Container Apps jobs | Cloud Scheduler")
    if p.redis:
        services.append("ElastiCache | Azure Cache for Redis / Managed Redis | Memorystore")
    if p.files:
        services.append("Amazon S3 | Azure Blob Storage | Cloud Storage")
    if p.mongo:
        services.append("MongoDB Atlas / DocumentDB | MongoDB Atlas / Cosmos DB for MongoDB | MongoDB Atlas")
    table = "\n".join(f"| {s} |" for s in services)
    expl = (f"**Cloud architecture** maps the service onto managed cloud building blocks, drawn for AWS (ECS Express "
            f"Mode, the AWS-recommended path for new accounts since App Runner closed to new customers in 2026). The "
            f"database sits in private subnets; credentials come from the secret store, never from the image; images are "
            f"immutable and tagged by commit SHA. The same design on the other clouds:\n\n"
            f"| AWS | Azure | GCP |\n| --- | --- | --- |\n{table}\n\n"
            f"Step-by-step setup for all three: [deployment guide](../../../deploy/README.md).")
    return Diagram("cloud", f"{p.title}: cloud architecture (AWS reference, Azure/GCP equivalents)", expl, columns, edges)


def technical(p: Profile) -> Diagram:
    web = [Node(f"c{i}", c, "comp") for i, c in enumerate(p.controllers)] + \
          [Node("advice", "ApiExceptionHandler\nRFC 9457 Problem Details", "comp"),
           Node("valid", "Bean Validation\n(DTO records)", "comp")]
    svc = [Node(f"s{i}", s, "service") for i, s in enumerate(p.services)]
    per = [Node(f"r{i}", r, "comp") for i, r in enumerate(p.repositories)] + [Node("flyway", "Flyway migrations\nddl-auto=validate", "comp")]
    cross = [Node("cfg", "@ConfigurationProperties\n(validated)", "comp"),
             Node("act", "Actuator + Micrometer\nhealth · readiness · Prometheus", "comp")]
    if p.job:
        cross.append(Node("sched", f"Scheduler\n{p.job}", "job"))
    if p.redis:
        cross.append(Node("cachec", "Spring Cache\n(Redis)", "comp"))
    if p.externals:
        cross.append(Node("http", "RestClient\ntimeouts · retries", "comp"))
    infra_nodes = [Node("pg", "PostgreSQL 17", "db")]
    if p.redis:
        infra_nodes.append(Node("redis", "Redis", "store"))
    if p.mongo:
        infra_nodes.append(Node("mongo", "MongoDB", "store"))
    if p.files:
        infra_nodes.append(Node("files", "File / object storage", "store"))
    infra_nodes.append(Node("prom", "Prometheus / Grafana", "infra"))
    columns = [Group("Clients", [Node("client", "HTTP clients\n(apps, partners, ops)", "actor")]),
               Group("Web layer: Spring MVC", web), Group("Service layer", svc),
               Group("Persistence", per), Group("Cross-cutting", cross), Group("Runtime dependencies", infra_nodes)]
    edges = [Edge("client", "c0", "JSON over HTTPS")]
    edges += [Edge(f"c{i}", f"s{min(i, len(p.services) - 1)}") for i in range(len(p.controllers))]
    edges += [Edge(f"s{i}", f"r{min(i, len(p.repositories) - 1)}") for i in range(len(p.services))]
    edges += [Edge(f"r{i}", "pg", "JDBC") for i in range(len(p.repositories))]
    edges += [Edge("flyway", "pg", "migrate on start"), Edge("prom", "act", "scrape /actuator/prometheus", True),
              Edge("c0", "advice", "exceptions", True), Edge("c0", "valid", "@Valid", True)]
    if p.job:
        edges.append(Edge("sched", "s0", "invokes", True))
    if p.redis:
        edges += [Edge(f"s{len(p.services) - 1}", "cachec", "@Cacheable"), Edge("cachec", "redis")]
    if p.mongo:
        edges.append(Edge(f"s{len(p.services) - 1}", "mongo", "Spring Data MongoDB"))
    if p.files:
        edges.append(Edge("s0", "files", "store / read"))
    if p.externals:
        edges.append(Edge("s0", "http", "fetch"))
        columns.append(Group("External", [Node(i, l, "ext") for i, l, _ in p.externals]))
        edges += [Edge("http", i, proto) for i, _, proto in p.externals]
    expl = ("**Technical architecture** shows *how the code is organised* inside the Spring Boot 4 service: thin "
            "controllers that validate and translate errors, services that hold the business rules and transaction "
            "boundaries, and a persistence layer (JPA where entity behaviour and locking help, `JdbcClient`/SQL for "
            "reporting and bulk work). Cross-cutting concerns (configuration, health, metrics, scheduling, outbound HTTP) "
            "are framework features configured once. Package by feature in code; the layers here are logical.")
    return Diagram("technical", f"{p.title}: technical architecture (inside the service)", expl, columns, edges, "TB")
