#!/usr/bin/env python3
"""Print a Kover coverage table from build/reports/kover/report.xml.

Rows are packages by default, or modules with --by-module. Each row shows the line and branch
coverage that the koverVerify floors are expressed in, sorted weakest branch coverage first.
Packages span modules (com.pambrose.common.dsl lives in seven), so --by-module maps every source
file in the report back to the module whose main source set contains it.
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

REPORT = Path("build/reports/kover/report.xml")
COUNTERS = ("LINE", "BRANCH")


def counters(element):
    """Return {type: (covered, missed)} for the counters directly under element."""
    found = {c.get("type"): (int(c.get("covered")), int(c.get("missed"))) for c in element.findall("counter")}
    return {t: found.get(t, (0, 0)) for t in COUNTERS}


def module_index():
    """Map 'com/pambrose/.../File.kt' to the module whose main source set contains it."""
    index = defaultdict(set)
    for path in Path(".").glob("*/src/*/*/com/**/*"):
        module, _, source_set = path.parts[:3]
        if path.is_file() and (source_set == "main" or source_set.endswith("Main")):
            index["/".join(path.parts[4:])].add(module)
    return index


def rows_by_package(root):
    return [(p.get("name").replace("/", "."), counters(p)) for p in root.findall("package")]


def rows_by_module(root):
    index = module_index()
    totals = defaultdict(lambda: {t: [0, 0] for t in COUNTERS})
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            modules = index.get(f"{package.get('name')}/{source.get('name')}", {"(unknown)"})
            module = "/".join(sorted(modules))
            for t, (covered, missed) in counters(source).items():
                totals[module][t][0] += covered
                totals[module][t][1] += missed
    return [(module, {t: tuple(v) for t, v in c.items()}) for module, c in totals.items()]


def pct(covered, missed):
    total = covered + missed
    return covered / total * 100 if total else None


def fmt_pct(value):
    return f"{value:6.1f}" if value is not None else f"{'-':>6}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--by-module", action="store_true", help="group rows by module instead of package")
    args = parser.parse_args()

    if not REPORT.exists():
        print(f"error: {REPORT} not found — run 'make coverage-xml' first", file=sys.stderr)
        return 1

    root = ET.parse(REPORT).getroot()
    rows = rows_by_module(root) if args.by_module else rows_by_package(root)
    # Weakest branch coverage first; rows without branches go last.
    rows.sort(key=lambda r: (pct(*r[1]["BRANCH"]) is None, pct(*r[1]["BRANCH"]) or 0, pct(*r[1]["LINE"]) or 0))

    label = "module" if args.by_module else "package"
    width = max([len(label)] + [len(name) for name, _ in rows])
    print(f"{label:<{width}}  {'line%':>6} {'missed':>7} {'lines':>6}  {'branch%':>7} {'missed':>7} {'branches':>8}")
    for name, c in rows:
        (lc, lm), (bc, bm) = c["LINE"], c["BRANCH"]
        print(
            f"{name:<{width}}  {fmt_pct(pct(lc, lm))} {lm:7d} {lc + lm:6d}  "
            f" {fmt_pct(pct(bc, bm))} {bm:7d} {bc + bm:8d}"
        )

    overall = counters(root)
    (lc, lm), (bc, bm) = overall["LINE"], overall["BRANCH"]
    print(
        f"\nOVERALL: line {pct(lc, lm):.1f}% ({lm} of {lc + lm} missed), "
        f"branch {pct(bc, bm):.1f}% ({bm} of {bc + bm} missed)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
