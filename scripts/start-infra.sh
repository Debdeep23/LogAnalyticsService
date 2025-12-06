#!/bin/bash

# Start Infrastructure Script
# Brings up Kafka, Postgres, and MinIO for local development

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="${SCRIPT_DIR}/../infra"

echo "=============================================="
echo "  Starting Log Analytics Infrastructure"
echo "=============================================="

cd "$INFRA_DIR"

# Start only infrastructure services (not the apps)
echo "Starting infrastructure services..."
docker compose up -d zookeeper kafka postgres minio

# Wait for services to be healthy
echo "Waiting for services to be ready..."

# Wait for Kafka
echo -n "Waiting for Kafka..."
until docker compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo " Ready!"

# Wait for Postgres
echo -n "Waiting for Postgres..."
until docker compose exec -T postgres pg_isready -U loguser -d logdb > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo " Ready!"

# Wait for MinIO
echo -n "Waiting for MinIO..."
until curl -s http://localhost:9000/minio/health/ready > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo " Ready!"

# Initialize Kafka topic
echo "Creating Kafka topic..."
docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic logs_raw --partitions 3 --replication-factor 1 || true

# Initialize MinIO bucket
echo "Creating MinIO bucket..."
docker compose run --rm minio-init || true

echo ""
echo "=============================================="
echo "  Infrastructure is ready!"
echo "=============================================="
echo ""
echo "Services:"
echo "  Kafka:          localhost:9092"
echo "  Postgres:       localhost:5432 (loguser/logpassword)"
echo "  MinIO API:      http://localhost:9000"
echo "  MinIO Console:  http://localhost:9001 (minioadmin/minioadmin123)"
echo ""
echo "To start the applications, run each service with Maven:"
echo "  cd ingestion-service && mvn spring-boot:run"
echo "  cd storage-service && mvn spring-boot:run"
echo "  cd query-service && mvn spring-boot:run"
echo ""

