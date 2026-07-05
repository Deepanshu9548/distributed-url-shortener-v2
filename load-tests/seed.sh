#!/usr/bin/env bash
set -e

echo "Seeding 100 links..."
rm -f codes.csv

for i in {1..100}; do
  RESPONSE=$(curl -s -X POST http://localhost:8080/api/links \
    -H "Content-Type: application/json" \
    -d "{\"longUrl\": \"https://example.com/seed/${i}\"}")
  
  SHORT_CODE=$(echo "$RESPONSE" | grep -o '"shortCode":"[^"]*' | cut -d'"' -f4)
  if [ -n "$SHORT_CODE" ]; then
    echo "$SHORT_CODE" >> codes.csv
  fi
done

echo "Done seeding! Codes saved to codes.csv."
