#!/usr/bin/env python3
"""
Generate diagrams and charts for the donkeycar simulator setup report
"""

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np
import seaborn as sns
from pathlib import Path

# Set style for consistent appearance
plt.style.use('seaborn-v0_8-whitegrid')
sns.set_palette("husl")

def create_donkeycar_architecture():
    """Generate donkeycar architecture diagram"""
    fig, ax = plt.subplots(figsize=(12, 8))
    
    # Define components and their positions
    components = {
        'Unity Simulator': (2, 7, 3, 1),
        'Gym-Donkeycar': (6, 7, 3, 1),
        'OpenAI Gym': (10, 7, 3, 1),
        'Data Collection': (2, 5, 3, 1),
        'LeRobot Framework': (6, 5, 3, 1),
        'Model Training': (10, 5, 3, 1),
        'Policy Network': (4, 3, 3, 1),
        'HuggingFace Hub': (8, 3, 3, 1),
        'Deployed Model': (6, 1, 3, 1)
    }
    
    # Color mapping for different layers
    colors = {
        'Unity Simulator': '#FF6B6B',
        'Gym-Donkeycar': '#4ECDC4', 
        'OpenAI Gym': '#45B7D1',
        'Data Collection': '#96CEB4',
        'LeRobot Framework': '#FFEAA7',
        'Model Training': '#DDA0DD',
        'Policy Network': '#98D8C8',
        'HuggingFace Hub': '#F7DC6F',
        'Deployed Model': '#BB8FCE'
    }
    
    # Draw components
    for name, (x, y, w, h) in components.items():
        rect = patches.Rectangle((x, y), w, h, linewidth=2, 
                                edgecolor='black', facecolor=colors[name], alpha=0.7)
        ax.add_patch(rect)
        ax.text(x + w/2, y + h/2, name, ha='center', va='center', 
                fontsize=10, weight='bold', wrap=True)
    
    # Draw connections
    connections = [
        ((3.5, 7), (6, 7.5)),  # Unity -> Gym-Donkeycar
        ((7.5, 7), (10, 7.5)), # Gym-Donkeycar -> OpenAI Gym
        ((3.5, 6), (3.5, 7)),  # Data Collection -> Unity
        ((7.5, 6), (7.5, 7)),  # LeRobot -> Gym-Donkeycar
        ((11.5, 6), (11.5, 7)), # Model Training -> OpenAI Gym
        ((5.5, 4), (5.5, 5)),  # Policy Network -> LeRobot
        ((9.5, 4), (9.5, 5)),  # HuggingFace -> Model Training
        ((7.5, 2), (7.5, 3)),  # Deployed Model -> HuggingFace
    ]
    
    for (x1, y1), (x2, y2) in connections:
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle='->', lw=2, color='darkblue'))
    
    # Add labels for workflow stages
    ax.text(1, 8.5, 'Simulation Layer', fontsize=14, weight='bold', color='darkred')
    ax.text(1, 6.5, 'Data & Training Layer', fontsize=14, weight='bold', color='darkgreen')
    ax.text(1, 4.5, 'Model Layer', fontsize=14, weight='bold', color='darkblue')
    ax.text(1, 2.5, 'Deployment Layer', fontsize=14, weight='bold', color='purple')
    
    ax.set_xlim(0, 15)
    ax.set_ylim(0, 10)
    ax.set_title('Donkeycar Simulator Architecture with LeRobot Integration', 
                 fontsize=16, weight='bold', pad=20)
    ax.axis('off')
    
    plt.tight_layout()
    plt.savefig('illustrations/donkeycar_architecture.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_simulator_workflow():
    """Generate simulator workflow diagram"""
    fig, ax = plt.subplots(figsize=(14, 8))
    
    # Workflow steps
    steps = [
        'Install\nDependencies',
        'Setup Unity\nSimulator', 
        'Configure\nGym-Donkeycar',
        'Test\nConnection',
        'Collect\nDemonstrations',
        'Train\nPolicy',
        'Evaluate\nModel',
        'Deploy to\nHuggingFace'
    ]
    
    # Position steps in a flow
    positions = [(i*1.8 + 1, 4) for i in range(len(steps))]
    
    # Draw workflow boxes
    for i, (step, (x, y)) in enumerate(zip(steps, positions)):
        # Alternate colors
        color = '#E8F4FD' if i % 2 == 0 else '#FFF2E8'
        
        rect = patches.FancyBboxPatch((x-0.7, y-0.8), 1.4, 1.6,
                                      boxstyle="round,pad=0.1",
                                      facecolor=color, edgecolor='darkblue', linewidth=2)
        ax.add_patch(rect)
        ax.text(x, y, step, ha='center', va='center', fontsize=10, weight='bold')
        
        # Add arrows between steps
        if i < len(steps) - 1:
            ax.annotate('', xy=(positions[i+1][0]-0.7, y), xytext=(x+0.7, y),
                        arrowprops=dict(arrowstyle='->', lw=2, color='darkblue'))
    
    # Add time estimates below each step
    time_estimates = ['30min', '20min', '15min', '10min', '2hrs', '4hrs', '1hr', '30min']
    for (x, y), time_est in zip(positions, time_estimates):
        ax.text(x, y-1.5, f'~{time_est}', ha='center', va='center', 
                fontsize=9, style='italic', color='gray')
    
    # Add complexity indicators
    complexity = ['Easy', 'Easy', 'Medium', 'Easy', 'Medium', 'Hard', 'Medium', 'Easy']
    colors_complex = {'Easy': 'green', 'Medium': 'orange', 'Hard': 'red'}
    
    for (x, y), comp in zip(positions, complexity):
        ax.text(x, y+1.2, comp, ha='center', va='center', 
                fontsize=8, weight='bold', color=colors_complex[comp])
    
    ax.set_xlim(-0.5, len(steps)*1.8 + 0.5)
    ax.set_ylim(1, 7)
    ax.set_title('Donkeycar Simulator Setup Workflow', fontsize=16, weight='bold', pad=20)
    ax.text(len(steps)*0.9, 6, 'Estimated Total Setup Time: ~8.5 hours', 
            ha='center', fontsize=12, weight='bold', 
            bbox=dict(boxstyle="round,pad=0.3", facecolor='lightblue'))
    ax.axis('off')
    
    plt.tight_layout()
    plt.savefig('illustrations/simulator_workflow.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_lerobot_integration():
    """Generate LeRobot integration pathway diagram"""
    fig, ax = plt.subplots(figsize=(12, 10))
    
    # Integration components
    components = {
        # Input layer
        'Human\nDemonstrations': (1, 8, 2.5, 1),
        'Simulator\nData': (4.5, 8, 2.5, 1),
        'Real Robot\nData': (8, 8, 2.5, 1),
        
        # Processing layer
        'Data\nCollection': (2, 6, 2.5, 1),
        'Data\nAugmentation': (5.5, 6, 2.5, 1),
        
        # LeRobot core
        'LeRobot\nDataset API': (1, 4, 3, 1),
        'Policy\nArchitectures': (4.5, 4, 3, 1),
        'Training\nPipeline': (8, 4, 2.5, 1),
        
        # Output layer
        'Trained\nModel': (2, 2, 2.5, 1),
        'HuggingFace\nHub': (5.5, 2, 2.5, 1),
        'Deployment': (8.5, 2, 2, 1),
    }
    
    # Color scheme for different layers
    layer_colors = {
        8: '#FFE6E6',  # Input layer - light red
        6: '#E6F3FF',  # Processing layer - light blue  
        4: '#E6FFE6',  # LeRobot core - light green
        2: '#FFF0E6'   # Output layer - light orange
    }
    
    # Draw components
    for name, (x, y, w, h) in components.items():
        color = layer_colors.get(y, '#F0F0F0')
        rect = patches.FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.1",
                                      facecolor=color, edgecolor='black', linewidth=1.5)
        ax.add_patch(rect)
        ax.text(x + w/2, y + h/2, name, ha='center', va='center', 
                fontsize=10, weight='bold')
    
    # Draw connections
    connections = [
        # Input to processing
        ((2.25, 8), (3.25, 7)),   # Human demos -> Data collection
        ((5.75, 8), (6.75, 7)),   # Simulator -> Data augmentation
        ((9.25, 8), (6.75, 7)),   # Real robot -> Data augmentation
        
        # Processing to LeRobot
        ((3.25, 6), (2.5, 5)),    # Data collection -> Dataset API
        ((6.75, 6), (6, 5)),      # Data augmentation -> Policy architectures
        
        # Within LeRobot layer
        ((4, 4.5), (4.5, 4.5)),   # Dataset API -> Policy architectures
        ((7.5, 4.5), (8, 4.5)),   # Policy architectures -> Training pipeline
        
        # LeRobot to output
        ((2.5, 4), (3.25, 3)),    # Dataset API -> Trained model
        ((6, 4), (6.75, 3)),      # Policy architectures -> HuggingFace Hub
        ((9.25, 4), (9.5, 3)),    # Training pipeline -> Deployment
        
        # Output layer connections
        ((4.5, 2.5), (5.5, 2.5)), # Trained model -> HuggingFace Hub
        ((8, 2.5), (8.5, 2.5)),   # HuggingFace Hub -> Deployment
    ]
    
    for (x1, y1), (x2, y2) in connections:
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle='->', lw=1.5, color='darkblue'))
    
    # Add layer labels
    ax.text(0.2, 8.5, 'Data Sources', fontsize=12, weight='bold', rotation=90, va='center')
    ax.text(0.2, 6.5, 'Processing', fontsize=12, weight='bold', rotation=90, va='center')
    ax.text(0.2, 4.5, 'LeRobot Core', fontsize=12, weight='bold', rotation=90, va='center')
    ax.text(0.2, 2.5, 'Output', fontsize=12, weight='bold', rotation=90, va='center')
    
    # Add LeRobot features callout
    features_text = """LeRobot Features:
• Pre-trained Models
• Imitation Learning
• Multi-modal Support
• Dataset Management
• Model Hub Integration"""
    
    ax.text(11.5, 5.5, features_text, fontsize=9, 
            bbox=dict(boxstyle="round,pad=0.5", facecolor='lightyellow', alpha=0.8),
            verticalalignment='top')
    
    ax.set_xlim(0, 13)
    ax.set_ylim(1, 10)
    ax.set_title('LeRobot Integration Architecture for Donkeycar', 
                 fontsize=16, weight='bold', pad=20)
    ax.axis('off')
    
    plt.tight_layout()
    plt.savefig('illustrations/lerobot_integration.png', dpi=300, bbox_inches='tight')
    plt.close()

def create_mac_setup_flow():
    """Generate M2 Mac specific setup flow"""
    fig, ax = plt.subplots(figsize=(10, 12))
    
    # Setup phases with Mac-specific considerations
    phases = [
        {
            'title': 'System Preparation',
            'steps': ['Install Xcode CLI Tools', 'Install Homebrew', 'Update macOS'],
            'notes': 'Ensure Apple Silicon compatibility'
        },
        {
            'title': 'Python Environment',
            'steps': ['Install Python 3.11', 'Create virtual environment', 'Install base packages'],
            'notes': 'Use ARM64 native packages'
        },
        {
            'title': 'Graphics & ML Libraries',
            'steps': ['Install OpenCV (ARM64)', 'Install PyTorch (Metal)', 'Install Unity dependencies'],
            'notes': 'Leverage Metal Performance Shaders'
        },
        {
            'title': 'Simulator Setup',
            'steps': ['Download Unity Simulator', 'Configure permissions', 'Test connection'],
            'notes': 'May require Rosetta for Unity'
        },
        {
            'title': 'LeRobot Integration',
            'steps': ['Install LeRobot', 'Setup HuggingFace', 'Configure training'],
            'notes': 'Optimize for 96GB unified memory'
        },
        {
            'title': 'Verification',
            'steps': ['Run test scripts', 'Verify GPU acceleration', 'Performance benchmarks'],
            'notes': 'Confirm Metal acceleration works'
        }
    ]
    
    y_start = 11
    phase_height = 1.8
    
    for i, phase in enumerate(phases):
        y = y_start - i * phase_height
        
        # Phase box
        rect = patches.FancyBboxPatch((1, y-0.8), 8, 1.5,
                                      boxstyle="round,pad=0.1",
                                      facecolor='#E8F4FD', 
                                      edgecolor='darkblue', linewidth=2)
        ax.add_patch(rect)
        
        # Phase title
        ax.text(1.5, y, phase['title'], fontsize=12, weight='bold', va='center')
        
        # Steps
        steps_text = ' → '.join(phase['steps'])
        ax.text(1.5, y-0.4, steps_text, fontsize=9, va='center', wrap=True)
        
        # Mac-specific notes
        ax.text(10, y, phase['notes'], fontsize=8, style='italic', 
                va='center', ha='left', color='darkred',
                bbox=dict(boxstyle="round,pad=0.2", facecolor='#FFF0F0'))
        
        # Connection arrow
        if i < len(phases) - 1:
            ax.annotate('', xy=(5, y-1.2), xytext=(5, y-0.8),
                        arrowprops=dict(arrowstyle='->', lw=2, color='darkblue'))
    
    # Add M2 Mac advantages callout
    advantages_text = """M2 Mac Studio Advantages:
• 96GB Unified Memory
• Metal Performance Shaders
• ARM64 Native Performance
• Excellent ML Framework Support
• Energy Efficient Training"""
    
    ax.text(0.5, 2.5, advantages_text, fontsize=10, 
            bbox=dict(boxstyle="round,pad=0.5", facecolor='lightgreen', alpha=0.8),
            verticalalignment='top')
    
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 12)
    ax.set_title('M2 Mac Studio Setup Flow for Donkeycar Simulator', 
                 fontsize=14, weight='bold', pad=20)
    ax.axis('off')
    
    plt.tight_layout()
    plt.savefig('illustrations/mac_setup_flow.png', dpi=300, bbox_inches='tight')
    plt.close()

def main():
    """Generate all diagrams"""
    print("Generating donkeycar simulator setup diagrams...")
    
    # Ensure illustrations directory exists
    Path('illustrations').mkdir(exist_ok=True)
    
    # Generate all diagrams
    create_donkeycar_architecture()
    print("✓ Generated donkeycar_architecture.png")
    
    create_simulator_workflow()
    print("✓ Generated simulator_workflow.png")
    
    create_lerobot_integration()
    print("✓ Generated lerobot_integration.png")
    
    create_mac_setup_flow()
    print("✓ Generated mac_setup_flow.png")
    
    print("All diagrams generated successfully!")

if __name__ == "__main__":
    main()