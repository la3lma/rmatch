# Prospects Directory - Dockerized Test Environment

This directory contains experimental dockerized test environments that are completely independent of the main rmatch test suite.

## Postgres Test Environment

The Postgres test demonstrates:
- Docker Compose setup with PostgreSQL database
- Simple Java application connecting to Postgres
- Basic schema creation and data manipulation
- Independent test execution isolated from main codebase

### Usage

```bash
# Start the Postgres container
cd prospects
docker compose up -d

# Run the Postgres test
./run-postgres-test.sh

# Stop the containers
docker compose down
```

### Requirements

- Docker and Docker Compose
- Java 17+ 
- Maven 3.6+

### Test Structure

- `docker-compose.yml`: PostgreSQL service configuration
- `schema.sql`: Database schema initialization
- `data.sql`: Sample data insertion  
- `pom.xml`: Maven project configuration with PostgreSQL JDBC dependency
- `src/main/java/PostgresTest.java`: Simple Java test connecting to Postgres
- `run-postgres-test.sh`: Test runner script