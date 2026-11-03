#!/usr/bin/env python3
"""Generate a line-coverage SVG badge from JaCoCo's aggregate XML report."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    # Only the report-level counter: package/class counters would double-count lines.
    counter = ET.parse(args.report).getroot().find("counter[@type='LINE']")
    if counter is None:
        parser.error("JaCoCo report has no LINE counter")
    covered = int(counter.attrib["covered"])
    total = covered + int(counter.attrib["missed"])
    if total == 0:
        parser.error("JaCoCo report contains no executable lines")
    percent = 100 * covered / total
    value = f"{percent:.1f}%"
    color = "#4c1" if percent >= 80 else "#dfb317" if percent >= 60 else "#e05d44"
    label = f"coverage: {value}"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        f'''<svg xmlns="http://www.w3.org/2000/svg" width="132" height="20" role="img" aria-label="{label}">
  <title>{label}</title>
  <linearGradient id="s" x2="0" y2="100%">
    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
    <stop offset="1" stop-opacity=".1"/>
  </linearGradient>
  <clipPath id="r"><rect width="132" height="20" rx="3"/></clipPath>
  <g clip-path="url(#r)">
    <path fill="#555" d="M0 0h65v20H0z"/>
    <path fill="{color}" d="M65 0h67v20H65z"/>
    <path fill="url(#s)" d="M0 0h132v20H0z"/>
  </g>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
    <g fill="#010101" fill-opacity=".3">
      <text x="33" y="15">coverage</text><text x="98" y="15">{value}</text>
    </g>
    <text x="33" y="14">coverage</text><text x="98" y="14">{value}</text>
  </g>
</svg>
''',
        encoding="utf-8",
    )
    print(f"Line coverage: {value} ({covered}/{total}); badge: {args.output}")


if __name__ == "__main__":
    main()
