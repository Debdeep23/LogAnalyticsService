#!/usr/bin/env python3
"""
Log Analytics Load Generator

A concurrent load testing tool for the Log Analytics ingestion service.
Generates realistic log events and measures throughput and latency.

Usage:
    python load_generator.py --count 10000 --rate 100 --workers 4

Args:
    --count: Total number of logs to generate (default: 1000)
    --rate: Target requests per second (default: 50)
    --workers: Number of concurrent workers (default: 4)
    --url: Ingestion service URL (default: http://localhost:8081/logs)
"""

import argparse
import asyncio
import json
import random
import time
from datetime import datetime, timezone
from typing import List, Dict, Any
import aiohttp
from dataclasses import dataclass
from collections import defaultdict
import sys

# Sample data for generating realistic logs
SERVICES = [
    "user-service",
    "order-service",
    "payment-service",
    "inventory-service",
    "notification-service",
    "auth-service",
    "api-gateway",
    "analytics-service",
]

LEVELS = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
LEVEL_WEIGHTS = [0.05, 0.15, 0.50, 0.20, 0.10]  # INFO is most common

HOSTS = [f"server-{i:02d}" for i in range(1, 11)]

MESSAGES = {
    "INFO": [
        "Request processed successfully",
        "User logged in",
        "Cache hit for key",
        "Database query completed",
        "Scheduled job executed",
        "Health check passed",
        "Configuration reloaded",
        "Session created",
    ],
    "WARN": [
        "High memory usage detected",
        "Slow query execution: {}ms",
        "Rate limit approaching threshold",
        "Deprecated API endpoint called",
        "Connection pool running low",
        "Retry attempt {} of 3",
    ],
    "ERROR": [
        "Failed to connect to database",
        "Authentication failed for user",
        "Payment processing error",
        "Timeout waiting for response",
        "Invalid request format",
        "Service unavailable",
        "Out of memory",
    ],
    "DEBUG": [
        "Entering method processRequest",
        "Variable state: {}",
        "SQL query: SELECT * FROM...",
        "HTTP headers received",
        "Serializing response object",
    ],
    "TRACE": [
        "Stack trace entry",
        "Low-level operation started",
        "Byte buffer allocated",
    ],
}


@dataclass
class RequestResult:
    success: bool
    latency_ms: float
    status_code: int = 0
    error: str = ""


@dataclass
class LoadTestStats:
    total_requests: int = 0
    successful_requests: int = 0
    failed_requests: int = 0
    total_latency_ms: float = 0.0
    latencies: List[float] = None
    start_time: float = 0.0
    end_time: float = 0.0
    errors: Dict[str, int] = None

    def __post_init__(self):
        if self.latencies is None:
            self.latencies = []
        if self.errors is None:
            self.errors = defaultdict(int)

    @property
    def duration_seconds(self) -> float:
        return self.end_time - self.start_time

    @property
    def requests_per_second(self) -> float:
        if self.duration_seconds > 0:
            return self.total_requests / self.duration_seconds
        return 0.0

    @property
    def success_rate(self) -> float:
        if self.total_requests > 0:
            return (self.successful_requests / self.total_requests) * 100
        return 0.0

    @property
    def avg_latency_ms(self) -> float:
        if self.successful_requests > 0:
            return self.total_latency_ms / self.successful_requests
        return 0.0

    @property
    def p50_latency_ms(self) -> float:
        return self._percentile(50)

    @property
    def p95_latency_ms(self) -> float:
        return self._percentile(95)

    @property
    def p99_latency_ms(self) -> float:
        return self._percentile(99)

    def _percentile(self, p: int) -> float:
        if not self.latencies:
            return 0.0
        sorted_latencies = sorted(self.latencies)
        idx = int(len(sorted_latencies) * p / 100)
        return sorted_latencies[min(idx, len(sorted_latencies) - 1)]


def generate_log_event() -> Dict[str, Any]:
    """Generate a realistic log event."""
    level = random.choices(LEVELS, weights=LEVEL_WEIGHTS)[0]
    service = random.choice(SERVICES)
    
    messages = MESSAGES.get(level, MESSAGES["INFO"])
    message = random.choice(messages)
    
    # Add some dynamic content to messages
    if "{}" in message:
        message = message.format(random.randint(100, 5000))
    
    event = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "serviceName": service,
        "level": level,
        "message": message,
        "host": random.choice(HOSTS),
    }
    
    # Add traceId with 70% probability
    if random.random() < 0.7:
        event["traceId"] = f"trace-{random.randint(100000, 999999)}"
    
    # Add extra metadata with 30% probability
    if random.random() < 0.3:
        event["extra"] = {
            "userId": f"user-{random.randint(1, 10000)}",
            "requestId": f"req-{random.randint(100000, 999999)}",
            "duration": random.randint(1, 1000),
        }
    
    return event


async def send_log(session: aiohttp.ClientSession, url: str, event: Dict[str, Any]) -> RequestResult:
    """Send a single log event to the ingestion service."""
    start_time = time.perf_counter()
    try:
        async with session.post(url, json=event) as response:
            latency_ms = (time.perf_counter() - start_time) * 1000
            if response.status == 200:
                return RequestResult(success=True, latency_ms=latency_ms, status_code=response.status)
            else:
                text = await response.text()
                return RequestResult(
                    success=False,
                    latency_ms=latency_ms,
                    status_code=response.status,
                    error=text[:100],
                )
    except Exception as e:
        latency_ms = (time.perf_counter() - start_time) * 1000
        return RequestResult(success=False, latency_ms=latency_ms, error=str(e)[:100])


async def worker(
    worker_id: int,
    url: str,
    event_queue: asyncio.Queue,
    stats: LoadTestStats,
    rate_limiter: asyncio.Semaphore,
):
    """Worker coroutine that sends log events."""
    connector = aiohttp.TCPConnector(limit=100)
    timeout = aiohttp.ClientTimeout(total=30)
    
    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        while True:
            try:
                event = await asyncio.wait_for(event_queue.get(), timeout=1.0)
            except asyncio.TimeoutError:
                continue
            except Exception:
                break
            
            if event is None:
                break
            
            async with rate_limiter:
                result = await send_log(session, url, event)
            
            stats.total_requests += 1
            if result.success:
                stats.successful_requests += 1
                stats.total_latency_ms += result.latency_ms
                stats.latencies.append(result.latency_ms)
            else:
                stats.failed_requests += 1
                stats.errors[result.error] += 1
            
            event_queue.task_done()


async def run_load_test(
    url: str,
    count: int,
    rate: int,
    workers: int,
) -> LoadTestStats:
    """Run the load test with specified parameters."""
    stats = LoadTestStats()
    event_queue = asyncio.Queue(maxsize=workers * 10)
    rate_limiter = asyncio.Semaphore(rate)
    
    # Create workers
    worker_tasks = [
        asyncio.create_task(worker(i, url, event_queue, stats, rate_limiter))
        for i in range(workers)
    ]
    
    print(f"\n🚀 Starting load test...")
    print(f"   URL: {url}")
    print(f"   Total logs: {count}")
    print(f"   Target rate: {rate} req/s")
    print(f"   Workers: {workers}\n")
    
    stats.start_time = time.perf_counter()
    
    # Generate and queue events
    for i in range(count):
        event = generate_log_event()
        await event_queue.put(event)
        
        # Progress indicator
        if (i + 1) % 1000 == 0:
            elapsed = time.perf_counter() - stats.start_time
            current_rate = (i + 1) / elapsed if elapsed > 0 else 0
            print(f"   Progress: {i + 1}/{count} ({current_rate:.1f} req/s)")
    
    # Wait for queue to be processed
    await event_queue.join()
    
    # Signal workers to stop
    for _ in range(workers):
        await event_queue.put(None)
    
    await asyncio.gather(*worker_tasks)
    
    stats.end_time = time.perf_counter()
    return stats


def print_results(stats: LoadTestStats):
    """Print formatted test results."""
    print("\n" + "=" * 60)
    print("                    LOAD TEST RESULTS")
    print("=" * 60)
    
    print(f"\n📊 Summary:")
    print(f"   Duration:          {stats.duration_seconds:.2f} seconds")
    print(f"   Total Requests:    {stats.total_requests}")
    print(f"   Successful:        {stats.successful_requests}")
    print(f"   Failed:            {stats.failed_requests}")
    print(f"   Success Rate:      {stats.success_rate:.2f}%")
    
    print(f"\n⚡ Throughput:")
    print(f"   Requests/sec:      {stats.requests_per_second:.2f}")
    
    print(f"\n⏱️  Latency:")
    print(f"   Average:           {stats.avg_latency_ms:.2f} ms")
    print(f"   P50:               {stats.p50_latency_ms:.2f} ms")
    print(f"   P95:               {stats.p95_latency_ms:.2f} ms")
    print(f"   P99:               {stats.p99_latency_ms:.2f} ms")
    
    if stats.errors:
        print(f"\n❌ Errors:")
        for error, count in sorted(stats.errors.items(), key=lambda x: -x[1])[:5]:
            print(f"   [{count}x] {error}")
    
    print("\n" + "=" * 60 + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="Load Generator for Log Analytics Ingestion Service"
    )
    parser.add_argument(
        "--count",
        type=int,
        default=1000,
        help="Total number of logs to generate (default: 1000)",
    )
    parser.add_argument(
        "--rate",
        type=int,
        default=50,
        help="Target requests per second (default: 50)",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=4,
        help="Number of concurrent workers (default: 4)",
    )
    parser.add_argument(
        "--url",
        type=str,
        default="http://localhost:8081/logs",
        help="Ingestion service URL (default: http://localhost:8081/logs)",
    )
    
    args = parser.parse_args()
    
    print("\n" + "=" * 60)
    print("        LOG ANALYTICS LOAD GENERATOR")
    print("=" * 60)
    
    try:
        stats = asyncio.run(
            run_load_test(
                url=args.url,
                count=args.count,
                rate=args.rate,
                workers=args.workers,
            )
        )
        print_results(stats)
        
        # Exit with error if success rate is below 95%
        if stats.success_rate < 95:
            sys.exit(1)
            
    except KeyboardInterrupt:
        print("\n\n⚠️  Load test interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()

