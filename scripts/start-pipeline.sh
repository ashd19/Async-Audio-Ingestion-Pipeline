#!/bin/bash

set -e

# Get script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"
cd "$PROJECT_ROOT"

echo "🎵 Audio Pipeline - Complete Startup Script"
echo "============================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Docker is running
echo ""
echo "📋 Checking prerequisites..."
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Error: Docker is not running. Please start Docker first.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker is running${NC}"

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Error: Python 3 is not installed.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Python 3 is available${NC}"

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Error: Java is not installed.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java is available${NC}"

# Start Docker infrastructure
echo ""
echo "🐳 Starting Docker infrastructure..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check service health
echo ""
echo "🏥 Checking service health..."

# Check RabbitMQ
if curl -s http://localhost:15672 > /dev/null; then
    echo -e "${GREEN}✅ RabbitMQ is ready${NC}"
else
    echo -e "${YELLOW}⚠️  RabbitMQ may still be starting...${NC}"
fi

# Check MinIO
if curl -s http://localhost:9000/minio/health/live > /dev/null; then
    echo -e "${GREEN}✅ MinIO is ready${NC}"
else
    echo -e "${YELLOW}⚠️  MinIO may still be starting...${NC}"
fi

# Check PostgreSQL
if docker exec audio-pipeline-postgres pg_isready -U postgres > /dev/null 2>&1; then
    echo -e "${GREEN}✅ PostgreSQL is ready${NC}"
else
    echo -e "${YELLOW}⚠️  PostgreSQL may still be starting...${NC}"
fi

# Check if Python virtual environment exists
echo ""
if [ ! -d "workers/venv" ]; then
    echo "📦 Setting up Python virtual environment..."
    cd workers
    python3 -m venv venv
    source venv/bin/activate
    pip install --quiet -r requirements.txt
    cd "$PROJECT_ROOT"
    echo -e "${GREEN}✅ Python environment created${NC}"
else
    echo -e "${GREEN}✅ Python environment already exists${NC}"
fi

# Check if .env exists for workers
if [ ! -f "workers/.env" ]; then
    echo ""
    echo -e "${YELLOW}⚠️  workers/.env not found, copying from .env.example${NC}"
    cp workers/.env.example workers/.env
    echo -e "${GREEN}✅ Created workers/.env (please review and adjust if needed)${NC}"
fi

echo ""
echo "✨ Infrastructure is ready!"
echo ""
echo "📋 Next Steps:"
echo ""
echo "1️⃣  Start API Server (in a new terminal):"
echo "   cd AudioPipeline && ./mvnw spring-boot:run"
echo ""
echo "2️⃣  Start Python Workers (in this or a new terminal):"
echo "   ./scripts/run-python-workers.sh"
echo ""
echo "3️⃣  Test the pipeline:"
echo "   curl -X POST http://localhost:8080/api/upload -F \"file=@your_audio.mp3\""
echo ""
echo "📊 Monitoring & Logs:"
echo "   API:              http://localhost:8080"
echo "   RabbitMQ UI:      http://localhost:15672 (guest/guest)"
echo "   MinIO Console:    http://localhost:9001 (minioadmin/minioadmin)"
echo "   Worker Logs:      tail -f workers/*.log"
echo "   Stop Workers:     ./scripts/stop-python-workers.sh"
echo ""
echo "🛑 To stop all infrastructure: docker-compose down"
