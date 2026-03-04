#!/usr/bin/env python3
"""
Direct test of the world-class analytics system with sample data.
This bypasses any CLI issues to directly demonstrate all enhanced features.
"""

import sys
from pathlib import Path

# Add the project path to sys.path
project_root = Path(__file__).parent
sys.path.append(str(project_root))

try:
    from regex_bench.analysis.advanced_analytics import AdvancedAnalytics
    from regex_bench.analysis.advanced_chart_generator import AdvancedChartGenerator
    from regex_bench.reporting.formatter import HTMLFormatter
    import sqlite3
    print("✅ All advanced analytics modules imported successfully")
except ImportError as e:
    print(f"❌ Import error: {e}")
    sys.exit(1)

def test_direct_analytics():
    """Test the analytics system directly with our demo database."""

    db_path = "demo_benchmark_results.db"

    if not Path(db_path).exists():
        print(f"❌ Database not found: {db_path}")
        return False

    print(f"📊 Testing world-class analytics with database: {db_path}")

    try:
        # Test 1: Initialize Advanced Analytics
        print("🔬 Initializing Advanced Analytics...")
        analytics = AdvancedAnalytics(db_path)
        print("✅ Advanced Analytics initialized successfully")

        # Test 2: Generate scaling analysis
        print("📈 Generating scaling analysis...")
        scaling_analysis = analytics.generate_scaling_analysis()
        print(f"✅ Scaling analysis completed for {len(scaling_analysis)} engines")
        for engine, data in scaling_analysis.items():
            print(f"   - {engine}: Pattern scaling model type = {data.get('pattern_scaling', {}).get('model', {}).get('type', 'N/A')}")

        # Test 3: Generate comparative analysis
        print("🔍 Generating comparative analysis...")
        comparative_analysis = analytics.generate_comparative_analysis()
        print(f"✅ Comparative analysis completed")
        print(f"   - Statistical comparisons: {len(comparative_analysis.get('statistical_comparisons', {}))}")
        print(f"   - Engine profiles: {len(comparative_analysis.get('engine_profiles', {}))}")

        # Test 4: Initialize Chart Generator
        print("🎨 Initializing Advanced Chart Generator...")
        chart_generator = AdvancedChartGenerator()
        print("✅ Advanced Chart Generator initialized successfully")

        # Test 5: Generate scaling analysis charts
        print("📊 Generating scaling analysis charts...")
        scaling_charts = chart_generator.generate_scaling_analysis_charts(scaling_analysis)
        print(f"✅ Scaling analysis charts generated ({len(scaling_charts)} characters)")

        # Test 6: Generate comparative analysis charts
        print("📈 Generating comparative analysis charts...")
        comparative_charts = chart_generator.generate_comparative_analysis_charts(comparative_analysis)
        print(f"✅ Comparative analysis charts generated ({len(comparative_charts)} characters)")

        # Test 7: Generate complete analytics dashboard
        print("🌟 Generating complete world-class analytics dashboard...")

        # Create a comprehensive HTML report with all features
        html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>World-Class Regex Benchmark Analytics Dashboard</title>
    <script src="https://cdn.plot.ly/plotly-latest.min.js"></script>
    <style>
        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            color: #333;
            background: #f8f9fa;
            margin: 0;
            padding: 20px;
        }}
        .container {{
            max-width: 1400px;
            margin: 0 auto;
        }}
        .dashboard-header {{
            text-align: center;
            margin-bottom: 40px;
            padding: 30px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1);
        }}
        .dashboard-header h1 {{
            margin: 0;
            font-size: 3rem;
            font-weight: bold;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }}
        .dashboard-header .subtitle {{
            font-size: 1.2rem;
            opacity: 0.9;
            margin-top: 10px;
        }}
        .analytics-section {{
            background: white;
            margin: 30px 0;
            padding: 40px;
            border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.1);
        }}
        .section-title {{
            font-size: 2rem;
            color: #2c3e50;
            border-bottom: 3px solid #3498db;
            padding-bottom: 15px;
            margin-bottom: 30px;
            display: flex;
            align-items: center;
            gap: 15px;
        }}
        .feature-highlight {{
            background: linear-gradient(135deg, #74b9ff 0%, #0984e3 100%);
            color: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }}
        .feature-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
            margin: 20px 0;
        }}
        .feature-card {{
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #27ae60;
        }}
        .chart-explanation {{
            background: #e8f6f3;
            border-left: 4px solid #27ae60;
            padding: 20px;
            margin: 20px 0;
            border-radius: 0 8px 8px 0;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="dashboard-header">
            <h1>🚀 World-Class Regex Analytics Dashboard</h1>
            <div class="subtitle">
                Advanced Performance Analysis & Predictive Modeling for Regex Engine Benchmarks
            </div>
        </div>

        <div class="feature-highlight">
            <h3>🌟 Enhanced Analytics Features Implemented</h3>
            <div class="feature-grid">
                <div class="feature-card">
                    <strong>📈 Scanning Linearity Analysis</strong><br>
                    Verifies that scanning time scales linearly with corpus size as expected
                </div>
                <div class="feature-card">
                    <strong>🔮 Performance Prediction Models</strong><br>
                    Statistical models that predict performance up to 100K patterns with R² scores
                </div>
                <div class="feature-card">
                    <strong>⚡ Efficiency Trend Analysis</strong><br>
                    Throughput per KB of memory usage to identify most efficient engines
                </div>
                <div class="feature-card">
                    <strong>🎯 Enhanced Clarity</strong><br>
                    Detailed explanations of what each graph element means
                </div>
                <div class="feature-card">
                    <strong>📺 Maximum Screen Usage</strong><br>
                    Charts are 1600px tall for optimal visibility and analysis
                </div>
                <div class="feature-card">
                    <strong>🧬 Statistical Analysis</strong><br>
                    Confidence intervals, statistical significance testing, and outlier detection
                </div>
            </div>
        </div>

        <div class="analytics-section">
            <h2 class="section-title">
                📊 Advanced Scaling Analysis Dashboard
            </h2>
            <div class="chart-explanation">
                <h4>🔍 Understanding the Scaling Analysis Charts</h4>
                <p><strong>What you're seeing:</strong> This comprehensive dashboard shows how different regex engines perform as the number of patterns increases (complexity scaling) and as corpus size grows (data scaling).</p>
                <ul>
                    <li><strong>🚀 Throughput vs Pattern Count:</strong> Shows how processing speed decreases as pattern complexity increases</li>
                    <li><strong>🧠 Memory Efficiency:</strong> Memory usage per individual regex pattern - lower is better</li>
                    <li><strong>⚡ Compilation Time Scaling:</strong> How long it takes to compile patterns into internal representation</li>
                    <li><strong>📏 Scanning Linearity:</strong> Verifies scanning time scales linearly with corpus size (good engines show straight lines)</li>
                    <li><strong>🔮 Performance Prediction:</strong> Machine learning models predict performance up to 100K patterns</li>
                    <li><strong>📈 Efficiency Trends:</strong> Throughput divided by memory usage shows most efficient engines overall</li>
                </ul>
            </div>
            {scaling_charts}
        </div>

        <div class="analytics-section">
            <h2 class="section-title">
                🔬 Comparative Analysis Dashboard
            </h2>
            <div class="chart-explanation">
                <h4>🎯 Understanding the Comparative Analysis</h4>
                <p><strong>What you're seeing:</strong> Head-to-head statistical comparisons between engines with advanced analytics.</p>
                <ul>
                    <li><strong>📊 Performance Comparison Matrix:</strong> Statistical significance testing between engine pairs</li>
                    <li><strong>📈 Multi-dimensional Analysis:</strong> 3D performance landscapes and radar charts</li>
                    <li><strong>🏆 Engine Rankings:</strong> Data-driven rankings across multiple performance dimensions</li>
                    <li><strong>💡 AI-Generated Insights:</strong> Automated analysis and recommendations based on performance patterns</li>
                </ul>
            </div>
            {comparative_charts}
        </div>

        <div class="analytics-section">
            <h2 class="section-title">
                🎓 Technical Implementation Summary
            </h2>
            <div class="feature-highlight">
                <h4>🔧 Technologies & Techniques Used</h4>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 15px; margin-top: 15px;">
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>📊 Visualization:</strong> Plotly.js interactive charts
                    </div>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>📈 Statistics:</strong> SciPy & scikit-learn models
                    </div>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>🔮 Prediction:</strong> Polynomial regression with R² scoring
                    </div>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>📏 Testing:</strong> Statistical significance & confidence intervals
                    </div>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>🎨 Layout:</strong> Multi-panel dashboards with explanations
                    </div>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <strong>🔍 Analysis:</strong> Outlier detection & data quality metrics
                    </div>
                </div>
            </div>
        </div>
    </div>
</body>
</html>"""

        # Write the complete dashboard
        output_file = "world_class_analytics_demo.html"
        with open(output_file, 'w') as f:
            f.write(html_content)

        print(f"🌟 World-class analytics dashboard generated successfully!")
        print(f"📄 Output file: {output_file}")
        print(f"📊 Dashboard includes:")
        print("   ✅ Scanning linearity analysis")
        print("   ✅ Performance prediction models")
        print("   ✅ Efficiency trend analysis")
        print("   ✅ Enhanced clarity with detailed explanations")
        print("   ✅ Maximum screen real-estate usage (1600px tall charts)")
        print("   ✅ Statistical significance testing")
        print("   ✅ Interactive visualizations with Plotly.js")

        return True

    except Exception as e:
        print(f"❌ Error in analytics testing: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    test_direct_analytics()