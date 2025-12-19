#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build"
LIB_DIR="$BUILD_DIR/lib"

echo "Building RE2J benchmark engine..."

# Create build directories
mkdir -p "$BUILD_DIR" "$LIB_DIR"

# RE2J version - check for latest at https://github.com/google/re2j/releases
RE2J_VERSION="1.7"
RE2J_JAR="re2j-${RE2J_VERSION}.jar"
RE2J_URL="https://repo1.maven.org/maven2/com/google/re2j/re2j/${RE2J_VERSION}/${RE2J_JAR}"

# Download RE2J if not present
if [ ! -f "$LIB_DIR/$RE2J_JAR" ]; then
    echo "Downloading RE2J $RE2J_VERSION..."
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$LIB_DIR/$RE2J_JAR" "$RE2J_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$LIB_DIR/$RE2J_JAR" "$RE2J_URL"
    else
        echo "ERROR: Neither curl nor wget found. Please install one of them."
        echo "Or download manually:"
        echo "  URL: $RE2J_URL"
        echo "  Target: $LIB_DIR/$RE2J_JAR"
        exit 1
    fi

    if [ ! -f "$LIB_DIR/$RE2J_JAR" ]; then
        echo "ERROR: Failed to download RE2J JAR"
        exit 1
    fi

    echo "✓ Downloaded RE2J $RE2J_VERSION"
else
    echo "✓ RE2J $RE2J_VERSION already downloaded"
fi

# Verify Java is available
if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found. Please install a JDK."
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found. Please install a JRE/JDK."
    exit 1
fi

# Check Java version (RE2J requires Java 8+)
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo "Java version: $JAVA_VERSION"

# Compile the benchmark
echo "Compiling RE2J benchmark..."
javac -cp "$LIB_DIR/$RE2J_JAR" \
      -d "$BUILD_DIR" \
      "$SCRIPT_DIR/RE2JBenchmark.java"

if [ $? -eq 0 ]; then
    echo "✓ RE2J benchmark compiled successfully"
else
    echo "ERROR: Compilation failed"
    exit 1
fi

# Create a runner script that includes the classpath
cat > "$BUILD_DIR/run_re2j_benchmark.sh" << EOF
#!/bin/bash
# Generated RE2J benchmark runner
SCRIPT_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
exec java -cp "\$SCRIPT_DIR:\$SCRIPT_DIR/lib/$RE2J_JAR" RE2JBenchmark "\$@"
EOF

chmod +x "$BUILD_DIR/run_re2j_benchmark.sh"

echo "✓ Build complete!"
echo "  Executable: $BUILD_DIR/run_re2j_benchmark.sh"
echo "  RE2J JAR: $LIB_DIR/$RE2J_JAR"
echo ""
echo "Test with:"
echo "  $BUILD_DIR/run_re2j_benchmark.sh patterns.txt corpus.txt"