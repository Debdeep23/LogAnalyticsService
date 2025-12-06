# Load Generator

A Python-based load testing tool for the Log Analytics platform.

## Installation

```bash
cd scripts/load-generator
pip install -r requirements.txt
```

## Usage

### Basic Usage

```bash
# Generate 1000 logs at 50 req/s
python load_generator.py

# Generate 10000 logs at 100 req/s with 8 workers
python load_generator.py --count 10000 --rate 100 --workers 8

# Target a specific URL
python load_generator.py --url http://my-ingestion-service:8081/logs
```

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `--count` | Total number of logs to generate | 1000 |
| `--rate` | Target requests per second | 50 |
| `--workers` | Number of concurrent workers | 4 |
| `--url` | Ingestion service URL | http://localhost:8081/logs |

## Example Output

```
============================================================
        LOG ANALYTICS LOAD GENERATOR
============================================================

🚀 Starting load test...
   URL: http://localhost:8081/logs
   Total logs: 10000
   Target rate: 100 req/s
   Workers: 8

   Progress: 1000/10000 (98.5 req/s)
   Progress: 2000/10000 (101.2 req/s)
   ...

============================================================
                    LOAD TEST RESULTS
============================================================

📊 Summary:
   Duration:          101.23 seconds
   Total Requests:    10000
   Successful:        9987
   Failed:            13
   Success Rate:      99.87%

⚡ Throughput:
   Requests/sec:      98.78

⏱️  Latency:
   Average:           12.34 ms
   P50:               8.45 ms
   P95:               28.67 ms
   P99:               45.23 ms

============================================================
```

## Log Event Format

The generator creates realistic log events with the following structure:

```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "serviceName": "user-service",
  "level": "INFO",
  "message": "Request processed successfully",
  "host": "server-01",
  "traceId": "trace-123456",
  "extra": {
    "userId": "user-1234",
    "requestId": "req-567890",
    "duration": 125
  }
}
```

### Field Randomization

- **serviceName**: Randomly selected from 8 predefined services
- **level**: Weighted random selection (INFO most common, TRACE least)
- **host**: Randomly selected from 10 servers
- **traceId**: Included with 70% probability
- **extra**: Included with 30% probability

