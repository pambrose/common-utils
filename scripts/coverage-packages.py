#!/usr/bin/env python3
"""Print a per-package Kover coverage table from build/reports/kover/report.xml."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPORT = Path("build/reports/kover/report.xml")


def main() -> int:
    if not REPORT.exists():
        print(f"error: {REPORT} not found — run 'make coverage-xml' first", file=sys.stderr)
        return 1

    root = ET.parse(REPORT).getroot()
    pkgs = [
        (p.get("name"), int(c.get("covered")), int(c.get("missed")))
        for p in root.findall("package")
        for c in p.findall("counter")
        if c.get("type") == "INSTRUCTION"
    ]
    pkgs.sort(key=lambda x: -x[2])

    print(f"{'package':<55} {'cov%':>6} {'covered':>9} {'missed':>9} {'total':>9}")
    for name, covered, missed in pkgs:
        total = covered + missed
        pct = (covered / total * 100) if total else 0
        print(f"{name:<55} {pct:6.1f} {covered:9d} {missed:9d} {total:9d}")

    total_covered = sum(p[1] for p in pkgs)
    total_missed = sum(p[2] for p in pkgs)
    grand_total = total_covered + total_missed
    overall_pct = (total_covered / grand_total * 100) if grand_total else 0
    print(f"\nOVERALL: {overall_pct:.2f}% ({total_covered}/{grand_total} instructions, {total_missed} missed)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
