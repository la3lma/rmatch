#!/usr/bin/env python3
"""Simple test script for the report generator."""

import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

# Import the reporting module directly to avoid dependency issues
from regex_bench.reporting.generator import ReportGenerator

def main():
    # Test with latest results
    results_dir = Path("results/quick_20251219_134403")
    output_dir = Path("test_reports")

    if not results_dir.exists():
        print(f"❌ Results directory not found: {results_dir}")
        return

    print(f"📊 Generating report from: {results_dir}")
    print(f"📁 Output directory: {output_dir}")

    try:
        generator = ReportGenerator()
        report_file = generator.generate(
            input_dir=results_dir,
            output_dir=output_dir,
            format="html",
            include_charts=False
        )

        print(f"✅ Report generated successfully!")
        print(f"📄 Report file: {report_file}")

        # Also generate markdown version
        md_report = generator.generate(
            input_dir=results_dir,
            output_dir=output_dir,
            format="markdown",
            include_charts=False
        )

        print(f"📄 Markdown report: {md_report}")

    except Exception as e:
        print(f"❌ Report generation failed: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()