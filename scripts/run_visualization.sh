#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Clear all Python-related environment variables
unset PYTHONPATH
unset PYTHONHOME
unset VIRTUAL_ENV
unset CONDA_DEFAULT_ENV
unset CONDA_PREFIX
unset PIP_USER

# Activate virtual environment using relative path
source "$SCRIPT_DIR/.venv/bin/activate"

# Run the visualization script with absolute paths
"$SCRIPT_DIR/.venv/bin/python" \
    "$SCRIPT_DIR/visualize_benchmarks.py" \
    --results-dir "$PROJECT_DIR/benchmarks/results" \
    --output-dir "$PROJECT_DIR/performance-graphs"
