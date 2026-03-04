#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"

echo "Building Java Native (Unfair) benchmark engine..."

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

# Compile the benchmark
echo "Compiling Java Native (Unfair) benchmark..."
javac -d "$BUILD_DIR" "$SCRIPT_DIR/JavaNativeUnfairBenchmark.java"
echo "✓ Java Native (Unfair) benchmark compiled successfully"

# Create runner script
cat > "$BUILD_DIR/run_java_native_unfair.sh" << 'EOF'
#!/bin/bash
# Generated Java Native (Unfair) benchmark runner
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -cp "$SCRIPT_DIR" JavaNativeUnfairBenchmark "$@"
EOF

chmod +x "$BUILD_DIR/run_java_native_unfair.sh"

echo "✓ Build complete!"
echo "  Executable: $BUILD_DIR/run_java_native_unfair.sh"
echo ""
echo "Test with:"
echo "  $BUILD_DIR/run_java_native_unfair.sh patterns.txt corpus.txt"