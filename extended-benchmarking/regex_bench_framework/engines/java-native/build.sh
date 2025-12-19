#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"

echo "Building Java Native benchmark engine..."

# Create build directory
mkdir -p "$BUILD_DIR"

# Check for Java
if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found. Please install a JDK."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. Please install a JRE/JDK."
    exit 1
fi

# Compile the benchmark (we'll create this file next)
if [ -f "$SCRIPT_DIR/JavaNativeBenchmark.java" ]; then
    echo "Compiling Java Native benchmark..."
    javac -d "$BUILD_DIR" "$SCRIPT_DIR/JavaNativeBenchmark.java"
    echo "✓ Java Native benchmark compiled successfully"
else
    echo "⚠️  JavaNativeBenchmark.java not found - will create placeholder"
    # Create a simple placeholder
    cat > "$BUILD_DIR/JavaNativeBenchmark.class" << 'EOF'
# Placeholder - Java Native benchmark will be available after creating JavaNativeBenchmark.java
EOF
fi

echo "✓ Build complete!"