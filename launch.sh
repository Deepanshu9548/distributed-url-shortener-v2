#!/bin/bash

# Function to clean up background processes on exit
cleanup() {
    echo ""
    echo "🛑 Stopping servers..."
    kill $BACKEND_PID 2>/dev/null
    kill $FRONTEND_PID 2>/dev/null
    echo "✅ Servers stopped."
    exit 0
}

# Trap SIGINT (Ctrl+C) and call the cleanup function
trap cleanup SIGINT SIGTERM

# Get the directory where the script is located and cd into it
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Distributed URL Shortener (Native Local Mode)..."
echo "=========================================================="

# Start Backend
echo "⏳ [1/2] Starting Spring Boot Backend on port 8080..."
cd backend
# Export variables from .env so Spring Boot can read them
set -a && source .env && set +a
# Run maven in the background
mvn spring-boot:run -Dspring-boot.run.profiles=local &
BACKEND_PID=$!
cd ..

# Wait a couple of seconds to let backend initialize slightly
sleep 2

# Start Frontend
echo "⏳ [2/2] Starting Vite Frontend on port 5173..."
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo "=========================================================="
echo "🎉 Servers are booting up!"
echo "👉 Frontend App: http://localhost:5173"
echo "👉 Backend API:  http://localhost:8080"
echo "=========================================================="
echo "⚠️  Keep this terminal window open. Press Ctrl+C to stop both servers."

# Wait for background processes to keep the script running
wait $BACKEND_PID
wait $FRONTEND_PID
