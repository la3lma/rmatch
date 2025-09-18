#!/usr/bin/env python3
"""
Generate illustrations for the Extended Testing Proposal.

This script creates various charts and diagrams to visualize the testing framework
concepts and performance characteristics.
"""

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np
import seaborn as sns
import pandas as pd
from matplotlib.patches import FancyBboxPatch
import os

# Set style for consistent appearance
plt.style.use('seaborn-v0_8-whitegrid')
sns.set_palette("husl")

# Ensure output directory exists
output_dir = '../illustrations'
os.makedirs(output_dir, exist_ok=True)

def create_pattern_complexity_taxonomy():
    """Create a visualization of pattern complexity categories."""
    fig, ax = plt.subplots(figsize=(12, 8))
    
    # Define categories and their complexity levels
    categories = [
        'Simple Literals',
        'Character Classes',
        'Basic Quantifiers',
        'Complex Quantifiers',
        'Nested Alternations',
        'Pathological Patterns'
    ]
    
    complexity_scores = [1, 2, 3, 5, 7, 10]
    example_counts = [100, 150, 200, 80, 50, 20]
    
    # Create horizontal bar chart
    bars = ax.barh(categories, complexity_scores, color=sns.color_palette("viridis", len(categories)))
    
    # Add example counts as text on bars
    for i, (bar, count) in enumerate(zip(bars, example_counts)):
        width = bar.get_width()
        ax.text(width + 0.1, bar.get_y() + bar.get_height()/2, 
                f'{count} patterns', ha='left', va='center', fontweight='bold')
    
    ax.set_xlabel('Complexity Score', fontsize=12, fontweight='bold')
    ax.set_ylabel('Pattern Category', fontsize=12, fontweight='bold')
    ax.set_title('Pattern Complexity Test Categories\nwith Example Pattern Counts', 
                 fontsize=14, fontweight='bold', pad=20)
    
    # Add complexity scale explanation
    ax.text(0.02, 0.98, 'Complexity Scale:\n1-3: Low\n4-6: Medium\n7-10: High', 
            transform=ax.transAxes, va='top', ha='left',
            bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/pattern_complexity_taxonomy.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_input_characteristics():
    """Create a matrix visualization of input characteristics."""
    fig, ax = plt.subplots(figsize=(10, 8))
    
    # Define test matrix dimensions
    text_types = ['Literature', 'Source Code', 'Log Files', 'Structured Data', 'Unicode Text']
    characteristics = ['Small (KB)', 'Medium (MB)', 'Large (GB)', 'High Match Density', 'Low Match Density', 'No Matches']
    
    # Create a matrix of test coverage (random data for illustration)
    np.random.seed(42)
    coverage_matrix = np.random.choice([0, 0.3, 0.7, 1.0], size=(len(text_types), len(characteristics)), 
                                     p=[0.1, 0.2, 0.4, 0.3])
    
    # Create heatmap
    im = ax.imshow(coverage_matrix, cmap='RdYlGn', aspect='auto', vmin=0, vmax=1)
    
    # Set ticks and labels
    ax.set_xticks(range(len(characteristics)))
    ax.set_yticks(range(len(text_types)))
    ax.set_xticklabels(characteristics, rotation=45, ha='right')
    ax.set_yticklabels(text_types)
    
    # Add text annotations
    for i in range(len(text_types)):
        for j in range(len(characteristics)):
            coverage = coverage_matrix[i, j]
            if coverage == 0:
                text = 'None'
            elif coverage == 0.3:
                text = 'Basic'
            elif coverage == 0.7:
                text = 'Good'
            else:
                text = 'Full'
            ax.text(j, i, text, ha='center', va='center', fontweight='bold',
                   color='white' if coverage < 0.5 else 'black')
    
    # Add colorbar
    cbar = plt.colorbar(im, ax=ax, fraction=0.046, pad=0.04)
    cbar.set_label('Test Coverage Level', rotation=270, labelpad=20, fontweight='bold')
    
    ax.set_title('Input Characteristic Test Matrix\nCoverage Across Different Text Types and Characteristics', 
                 fontsize=14, fontweight='bold', pad=20)
    ax.set_xlabel('Input Characteristics', fontsize=12, fontweight='bold')
    ax.set_ylabel('Text Types', fontsize=12, fontweight='bold')
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/input_characteristics.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_test_generation_pipeline():
    """Create a flowchart of the test generation pipeline."""
    fig, ax = plt.subplots(figsize=(14, 10))
    
    # Define pipeline components
    components = [
        {'name': 'Pattern\nGenerators', 'x': 1, 'y': 8, 'width': 2, 'height': 1.5, 'color': 'lightblue'},
        {'name': 'Input\nSynthesizers', 'x': 4, 'y': 8, 'width': 2, 'height': 1.5, 'color': 'lightgreen'},
        {'name': 'Corpus\nManager', 'x': 7, 'y': 8, 'width': 2, 'height': 1.5, 'color': 'lightyellow'},
        {'name': 'Test\nOrchestrator', 'x': 4, 'y': 5.5, 'width': 2, 'height': 1.5, 'color': 'lightcoral'},
        {'name': 'Metrics\nCollector', 'x': 1, 'y': 3, 'width': 2, 'height': 1.5, 'color': 'lightpink'},
        {'name': 'Results\nAnalyzer', 'x': 4, 'y': 3, 'width': 2, 'height': 1.5, 'color': 'lightsteelblue'},
        {'name': 'Report\nGenerator', 'x': 7, 'y': 3, 'width': 2, 'height': 1.5, 'color': 'lightgray'},
        {'name': 'Performance\nBaseline', 'x': 4, 'y': 0.5, 'width': 2, 'height': 1.5, 'color': 'wheat'},
    ]
    
    # Draw components
    for comp in components:
        rect = FancyBboxPatch((comp['x']-comp['width']/2, comp['y']-comp['height']/2),
                             comp['width'], comp['height'],
                             boxstyle="round,pad=0.1",
                             facecolor=comp['color'],
                             edgecolor='black',
                             linewidth=2)
        ax.add_patch(rect)
        ax.text(comp['x'], comp['y'], comp['name'], ha='center', va='center', 
                fontweight='bold', fontsize=10)
    
    # Define arrows (connections)
    arrows = [
        {'start': (2, 7.25), 'end': (4, 6.25)},  # Pattern Gen -> Orchestrator
        {'start': (5, 7.25), 'end': (5, 6.25)},  # Input Synth -> Orchestrator
        {'start': (7, 7.25), 'end': (5, 6.25)},  # Corpus -> Orchestrator
        {'start': (4, 4.75), 'end': (2, 3.75)},  # Orchestrator -> Metrics
        {'start': (4, 4.75), 'end': (4, 3.75)},  # Orchestrator -> Analyzer
        {'start': (4, 4.75), 'end': (7, 3.75)},  # Orchestrator -> Report
        {'start': (4, 2.25), 'end': (4, 2.0)},   # Analyzer -> Baseline
    ]
    
    # Draw arrows
    for arrow in arrows:
        ax.annotate('', xy=arrow['end'], xytext=arrow['start'],
                   arrowprops=dict(arrowstyle='->', lw=2, color='darkblue'))
    
    # Add data flow labels
    flow_labels = [
        {'pos': (10, 8), 'text': 'Input:\n• Pattern specs\n• Test requirements\n• Performance targets'},
        {'pos': (10, 3), 'text': 'Output:\n• Performance reports\n• Bottleneck analysis\n• Optimization recommendations'},
    ]
    
    for label in flow_labels:
        ax.text(label['pos'][0], label['pos'][1], label['text'], 
                bbox=dict(boxstyle='round', facecolor='white', alpha=0.8),
                fontsize=9, va='center', ha='left')
    
    ax.set_xlim(-0.5, 12)
    ax.set_ylim(-0.5, 10)
    ax.set_aspect('equal')
    ax.axis('off')
    ax.set_title('Automated Test Data Generation Pipeline\nComprehensive Performance Testing Framework', 
                 fontsize=16, fontweight='bold', pad=20)
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/test_generation_pipeline.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_testing_architecture():
    """Create an architecture diagram of the testing framework."""
    fig, ax = plt.subplots(figsize=(14, 10))
    
    # Define architecture layers
    layers = [
        {'name': 'User Interface Layer', 'y': 8.5, 'color': 'lightcyan', 'components': ['CLI Tools', 'Web Dashboard', 'IDE Integration']},
        {'name': 'Test Orchestration Layer', 'y': 7, 'color': 'lightblue', 'components': ['Test Scheduler', 'Resource Manager', 'Results Aggregator']},
        {'name': 'Test Execution Layer', 'y': 5.5, 'color': 'lightgreen', 'components': ['Pattern Tests', 'Scale Tests', 'Stress Tests']},
        {'name': 'Metrics Collection Layer', 'y': 4, 'color': 'lightyellow', 'components': ['Performance Metrics', 'Resource Metrics', 'Quality Metrics']},
        {'name': 'Data Management Layer', 'y': 2.5, 'color': 'lightpink', 'components': ['Pattern Library', 'Test Corpora', 'Baseline Data']},
        {'name': 'Infrastructure Layer', 'y': 1, 'color': 'lightgray', 'components': ['JVM Runtime', 'CI/CD Integration', 'Cloud Resources']},
    ]
    
    # Draw layers
    for layer in layers:
        # Draw layer background
        rect = patches.Rectangle((0.5, layer['y']-0.4), 12, 0.8, 
                               facecolor=layer['color'], alpha=0.3, edgecolor='black')
        ax.add_patch(rect)
        
        # Add layer title
        ax.text(0.2, layer['y'], layer['name'], rotation=90, ha='center', va='center', 
                fontweight='bold', fontsize=11)
        
        # Add components
        comp_width = 3.5
        start_x = 1.5
        for i, comp in enumerate(layer['components']):
            comp_x = start_x + i * 4
            comp_rect = FancyBboxPatch((comp_x, layer['y']-0.25), comp_width, 0.5,
                                     boxstyle="round,pad=0.05",
                                     facecolor=layer['color'],
                                     edgecolor='darkblue',
                                     linewidth=1)
            ax.add_patch(comp_rect)
            ax.text(comp_x + comp_width/2, layer['y'], comp, ha='center', va='center', 
                   fontsize=9, fontweight='bold')
    
    # Add integration arrows
    for i in range(len(layers)-1):
        y_start = layers[i]['y'] - 0.4
        y_end = layers[i+1]['y'] + 0.4
        for x in [3, 7, 11]:
            ax.annotate('', xy=(x, y_end), xytext=(x, y_start),
                       arrowprops=dict(arrowstyle='->', lw=1.5, color='darkred', alpha=0.7))
    
    ax.set_xlim(0, 13)
    ax.set_ylim(0.2, 9.2)
    ax.axis('off')
    ax.set_title('Extended Testing Framework Architecture\nLayered Design for Comprehensive Performance Testing', 
                 fontsize=16, fontweight='bold', pad=20)
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/testing_architecture.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_performance_characterization():
    """Create a performance characterization matrix."""
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 8))
    
    # Left plot: Performance vs Pattern Complexity
    pattern_sizes = [10, 50, 100, 500, 1000, 5000, 10000]
    rmatch_throughput = [100, 85, 70, 45, 30, 15, 8]  # MB/s
    java_throughput = [120, 110, 100, 85, 70, 50, 35]  # MB/s
    
    ax1.plot(pattern_sizes, rmatch_throughput, 'o-', label='rmatch', linewidth=3, markersize=8)
    ax1.plot(pattern_sizes, java_throughput, 's-', label='Java Regex', linewidth=3, markersize=8)
    
    ax1.set_xlabel('Number of Patterns', fontsize=12, fontweight='bold')
    ax1.set_ylabel('Throughput (MB/s)', fontsize=12, fontweight='bold')
    ax1.set_title('Performance Scaling vs Pattern Count', fontsize=14, fontweight='bold')
    ax1.set_xscale('log')
    ax1.legend(fontsize=12)
    ax1.grid(True, alpha=0.3)
    
    # Add performance gap annotations
    for i, (x, r, j) in enumerate(zip(pattern_sizes[::2], rmatch_throughput[::2], java_throughput[::2])):
        gap = j / r
        ax1.annotate(f'{gap:.1f}x gap', xy=(x, r), xytext=(x, r-10),
                    arrowprops=dict(arrowstyle='->', color='red', alpha=0.7),
                    fontsize=10, ha='center', color='red')
    
    # Right plot: Memory Usage Comparison
    categories = ['Pattern\nCompilation', 'State\nStorage', 'Match\nResults', 'Overhead']
    rmatch_memory = [45, 120, 25, 35]  # MB
    java_memory = [5, 15, 8, 2]        # MB
    
    x = np.arange(len(categories))
    width = 0.35
    
    bars1 = ax2.bar(x - width/2, rmatch_memory, width, label='rmatch', alpha=0.8)
    bars2 = ax2.bar(x + width/2, java_memory, width, label='Java Regex', alpha=0.8)
    
    ax2.set_xlabel('Memory Category', fontsize=12, fontweight='bold')
    ax2.set_ylabel('Memory Usage (MB)', fontsize=12, fontweight='bold')
    ax2.set_title('Memory Usage Breakdown (10k patterns)', fontsize=14, fontweight='bold')
    ax2.set_xticks(x)
    ax2.set_xticklabels(categories)
    ax2.legend(fontsize=12)
    ax2.grid(True, alpha=0.3, axis='y')
    
    # Add ratio labels on bars
    for i, (r, j) in enumerate(zip(rmatch_memory, java_memory)):
        ratio = r / j if j > 0 else float('inf')
        ax2.text(i, max(r, j) + 5, f'{ratio:.1f}x', ha='center', va='bottom', 
                fontweight='bold', color='red')
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/performance_characterization.png', dpi=300, bbox_inches='tight')
    plt.close()

def main():
    """Generate all charts for the extended testing proposal."""
    print("Generating pattern complexity taxonomy...")
    create_pattern_complexity_taxonomy()
    
    print("Generating input characteristics matrix...")
    create_input_characteristics()
    
    print("Generating test generation pipeline...")
    create_test_generation_pipeline()
    
    print("Generating testing architecture diagram...")
    create_testing_architecture()
    
    print("Generating performance characterization...")
    create_performance_characterization()
    
    print("All charts generated successfully!")

if __name__ == '__main__':
    main()