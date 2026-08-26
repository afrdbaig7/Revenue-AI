#!/usr/bin/env python3
"""RecoverAI — standalone deterministic synthetic dataset generator.

Generates N payment/revenue incidents (default 10,000) with the same seeded
distributions as the Java experiment engine: amounts (lognormal-ish price points,
paise integers), failure cohorts, latent recoverability, and unrecoverable noise.

Output: CSV + JSON to datasets/fixtures (or a custom --out path).

    python3 datasets/generator/generate.py --count 10000 --seed 42

Same seed ⇒ identical output. All data is synthetic; label every artifact as
SIMULATED / SYNTHETIC TEST-MODE.
"""
from __future__ import annotations

import argparse
import csv
import json
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

CATEGORIES = [
    "INSUFFICIENT_FUNDS", "CARD_EXPIRED", "CARD_BLOCKED", "BANK_DECLINE", "NETWORK_TIMEOUT",
    "MANDATE_FAILURE", "CHECKOUT_ABANDONED", "AUTHENTICATION_FAILURE", "PROCESSOR_ERROR", "UNKNOWN",
]
PRICE_POINTS = [49900, 99900, 149900, 199900, 249900, 349900, 499900, 799900, 999900,
                1249900, 1499900, 1999900, 2499900, 3499900, 4999900, 7999900, 9999900,
                12499900, 14999900, 19999900, 24999900, 34999900, 49999900]
METHODS = ["card", "upi", "netbanking", "wallet", "card", "card"]
SEGMENTS = ["STANDARD", "STANDARD", "PREMIUM", "VIP"]


def generate(seed: int, count: int) -> list[dict]:
    rng = random.Random(seed)
    now = datetime.now(timezone.utc)
    incidents = []
    for i in range(count):
        amount = PRICE_POINTS[rng.randrange(len(PRICE_POINTS))]
        amount = max(49900, min(49999900, int(amount * (1 + rng.gauss(0, 0.12)))))
        amount = (amount // 100) * 100  # whole rupees, still paise int
        category = CATEGORIES[rng.randrange(len(CATEGORIES))]
        latent = round(0.15 + rng.random() * 0.55, 4)
        unrecoverable = rng.random() < 0.14
        detected_at = now - timedelta(hours=rng.randrange(1, 24 * 14))
        incidents.append({
            "incident_key": f"inc-{i}",
            "customer_id": f"cust_{1000 + rng.randrange(500)}",
            "merchant_id": "mch_demo",
            "amount_minor": amount,
            "currency": "INR",
            "payment_method": METHODS[rng.randrange(len(METHODS))],
            "customer_segment": SEGMENTS[rng.randrange(len(SEGMENTS))],
            "failure_category": category,
            "previous_successes": rng.randrange(0, 40),
            "previous_failures": rng.randrange(0, 6),
            "contact_history": rng.randrange(0, 4),
            "latent_recoverability": latent,
            "time_to_funds_hours": round(6 + rng.random() * 66, 2),
            "unrecoverable": unrecoverable,
            "detected_at": detected_at.isoformat(),
        })
    return incidents


def main() -> None:
    parser = argparse.ArgumentParser(description="RecoverAI synthetic dataset generator")
    parser.add_argument("--count", type=int, default=10_000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=Path(__file__).parent.parent / "fixtures")
    args = parser.parse_args()

    incidents = generate(args.seed, args.count)
    args.out.mkdir(parents=True, exist_ok=True)

    csv_path = args.out / f"incidents_{args.count}_seed{args.seed}.csv"
    with csv_path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(incidents[0].keys()))
        writer.writeheader()
        writer.writerows(incidents)

    json_path = args.out / f"incidents_{args.count}_seed{args.seed}.json"
    with json_path.open("w") as f:
        json.dump({"label": "SIMULATED / SYNTHETIC TEST-MODE", "seed": args.seed, "count": args.count, "incidents": incidents}, f, indent=2)

    cohorts = {c: sum(1 for i in incidents if i["failure_category"] == c) for c in CATEGORIES}
    print(f"Generated {args.count} incidents (seed {args.seed})")
    print(f"  CSV : {csv_path}")
    print(f"  JSON: {json_path}")
    print(f"  Cohorts: {cohorts}")
    print(f"  Unrecoverable: {sum(1 for i in incidents if i['unrecoverable'])} ({sum(1 for i in incidents if i['unrecoverable']) / args.count:.0%})")


if __name__ == "__main__":
    main()
