#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080"

echo "=== URL Shortener Demo ==="
echo ""

echo "1. Registering user 'demo'..."
curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}' > /dev/null

echo "2. Logging in..."
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Login failed!"
  exit 1
fi
echo "Received JWT token."
echo ""

echo "3. Creating a short link..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/links" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com/demo"}')

SHORT_CODE=$(echo "$RESPONSE" | grep -o '"shortCode":"[^"]*' | cut -d'"' -f4)
echo "Created short code: $SHORT_CODE"
echo ""

echo "4. Redirecting..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$SHORT_CODE")
echo "HTTP Status: $HTTP_STATUS (Expected 302)"
echo ""

echo "Waiting 1s for async analytics to process..."
sleep 1

echo "5. Fetching stats..."
STATS=$(curl -s -X GET "$BASE_URL/api/links/$SHORT_CODE/stats" \
  -H "Authorization: Bearer $TOKEN")
echo "Stats: $STATS"
echo ""

echo "6. Updating the link..."
curl -s -X PUT "$BASE_URL/api/links/$SHORT_CODE" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com/demo-updated"}' > /dev/null
echo "Link updated."
echo ""

echo "7. Redirecting again (should see new destination)..."
LOCATION=$(curl -s -I "$BASE_URL/$SHORT_CODE" | grep -i Location)
echo "$LOCATION"
echo ""

echo "8. Deleting the link..."
curl -s -X DELETE "$BASE_URL/api/links/$SHORT_CODE" \
  -H "Authorization: Bearer $TOKEN" > /dev/null
echo "Link deleted."
echo ""

echo "9. Redirecting to deleted link (Expected 404)..."
HTTP_STATUS_404=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$SHORT_CODE")
echo "HTTP Status: $HTTP_STATUS_404"
echo ""

echo "=== Demo Complete ==="
