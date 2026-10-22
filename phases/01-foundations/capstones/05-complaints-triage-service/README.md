# Capstone 5: Consumer complaints triage and SLA service

← [All capstones](../README.md) · [Starter](starter/README.md)

Weeks 8–12. Modules used: 1, 2, 3, 4.

## Business context

Banking regulators expect complaints to be acknowledged and resolved within set timelines, tracked by product and issue, and reported regularly. In Nigeria the CBN's consumer protection rules set such timelines and require complaint reporting; in the US the CFPB runs a public complaint database. Banks need internal systems that ingest complaints from many channels, route them to the right team, track deadlines, and give management a live view of where they are breaching.

You'll build that system using the **CFPB's public complaint database** as a realistic stream of millions of real complaints.

## What you'll build

### Ingestion

- A bulk importer for the CFPB CSV that loads a **filtered subset** (for example the last 12 months, or chosen products) into PostgreSQL with JDBC batching or `COPY`, idempotent on the complaint ID.
- Store the structured fields relationally (`complaint`, `company`, `product`, `issue`), and the free-text **consumer narrative** plus any extra attributes either in PostgreSQL (`TEXT`/`JSONB`) or as documents in **MongoDB** (topic 3.6). Justify your choice in the README.
- A `POST /api/v1/complaints` endpoint for new complaints from the bank's own channels (web, branch, contact centre), validated.

### Triage and SLA

- **Routing rules** (data-driven): product + issue → team (Cards, Mortgages, Collections, Fraud, Digital) and priority. Keywords in the narrative (e.g. "fraud", "unauthorized", "identity theft") raise the priority.
- **SLA clock:** acknowledgement due within 1 business day; resolution due within a configurable number of business days per priority. Use an injected `Clock`, business-day arithmetic and a holiday table.
- **Status workflow:** `RECEIVED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED | ESCALATED | CLOSED`, with illegal transitions rejected (409) and every change audited.

### API (suggested)

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/complaints?product=&state=&status=&q=&page=` | Search and filter, including **full-text search** on narratives (PostgreSQL `tsvector` + GIN index, or a MongoDB text index) |
| `GET /api/v1/complaints/{id}` | Details, history, SLA status |
| `POST /api/v1/complaints/{id}/transitions` | Change status (validated workflow) |
| `GET /api/v1/reports/sla-breaches` | Open complaints past their deadline, grouped by team and priority |
| `GET /api/v1/reports/monthly?product=` | Volumes, timely-response rate and top issues per month (SQL aggregates and window functions) |

### Quality bar

1. The importer handles quoted CSV fields with embedded commas and newlines (use a proper CSV parser such as Apache Commons CSV or Jackson's CSV module, not `split(",")`).
2. Every search used by the UI has a supporting index, shown with `EXPLAIN ANALYZE` in your README.
3. Narratives are already scrubbed of personal data by the CFPB; still treat them as sensitive: no narratives in logs or metric tags.

## Data

| | |
| --- | --- |
| Dataset | **CFPB Consumer Complaint Database** |
| Download | <https://files.consumerfinance.gov/ccdb/complaints.csv.zip> (several GB unzipped: filter while streaming, or keep the last 12 months). Overview: <https://www.consumerfinance.gov/data-research/consumer-complaints/> |
| Licence | Work of the US federal government: **public domain in the United States** (17 U.S.C. § 105). Commercial use allowed |
| Attribution (recommended) | "Data: Consumer Financial Protection Bureau, Consumer Complaint Database." |
| Caveats | Complaints are not a statistical sample, and the CFPB doesn't verify every allegation. Don't present counts as measures of company quality |

## Skills you'll demonstrate

Streaming large files and batching (1.7, 3.5), business-date arithmetic (1.7), sealed workflow states and exhaustive transitions (1.6), full-text search and indexing (3.3), MongoDB vs PostgreSQL trade-offs (3.6), pagination and Problem Details (4.2, 4.3), SLA metrics (4.5).

## Acceptance criteria

- [ ] Importing the same file twice doesn't duplicate complaints; the importer reports rows read, inserted and rejected.
- [ ] A CSV row with a multi-line narrative imports correctly.
- [ ] Routing rules assign team and priority as configured; a narrative containing "unauthorized transaction" is raised to high priority.
- [ ] SLA deadlines skip weekends and configured holidays (tests with a fixed `Clock`).
- [ ] Illegal status transitions return 409 Problem Details; legal ones are audited.
- [ ] The SLA-breach report matches a hand-checked fixture.
- [ ] Full-text search returns relevant complaints and uses an index (assert the plan or document it).

## Infrastructure and deployment

This capstone handles the most data. During import give PostgreSQL **2 vCPU / 4 GB RAM / 20–50 GB storage** (or limit yourself to 6–12 months of data: a few hundred thousand rows fit comfortably on 1 vCPU / 2 GB). MongoDB, if used: a container on the same VPS, or MongoDB Atlas's free tier. CI from the starter; deploy with the [deployment guide](../../deploy/README.md).

## Stretch goals

- A daily incremental import using the CFPB's API with a date filter.
- A materialised view for the monthly report, refreshed after each import.
- Email/SMS notification stubs when an SLA is about to breach.

## Interview talking points

- How you imported millions of messy CSV rows reliably and quickly.
- Why you chose PostgreSQL full-text search or MongoDB for narratives.
- How you modelled business days and SLA clocks, and tested them.
