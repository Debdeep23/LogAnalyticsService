#!/bin/bash

# Stop All Services Script
# Stops all Docker Compose services

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="${SCRIPT_DIR}/../infra"

echo "=============================================="
echo "  Stopping Log Analytics Services"
echo "=============================================="

cd "$INFRA_DIR"

# Stop all services
docker compose down

echo ""
echo "All services stopped."
echo ""
echo "To remove volumes as well, run:"
echo "  cd infra && docker compose down -v"
echo ""

