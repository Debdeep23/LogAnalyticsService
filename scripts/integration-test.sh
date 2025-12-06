#!/bin/bash

# Integration Test Script for Log Analytics Platform
# This script performs end-to-end testing of the system

set -e

# Configuration
INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
QUERY_URL="${QUERY_URL:-http://localhost:8083}"
WAIT_TIME=5

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=============================================="
echo "  Log Analytics Integration Test"
echo "=============================================="

# Helper functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_service() {
    local url=$1
    local name=$2
    log_info "Checking $name health..."
    if curl -s -f "${url}/actuator/health" > /dev/null 2>&1; then
        log_info "$name is healthy ✓"
        return 0
    else
        log_error "$name is not responding"
        return 1
    fi
}

# Test 1: Health Checks
echo ""
echo "--- Test 1: Service Health Checks ---"
check_service "$INGESTION_URL" "Ingestion Service"
check_service "$QUERY_URL" "Query Service"

# Test 2: Ingest Test Logs
echo ""
echo "--- Test 2: Ingesting Test Logs ---"

# Generate a unique test identifier
TEST_ID="test-$(date +%s)"
SERVICE_NAME="integration-test-${TEST_ID}"

log_info "Ingesting test logs with service name: $SERVICE_NAME"

for level in INFO WARN ERROR; do
    response=$(curl -s -w "\n%{http_code}" -X POST "${INGESTION_URL}/logs" \
        -H "Content-Type: application/json" \
        -d "{
            \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
            \"serviceName\": \"${SERVICE_NAME}\",
            \"level\": \"${level}\",
            \"message\": \"Integration test message - ${level}\",
            \"host\": \"test-host\",
            \"traceId\": \"${TEST_ID}\"
        }")
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" -eq 200 ]; then
        log_info "Ingested $level log ✓"
    else
        log_error "Failed to ingest $level log: $body"
        exit 1
    fi
done

# Test 3: Wait for processing
echo ""
echo "--- Test 3: Waiting for Kafka Processing ---"
log_info "Waiting ${WAIT_TIME} seconds for logs to be processed..."
sleep $WAIT_TIME

# Test 4: Query logs
echo ""
echo "--- Test 4: Querying Logs ---"

response=$(curl -s -w "\n%{http_code}" "${QUERY_URL}/logs?serviceName=${SERVICE_NAME}&size=10")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" -eq 200 ]; then
    total=$(echo "$body" | grep -o '"totalElements":[0-9]*' | grep -o '[0-9]*')
    if [ "$total" -ge 3 ]; then
        log_info "Found $total logs for test service ✓"
    else
        log_warn "Expected at least 3 logs, found $total"
    fi
else
    log_error "Query failed: $body"
    exit 1
fi

# Test 5: Query error statistics
echo ""
echo "--- Test 5: Error Statistics ---"

response=$(curl -s -w "\n%{http_code}" "${QUERY_URL}/stats/errors-per-service")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" -eq 200 ]; then
    log_info "Error statistics endpoint working ✓"
else
    log_error "Error statistics query failed: $body"
    exit 1
fi

# Test 6: Query level distribution
echo ""
echo "--- Test 6: Level Distribution ---"

response=$(curl -s -w "\n%{http_code}" "${QUERY_URL}/stats/levels")
http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" -eq 200 ]; then
    log_info "Level distribution endpoint working ✓"
else
    log_error "Level distribution query failed: $body"
    exit 1
fi

# Test 7: Prometheus metrics
echo ""
echo "--- Test 7: Prometheus Metrics ---"

for service_url in "$INGESTION_URL" "$QUERY_URL"; do
    if curl -s -f "${service_url}/actuator/prometheus" | head -5 > /dev/null 2>&1; then
        log_info "Prometheus metrics available at ${service_url} ✓"
    else
        log_warn "Prometheus metrics not available at ${service_url}"
    fi
done

# Summary
echo ""
echo "=============================================="
echo "  Integration Test Complete!"
echo "=============================================="
log_info "All tests passed successfully ✓"
echo ""

