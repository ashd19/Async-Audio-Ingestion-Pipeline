#!/bin/bash

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🛑 Stopping Audio Pipeline Python Workers...${NC}"

# Function to stop a worker using its PID file
stop_worker() {
    local worker_name=$1
    local pid_file="workers/${worker_name}.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null; then
            echo -e "${YELLOW}🛑 Stopping ${worker_name} (PID: ${pid})...${NC}"
            kill "$pid"
            echo -e "${GREEN}✅ ${worker_name} stopped.${NC}"
        else
            echo -e "${RED}⚠️  ${worker_name} (PID: ${pid}) is not running.${NC}"
        fi
        rm "$pid_file"
    else
        echo -e "${RED}⚠️  No PID file found for ${worker_name}. Is it running?${NC}"
    fi
}

# Stop workers
stop_worker "normalization_worker"
stop_worker "transcription_worker"

echo -e "${GREEN}✨ Done.${NC}"
