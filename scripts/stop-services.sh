#!/bin/bash

set -e

echo "🛑 Stopping Docker services for Audio Pipeline..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running."
    exit 1
fi

# Function to check if container is running
container_running() {
    docker ps --format '{{.Names}}' | grep -q "^$1$"
}

# Function to stop a container
stop_container() {
    local name=$1
    
    if container_running "$name"; then
        echo "⏹️  Stopping $name..."
        docker stop "$name"
        echo "✅ $name stopped"
    else
        echo "ℹ️  $name is not running"
    fi
}

# Stop MinIO
echo ""
stop_container "minio"

# Stop RabbitMQ
echo ""
stop_container "rabbitmq"

echo ""
echo "✨ All services stopped successfully!"
echo ""
echo "💡 To start services again, run: ./start-services.sh"
echo "💡 To remove containers, run: docker rm minio rabbitmq"
