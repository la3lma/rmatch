#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"

echo "Building Java Native (Naive) benchmark engine..."

mkdir -p "$BUILD_DIR"

if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found. Please install a JDK."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. Please install a JRE/JDK."
    exit 1
fi

echo "Compiling Java Native (Naive) benchmark..."
javac -d "$BUILD_DIR" "$SCRIPT_DIR/JavaNativeNaiveBenchmark.java"
echo "✓ Java Native (Naive) benchmark compiled successfully"

cat > "$BUILD_DIR/run_java_native_naive.sh" << 'EOF'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -cp "$SCRIPT_DIR" -Xmx2g -XX:+UseG1GC -server JavaNativeNaiveBenchmark "$@"
EOF

chmod +x "$BUILD_DIR/run_java_native_naive.sh"

echo "✓ Build complete!"
echo "  Executable: $BUILD_DIR/run_java_native_naive.sh"
