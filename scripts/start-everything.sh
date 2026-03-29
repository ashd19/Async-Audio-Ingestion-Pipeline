#!/bin/bash

# Get script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}🌟 Audio Pipeline - Full System Startup 🌟${NC}"
echo "============================================"

# Ensure runtime directory exists
mkdir -p "$PROJECT_ROOT/runtime"

# 1. Start Infrastructure (Docker)
echo -e "\n${BLUE}🐳 Step 1: Starting Docker Infrastructure...${NC}"
"$SCRIPT_DIR/start-pipeline.sh"

# 2. Start Java API in background
echo -e "\n${BLUE}☕ Step 2: Starting Spring Boot API...${NC}"
cd "$PROJECT_ROOT/AudioPipeline"
nohup ./mvnw spring-boot:run > "$PROJECT_ROOT/runtime/api.log" 2>&1 &
echo $! > "$PROJECT_ROOT/runtime/api.pid"
cd "$PROJECT_ROOT"
echo -e "${GREEN}✅ API is starting in the background (Logs: runtime/api.log)${NC}"

# 3. Wait for API to be ready
echo -e "\n${YELLOW}⏳ Waiting for API to be ready on port 8080...${NC}"
MAX_RETRIES=30
COUNT=0
while ! curl -s http://localhost:8080/api/jobs/1 > /dev/null 2>&1; do
    if [ $COUNT -ge $MAX_RETRIES ]; then
        echo -e "${RED}❌ API took too long to start. Check runtime/api.log for errors.${NC}"
        exit 1
    fi
    sleep 2
    COUNT=$((COUNT+1))
    echo -n "."
done
echo -e "\n${GREEN}✅ API is UP!${NC}"

# 4. Start Python Workers
echo -e "\n${BLUE}🐍 Step 3: Starting Python Workers...${NC}"
"$SCRIPT_DIR/run-python-workers.sh"

echo -e "\n${GREEN}🚀 ALL SYSTEMS GO!${NC}"
echo "--------------------------------------------"
echo -e "API:              ${BLUE}http://localhost:8080${NC}"
echo -e "RabbitMQ UI:      ${BLUE}http://localhost:15672 (guest/guest)${NC}"
echo -e "MinIO Console:    ${BLUE}http://localhost:9001 (minioadmin/minioadmin)${NC}"
echo -e "API Logs:         ${BLUE}tail -f runtime/api.log${NC}"
echo -e "Worker Logs:      ${BLUE}tail -f workers/*.log${NC}"
echo "--------------------------------------------"
echo -e "To stop everything, run: ${YELLOW}./scripts/stop-everything.sh${NC}"
