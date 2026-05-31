#!/bin/bash

set -eEuo pipefail
trap 'echo "❌ Worker startup failed at: ${BASH_COMMAND}" >&2' ERR

# Get script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"
cd "$PROJECT_ROOT"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting Audio Pipeline Python Workers...${NC}"

# Determine a system Python to create the venv
SYS_PYTHON=""
if command -v python3 &> /dev/null; then
    SYS_PYTHON="python3"
elif command -v python &> /dev/null; then
    SYS_PYTHON="python"
else
    echo -e "${RED}❌ Error: Python is not installed (python3 or python).${NC}"
    exit 1
fi

# Check if virtual environment exists
if [ ! -d "workers/venv" ]; then
    echo -e "${YELLOW}📦 Setting up Python virtual environment...${NC}"
    cd workers
    "$SYS_PYTHON" -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
    cd "$PROJECT_ROOT"
fi

# Activate virtual environment
source workers/venv/bin/activate
PYTHON_BIN="$(command -v python || true)"
if [ -z "$PYTHON_BIN" ]; then
    PYTHON_BIN="$(command -v python3 || true)"
fi
if [ -z "$PYTHON_BIN" ]; then
    echo -e "${RED}❌ Error: Python executable not found in virtual environment.${NC}"
    exit 1
fi

# Check for .env file
if [ ! -f "workers/.env" ]; then
    echo -e "${YELLOW}⚠️  workers/.env not found, copying from .env.example${NC}"
    cp workers/.env.example workers/.env
fi

# Export worker environment variables
set -a
source workers/.env
set +a

# Function to start a worker
start_worker() {
    local worker_file=$1
    local worker_name=$2
    local log_file="workers/${worker_name}.log"

    echo -e "${GREEN}✅ Starting ${worker_name}...${NC}"
    nohup "$PYTHON_BIN" "workers/${worker_file}" > "${log_file}" 2>&1 &
    local pid=$!
    echo "$pid" > "workers/${worker_name}.pid"
    sleep 1
    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "${RED}❌ ${worker_name} failed to start. Check ${log_file}.${NC}"
        exit 1
    fi
    echo -e "   Logs: ${log_file}"
}

# Start workers
start_worker "normalization_worker.py" "normalization_worker"
start_worker "transcription_worker.py" "transcription_worker"

echo ""
echo -e "${GREEN}✨ Both workers are running in the background!${NC}"
echo -e "To stop them, run: ${YELLOW}./scripts/stop-python-workers.sh${NC}"
echo -e "To view logs: ${YELLOW}tail -f workers/*.log${NC}"
