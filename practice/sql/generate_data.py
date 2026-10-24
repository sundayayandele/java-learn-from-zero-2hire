#!/usr/bin/env python3
"""Generate src/test/resources/db/data.sql deterministically (synthetic, CC0). Run: python generate_data.py

The data deliberately contains the patterns the drills look for: duplicate references, dormant accounts,
customers without transactions, consecutive-day streaks, structuring deposits, balance mismatches and referrals.
"""
import random
from datetime import date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

r = random.Random(2026)
FIRST = ["Ada", "Bayo", "Chika", "Dayo", "Emeka", "Funke", "Gbenga", "Halima", "Ife", "Jide", "Kemi", "Lola",
         "Musa", "Nkem", "Obi", "Pelumi", "Rukky", "Segun", "Tobi", "Uche", "Yemi", "Zainab", "Kunle", "Amaka"]
LAST = ["Adeyemi", "Okafor", "Bello", "Eze", "Ogunleye", "Abubakar", "Nwosu", "Balogun", "Ibrahim", "Okeke"]
MERCHANTS = {"groceries": ["Shoprite", "Spar", "Justrite"], "transport": ["Uber", "Bolt"],
             "shopping": ["Jumia", "Konga"], "utilities": ["IKEDC", "EKEDC", "DSTV"], "telecom": ["MTN", "Airtel", "Glo"]}
out = []
q = lambda s: "'" + s.replace("'", "''") + "'"

branches = [(1, "Lekki Phase 1", "Lagos"), (2, "Wuse II", "Abuja"), (3, "Trans-Amadi", "Port Harcourt"), (4, "Bodija", "Ibadan")]
out += [f"INSERT INTO branch VALUES ({b[0]}, {q(b[1])}, {q(b[2])});" for b in branches]

customers = []
for cid in range(1, 41):
    name = f"{FIRST[(cid * 7) % len(FIRST)]} {LAST[(cid * 3) % len(LAST)]}"
    branch = (cid % 4) + 1
    joined = date(2024, 1, 1) + timedelta(days=r.randint(0, 600))
    referred = None if cid <= 6 else (r.choice([1, 2, 3, 4, 5, 6, None, None]))
    customers.append((cid, name, branch, joined, referred))
    out.append(f"INSERT INTO customer VALUES ({cid}, {q(name)}, {branch}, '{joined}', {referred if referred else 'NULL'});")

accounts = []
aid = 0
for c in customers:
    for typ in (["CURRENT", "SAVINGS"] if c[0] % 3 else ["CURRENT", "SAVINGS", "DOMICILIARY"]):
        aid += 1
        accounts.append({"id": aid, "number": f"{1000000000 + aid * 7919:010d}", "customer": c[0], "type": typ,
                         "currency": "USD" if typ == "DOMICILIARY" else "NGN", "opened": c[3], "status": "ACTIVE"})

NO_TXN_CUSTOMERS = {38, 39, 40}          # drill 3
DORMANT_ACCOUNTS = {5, 9}                 # drill 12: ACTIVE but nothing in the last 90 days
DEBIT_ONLY_CUSTOMERS = {36, 37}           # drill 24: net outflow every month
txns = []
tid = 0
end = datetime(2026, 10, 31, 23, 0)
def add(acc, when, amount, direction, channel, ref=None, merchant=None, category=None):
    global tid
    tid += 1
    txns.append({"id": tid, "account": acc, "at": when, "amount": Decimal(amount).quantize(Decimal("0.01")),
                 "direction": direction, "channel": channel, "ref": ref or f"TRF-{tid:06d}",
                 "merchant": merchant, "category": category})

for a in accounts:
    if a["customer"] in NO_TXN_CUSTOMERS:
        continue
    n = r.randint(8, 22)
    last_day = end - timedelta(days=120) if a["id"] in DORMANT_ACCOUNTS else end
    first_day = datetime(2026, 1, 1) + timedelta(days=30 * (a["customer"] % 7))     # drill 22: staggered starts
    for _ in range(n):
        when = first_day + timedelta(minutes=r.randint(0, int((last_day - first_day).total_seconds() // 60)))
        if r.random() < 0.35 and a["customer"] not in DEBIT_ONLY_CUSTOMERS:
            add(a["id"], when, r.choice([50000, 150000, 250000, 350000, 420000]) + r.randint(0, 99) / 100, "CREDIT", r.choice(["NIP", "WEB"]))
        else:
            cat = r.choice(list(MERCHANTS))
            add(a["id"], when, r.randint(500, 90000) + r.randint(0, 99) / 100, "DEBIT", r.choice(["POS", "NIP", "USSD", "ATM", "WEB"]),
                merchant=r.choice(MERCHANTS[cat]), category=cat)

# drill 2: duplicate references
add(3, datetime(2026, 9, 3, 10, 0), 12000, "DEBIT", "NIP", ref="TRF-DUP-0001")
add(3, datetime(2026, 9, 3, 10, 1), 12000, "DEBIT", "NIP", ref="TRF-DUP-0001")
add(14, datetime(2026, 8, 20, 9, 0), 7500, "DEBIT", "WEB", ref="TRF-DUP-0002")
add(14, datetime(2026, 8, 20, 9, 5), 7500, "DEBIT", "WEB", ref="TRF-DUP-0002")
add(15, datetime(2026, 8, 21, 9, 5), 7500, "DEBIT", "WEB", ref="TRF-DUP-0002")
# drill 16: consecutive-day streaks (customer of account 20 on 4 days, account 30 on 3 days)
for d in range(4):
    add(20, datetime(2026, 7, 10 + d, 12, 0), 1000 + d, "DEBIT", "POS", merchant="Spar", category="groceries")
for d in range(3):
    add(30, datetime(2026, 6, 1 + d, 18, 0), 2000 + d, "DEBIT", "USSD", merchant="MTN", category="telecom")
# drill 23: structuring: three credits just under 1,000,000 within a week on account 12, two on account 25
for d, amt in [(0, "990000.00"), (2, "985500.00"), (5, "999999.99")]:
    add(12, datetime(2026, 5, 4 + d, 11, 0), amt, "CREDIT", "WEB")
for d, amt in [(0, "950000.00"), (10, "960000.00")]:
    add(25, datetime(2026, 4, 1 + d, 11, 0), amt, "CREDIT", "WEB")

# balances: the sum of credits minus debits from transactions, except two deliberate mismatches (drill 19)
for a in accounts:
    bal = sum((t["amount"] if t["direction"] == "CREDIT" else -t["amount"]) for t in txns if t["account"] == a["id"])
    if a["id"] in (7, 18):
        bal += Decimal("150.00") if a["id"] == 7 else Decimal("-2000.00")
    a["balance"] = bal
for a in accounts:
    out.append(f"INSERT INTO account VALUES ({a['id']}, '{a['number']}', {a['customer']}, '{a['type']}', '{a['currency']}', "
               f"'{a['opened']}', '{a['status']}', {a['balance']});")
for t in sorted(txns, key=lambda t: t["id"]):
    out.append(f"INSERT INTO txn VALUES ({t['id']}, {t['account']}, '{t['at']:%Y-%m-%d %H:%M:%S}', {t['amount']}, "
               f"'{t['direction']}', '{t['channel']}', {q(t['ref'])}, {q(t['merchant']) if t['merchant'] else 'NULL'}, "
               f"{q(t['category']) if t['category'] else 'NULL'});")

employees = [(1, "Ngozi Adebayo", None, None, 9500000), (2, "Tunde Bakare", 1, None, 7200000), (3, "Zainab Sule", 1, None, 7200000),
             (4, "Ifeanyi Obi", 2, 1, 4800000), (5, "Bisi Lawal", 2, 2, 4600000), (6, "Kelechi Amadi", 3, 3, 4800000),
             (7, "Hauwa Musa", 3, 4, 4100000), (8, "Femi Ojo", 4, 1, 2500000), (9, "Joy Etim", 4, 1, 2300000),
             (10, "Sani Garba", 5, 2, 2300000), (11, "Ruth Okon", 6, 3, 2600000), (12, "Ahmed Bala", 7, 4, 2100000),
             (13, "Grace Uche", 8, 1, 1500000)]
out += [f"INSERT INTO employee VALUES ({e[0]}, {q(e[1])}, {e[2] if e[2] else 'NULL'}, {e[3] if e[3] else 'NULL'}, {e[4]});" for e in employees]

Path(__file__).with_name("src").joinpath("test/resources/db/data.sql").write_text(
    "-- Generated by practice/sql/generate_data.py. Synthetic data, CC0.\n" + "\n".join(out) + "\n", encoding="utf-8")
print(f"{len(customers)} customers, {len(accounts)} accounts, {len(txns)} transactions")
