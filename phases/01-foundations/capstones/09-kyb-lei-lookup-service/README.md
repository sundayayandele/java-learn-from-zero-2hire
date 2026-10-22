# Capstone 9: KYB legal entity lookup service

← [All capstones](../README.md) · [Starter](starter/README.md)

Weeks 8–12. Modules used: 1, 2, 3, 4.

## Business context

Before a bank opens an account for a company, lends to it or trades with it, it must "know your business" (KYB): confirm the legal entity exists, its registered address and status, and who owns it. In capital markets and cross-border payments, entities are identified by the **Legal Entity Identifier (LEI)**, a 20-character code (ISO 17442) managed by GLEIF, with public reference data and parent relationships. Corporate onboarding teams search this data constantly, and payment systems validate LEIs on messages.

You'll build an internal **LEI reference-data service**: bulk-load the global LEI dataset (or a large country subset), keep it current with delta files, and serve fast lookups and searches.

## What you'll build

### Ingestion

- Bulk load of the **GLEIF Golden Copy** (Level 1 "who is who" CSV): LEI, legal name, other names, legal and headquarters addresses, jurisdiction, legal form, entity status, registration status, last update date. Optionally Level 2 relationships ("who owns whom": direct and ultimate parents).
- Use PostgreSQL `COPY` through the JDBC driver's `CopyManager` (or batched inserts) into a staging table, then upsert (`INSERT ... ON CONFLICT (lei) DO UPDATE ... WHERE excluded.last_update > lei_record.last_update`).
- **Delta updates:** apply GLEIF delta files (changes in the last 8 hours, 24 hours, etc.) idempotently; record each run.
- Validate every LEI's **check digits** (ISO 17442 uses the ISO 7064 MOD 97-10 scheme, like IBANs) and report invalid records instead of loading them.

### API (suggested)

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/entities/{lei}` | Full record, plus parents if loaded |
| `GET /api/v1/entities?name=&country=&status=&page=` | Search by name (prefix and fuzzy via `pg_trgm`), country, status |
| `GET /api/v1/entities/{lei}/validation` | Is the LEI well-formed (check digits), issued, and currently `ISSUED` (vs `LAPSED`/`RETIRED`)? |
| `POST /api/v1/entities/validations` | Bulk validation of up to 1,000 LEIs (for a payments team) |
| `GET /api/v1/admin/import-runs` | Import and delta history |

### Quality bar

1. Lookups by LEI in < 5 ms server time; name searches in < 100 ms on the loaded dataset, proved with `EXPLAIN ANALYZE` and an index strategy (B-tree on `lei`, trigram GIN on normalised names, composite indexes for common filters).
2. The full load is **resumable or repeatable**: running it twice yields the same table.
3. Imports never block reads (load into staging, then upsert in batches, or swap tables in a transaction).
4. Cache hot lookups with a TTL; invalidate entries touched by a delta.

## Data

| | |
| --- | --- |
| Dataset | **GLEIF LEI Golden Copy** (Level 1 and optional Level 2): 2.5 million+ entities worldwide |
| Download | [GLEIF Golden Copy and delta files](https://www.gleif.org/en/lei-data/gleif-golden-copy); API for small tests: `https://api.gleif.org/api/v1/lei-records?filter[entity.legalAddress.country]=NG` |
| Licence | **CC0 1.0** ([GLEIF terms of use](https://www.gleif.org/en/meta/lei-data-terms-of-use)): no restrictions, commercial use allowed |
| Attribution | Not required; recommended: "LEI data: GLEIF, CC0." |
| Smaller option | Load only selected countries (e.g. NG, GB, US, DE, ZA) by filtering while streaming the CSV, to keep the database under 2 GB |

## Skills you'll demonstrate

Streaming large files and check-digit algorithms (1.2, 1.7), bulk loading with COPY and upserts (3.5), indexing and trigram search (3.3), migrations for large tables (3.4), caching with invalidation (3.6), pagination and Problem Details (4.2, 4.3), import metrics (4.5).

## Acceptance criteria

- [ ] LEI check-digit validation passes for known valid LEIs (for example GLEIF's own LEI `506700GE1G29325QX363`) and fails for altered ones.
- [ ] Loading the same file twice leaves identical row counts and data.
- [ ] A delta file updates changed records only when its `last_update` is newer.
- [ ] Name search finds entities with typos and different word order, using an index (plan documented).
- [ ] Bulk validation of 1,000 LEIs returns per-LEI results.
- [ ] Reads continue to work during an import (demonstrate with a concurrent test or a documented manual test).

## Infrastructure and deployment

Full global load: PostgreSQL **2 vCPU / 4 GB / 30–50 GB** while loading and indexing. Country subset: 1 vCPU / 2 GB / 10 GB. The app: 0.5–1 vCPU / 1 GB. Enable `pg_trgm` in a migration (supported on RDS, Azure Flexible Server and Cloud SQL). CI from the starter with a small fixture; deploy with the [deployment guide](../../deploy/README.md).

## Stretch goals

- Level 2 relationships with an "ultimate parent" endpoint using a recursive CTE.
- ISIN-to-LEI mapping (GLEIF publishes it) for securities use cases.
- A change feed: `GET /entities/changes?since=` for downstream systems.

## Interview talking points

- How you loaded millions of rows fast, safely and repeatably.
- The index strategy behind sub-100 ms fuzzy search.
- How the ISO 7064 check digits work and why they catch typos.
