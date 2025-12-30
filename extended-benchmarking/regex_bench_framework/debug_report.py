#!/usr/bin/env python3
"""Debug script to test enhanced report generation directly."""

import sys
from pathlib import Path
from regex_bench.reporting.enhanced_generator import EnhancedReportGenerator

def debug_enhanced_generator():
    print("🔍 Debugging Enhanced Report Generator...")

    # Paths
    db_path = Path("results/production_20251221_115840/jobs.db")
    output_dir = Path("reports/debug_direct")
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"📁 Database: {db_path}")
    print(f"📁 Output: {output_dir}")
    print(f"✅ DB exists: {db_path.exists()}")

    try:
        # Create enhanced generator
        generator = EnhancedReportGenerator()
        print("✅ Generator created")

        # Load run metadata
        print("\n🔍 Loading run metadata...")
        metadata = generator._load_run_metadata(db_path)
        print(f"✅ Run ID: {metadata.run_id}")
        print(f"✅ Status: {metadata.status}")
        print(f"✅ Total jobs: {metadata.total_jobs}")
        print(f"✅ Completed: {metadata.completed_jobs}")

        # Load benchmark results from database
        print("\n🔍 Loading benchmark results...")
        benchmark_data = generator._load_benchmark_results_from_db(db_path, metadata.run_id)
        print(f"✅ Results count: {len(benchmark_data.get('results', []))}")
        print(f"✅ Engines found: {list(benchmark_data.get('engines', {}).keys())}")

        # Print first few results for debugging
        results = benchmark_data.get('results', [])
        print(f"\n📊 First 3 results:")
        for i, result in enumerate(results[:3]):
            print(f"  {i+1}. Engine: {result.get('engine', 'MISSING')}, "
                  f"Status: {result.get('status', 'MISSING')}, "
                  f"Patterns: {result.get('pattern_count', 'MISSING')}")

        # Print engine summary
        engines = benchmark_data.get('engines', {})
        print(f"\n🔧 Engine summary:")
        for engine_name, data in engines.items():
            print(f"  {engine_name}: {data.get('total_runs', 0)} runs, "
                  f"{data.get('success_rate', 0):.1%} success")

        # Generate actual report
        print("\n📊 Generating report...")
        report_file = generator.generate_from_database(
            db_path=db_path,
            output_dir=output_dir,
            format='html',
            include_charts=False
        )
        print(f"✅ Report generated: {report_file}")

    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    debug_enhanced_generator()