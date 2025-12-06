# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the Log Analytics platform.

## Prerequisites

- Kubernetes cluster (minikube, kind, or cloud provider)
- kubectl configured to access your cluster
- Docker images built and pushed to a registry (for non-local deployments)

## Quick Start with Minikube

```bash
# Start minikube
minikube start --memory=4096 --cpus=4

# Build images in minikube's Docker
eval $(minikube docker-env)
cd ../.. 
./scripts/build-all.sh

# Apply manifests
cd infra/k8s
kubectl apply -f namespace.yaml
kubectl apply -f secrets.yaml
kubectl apply -f configmap.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f kafka-statefulset.yaml
kubectl apply -f minio-statefulset.yaml

# Wait for infrastructure
kubectl -n log-analytics wait --for=condition=ready pod -l app=postgres --timeout=120s
kubectl -n log-analytics wait --for=condition=ready pod -l app=kafka --timeout=120s
kubectl -n log-analytics wait --for=condition=ready pod -l app=minio --timeout=120s

# Deploy applications
kubectl apply -f ingestion-deployment.yaml
kubectl apply -f storage-deployment.yaml
kubectl apply -f query-deployment.yaml
```

## Accessing Services

### Port Forwarding

```bash
# Ingestion service
kubectl -n log-analytics port-forward svc/ingestion-service 8081:8081

# Query service
kubectl -n log-analytics port-forward svc/query-service 8083:8083

# MinIO console
kubectl -n log-analytics port-forward svc/minio 9001:9001
```

### Using Ingress (with nginx-ingress)

```bash
# Install nginx-ingress controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.2/deploy/static/provider/cloud/deploy.yaml

# Apply ingress
kubectl apply -f ingress.yaml

# Add to /etc/hosts (minikube)
echo "$(minikube ip) logs.local" | sudo tee -a /etc/hosts
```

## Manifest Files

| File | Description |
|------|-------------|
| `namespace.yaml` | Creates the log-analytics namespace |
| `configmap.yaml` | Configuration for all services |
| `secrets.yaml` | Sensitive credentials |
| `postgres-statefulset.yaml` | PostgreSQL StatefulSet with PVC |
| `kafka-statefulset.yaml` | Kafka + Zookeeper StatefulSets |
| `minio-statefulset.yaml` | MinIO StatefulSet with PVC |
| `ingestion-deployment.yaml` | Ingestion service Deployment + Service |
| `storage-deployment.yaml` | Storage service Deployment + Service |
| `query-deployment.yaml` | Query service Deployment + Service |
| `ingress.yaml` | Nginx Ingress for external access |

## Resource Requirements

Default resource requests/limits per pod:

| Service | Memory Request | Memory Limit | CPU Request | CPU Limit |
|---------|---------------|--------------|-------------|-----------|
| Ingestion | 256Mi | 512Mi | 100m | 500m |
| Storage | 256Mi | 512Mi | 100m | 500m |
| Query | 256Mi | 512Mi | 100m | 500m |
| Postgres | 256Mi | 512Mi | 250m | 500m |
| Kafka | 512Mi | 1Gi | 250m | 500m |
| Zookeeper | 256Mi | 512Mi | 100m | 250m |
| MinIO | 256Mi | 512Mi | 100m | 250m |

**Total minimum cluster resources:** ~3GB RAM, 2 CPU cores

## Cleanup

```bash
kubectl delete namespace log-analytics
```

## Production Considerations

For production deployments, consider:

1. **Use Helm charts** for Kafka and PostgreSQL (Bitnami charts recommended)
2. **Enable TLS** for all services
3. **Use external secrets management** (e.g., AWS Secrets Manager, HashiCorp Vault)
4. **Configure HPA** for auto-scaling application services
5. **Set up proper monitoring** with Prometheus Operator
6. **Use managed services** where available (RDS, MSK, S3)
7. **Configure network policies** for pod-to-pod communication
8. **Set up proper backup strategies** for PostgreSQL and MinIO

