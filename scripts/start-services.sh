#!/bin/bash

set -e

echo "🚀 Starting Docker services for Audio Pipeline..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker first."
    exit 1
fi

# Function to check if container exists
container_exists() {
    docker ps -a --format '{{.Names}}' | grep -q "^$1$"
}

# Function to check if container is running
container_running() {
    docker ps --format '{{.Names}}' | grep -q "^$1$"
}

# Function to start or restart a container
start_container() {
    local name=$1
    
    if container_running "$name"; then
        echo "✅ $name is already running"
        return 0
    fi
    
    if container_exists "$name"; then
        echo "🔄 Starting existing $name container..."
        docker start "$name"
    else
        echo "📦 Creating and starting $name container..."
        case $name in
            minio)
                docker run -d \
                  --name minio \
                  -p 9000:9000 \
                  -p 9001:9001 \
                  --memory=512m \
                  -e MINIO_ROOT_USER=minioadmin \
                  -e MINIO_ROOT_PASSWORD=minioadmin \
                  -v minio_data:/data \
                  minio/minio server /data --console-address ":9001"
                ;;
            rabbitmq)
                docker run -d \
                  --name rabbitmq \
                  -p 5672:5672 \
                  -p 15672:15672 \
                  --memory=512m \
                  rabbitmq:3-management
                ;;
        esac
    fi
}

# Start MinIO
echo ""
echo "📂 MinIO Storage Service"
start_container "minio"

# Start RabbitMQ
echo ""
echo "🐰 RabbitMQ Message Broker"
start_container "rabbitmq"

# Display status
echo ""
echo "✨ All services started successfully!"
echo ""
echo "📋 Service URLs:"
echo "   MinIO API:        http://localhost:9000"
echo "   MinIO Console:    http://localhost:9001 (minioadmin/minioadmin)"
echo "   RabbitMQ AMQP:    amqp://localhost:5672"
echo "   RabbitMQ UI:      http://localhost:15672 (guest/guest)"
echo ""
echo "💡 To stop services, run: docker stop minio rabbitmq"
echo "💡 To view logs: docker logs -f <container_name>"
