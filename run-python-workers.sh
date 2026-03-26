#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting Audio Pipeline Python Workers...${NC}"

# Check if virtual environment exists
if [ ! -d "workers/venv" ]; then
    echo -e "${YELLOW}📦 Setting up Python virtual environment...${NC}"
    cd workers
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    cd ..
fi

# Activate virtual environment
source workers/venv/bin/activate

# Check for .env file
if [ ! -f "workers/.env" ]; then
    echo -e "${YELLOW}⚠️  workers/.env not found, copying from .env.example${NC}"
    cp workers/.env.example workers/.env
fi

# Function to start a worker
start_worker() {
    local worker_file=$1
    local worker_name=$2
    local log_file="workers/${worker_name}.log"

    echo -e "${GREEN}✅ Starting ${worker_name}...${NC}"
    nohup python workers/${worker_file} > "${log_file}" 2>&1 &
    echo $! > "workers/${worker_name}.pid"
    echo -e "   Logs: ${log_file}"
}

# Start workers
start_worker "normalization_worker.py" "normalization_worker"
start_worker "transcription_worker.py" "transcription_worker"

echo ""
echo -e "${GREEN}✨ Both workers are running in the background!${NC}"
echo -e "To stop them, run: ${YELLOW}./stop-python-workers.sh${NC}"
echo -e "To view logs: ${YELLOW}tail -f workers/*.log${NC}"
