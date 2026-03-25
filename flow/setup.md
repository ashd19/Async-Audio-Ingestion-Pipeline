# Setup

Project setup notes go here.

## Running Locally

### MinIO

```bash
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  --memory=512m \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v minio_data:/data \
  minio/minio server /data --console-address ":9001"
```

**URLs:**
- API: http://localhost:9000
- Console: http://localhost:9001
- Credentials: `minioadmin` / `minioadmin`

### RabbitMQ

```bash
docker run -d \
  --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  --memory=512m \
  rabbitmq:3-management
```

**URLs:**
- AMQP: `amqp://localhost:5672`
- Management UI: http://localhost:15672
- Default credentials: `guest` / `guest`
