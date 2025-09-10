#!/bin/bash

# Test runner script for the Postgres dockerized test environment
# This script is completely independent of the main rmatch build system

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🐘 Starting Postgres Dockerized Test Environment"
echo "================================================="

# Check if Docker and Docker Compose are available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not in PATH"
    exit 1
fi

# Check for Docker Compose V2
if ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose is not available (checked 'docker compose')"
    exit 1
fi

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed or not in PATH"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Start PostgreSQL container
echo ""
echo "🚀 Starting PostgreSQL container..."
docker compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
timeout=60
counter=0

while [ $counter -lt $timeout ]; do
    if docker compose exec -T postgres pg_isready -U testuser -d prospects_test &> /dev/null; then
        echo "✅ PostgreSQL is ready!"
        break
    fi
    
    echo "  Waiting... ($counter/$timeout)"
    sleep 3
    ((counter+=3))
done

if [ $counter -ge $timeout ]; then
    echo "❌ PostgreSQL failed to start within $timeout seconds"
    docker compose logs postgres
    docker compose down
    exit 1
fi

# Compile and run the test using Maven
echo ""
echo "🔨 Compiling Java test with Maven..."
if ! mvn compile -q; then
    echo "❌ Maven compilation failed"
    docker compose down
    exit 1
fi

echo "✅ Java test compiled successfully"

# Run the test
echo ""
echo "🧪 Running PostgreSQL test..."
echo "================================"

if mvn exec:java -q; then
    echo ""
    echo "🎉 Test completed successfully!"
    TEST_RESULT=0
else
    echo ""
    echo "❌ Test failed!"
    TEST_RESULT=1
fi

# Cleanup
echo ""
echo "🧹 Cleaning up..."
docker compose down

echo "✅ Cleanup completed"

if [ $TEST_RESULT -eq 0 ]; then
    echo ""
    echo "🏆 All tests passed! The Postgres dockerized test environment is working correctly."
else
    echo ""
    echo "💥 Tests failed. Check the output above for details."
fi

exit $TEST_RESULT