#!/bin/bash

# Build All Services Script
# Builds Docker images for all services

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."

echo "=============================================="
echo "  Building Log Analytics Services"
echo "=============================================="

# Build ingestion-service
echo ""
echo "Building ingestion-service..."
cd "${PROJECT_DIR}/ingestion-service"
docker build -t log-analytics/ingestion-service:latest .

# Build storage-service
echo ""
echo "Building storage-service..."
cd "${PROJECT_DIR}/storage-service"
docker build -t log-analytics/storage-service:latest .

# Build query-service
echo ""
echo "Building query-service..."
cd "${PROJECT_DIR}/query-service"
docker build -t log-analytics/query-service:latest .

echo ""
echo "=============================================="
echo "  Build Complete!"
echo "=============================================="
echo ""
echo "Images built:"
echo "  - log-analytics/ingestion-service:latest"
echo "  - log-analytics/storage-service:latest"
echo "  - log-analytics/query-service:latest"
echo ""
echo "To start all services:"
echo "  cd infra && docker compose up -d"
echo ""

