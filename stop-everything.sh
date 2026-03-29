#!/bin/bash

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🛑 Stopping ALL Pipeline Components...${NC}"

# 1. Stop Python Workers
echo -e "\n${BLUE}🐍 Step 1: Stopping Python Workers...${NC}"
./stop-python-workers.sh

# 2. Stop Java API
echo -e "\n${BLUE}☕ Step 2: Stopping Spring Boot API...${NC}"
if [ -f "api.pid" ]; then
    PID=$(cat api.pid)
    if ps -p "$PID" > /dev/null; then
        echo -e "${YELLOW}🛑 Stopping Java API (PID: ${PID})...${NC}"
        kill "$PID"
        echo -e "${GREEN}✅ API stopped.${NC}"
    else
        echo -e "${RED}⚠️  API is not running.${NC}"
    fi
    rm api.pid
else
    echo -e "${RED}⚠️  No API PID file found.${NC}"
fi

# 3. Stop Docker Infrastructure
echo -e "\n${BLUE}🐳 Step 3: Stopping Docker Services...${NC}"
docker-compose down

echo -e "\n${GREEN}✨ FULL PIPELINE STOPPED.${NC}"
