#!/usr/bin/env python3
"""
Generate troubleshooting report for a benchmark run.
"""

import sys
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from regex_bench.reporting.enhanced_generator import EnhancedReportGenerator


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 generate_troubleshooting_report.py <path_to_results_dir>")
        print("Example: python3 generate_troubleshooting_report.py results/phase1_20251220_142856")
        sys.exit(1)

    results_dir = Path(sys.argv[1])
    db_path = results_dir / "jobs.db"

    if not db_path.exists():
        print(f"❌ Database not found: {db_path}")
        sys.exit(1)

    print(f"🔍 Generating troubleshooting report for: {results_dir}")

    try:
        generator = EnhancedReportGenerator()
        report_path = generator.generate_troubleshooting_report(db_path, results_dir)

        print(f"✅ Troubleshooting report generated: {report_path}")
        print(f"\nTo view:")
        print(f"cat '{report_path}'")

    except Exception as e:
        print(f"❌ Failed to generate report: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()