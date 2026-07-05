# Demo Script

This directory contains `demo.sh`, which exercises the core functionality of the URL Shortener end-to-end via `curl`.

## Prerequisites

1. The URL Shortener application must be running locally on `http://localhost:8080`.
2. Redis, PostgreSQL, and Kafka must be available (e.g., via `docker-compose up`).

## Running the Demo

```bash
./demo.sh
```

## Expected Output

```
=== URL Shortener Demo ===

1. Registering user 'demo'...
2. Logging in...
Received JWT token.

3. Creating a short link...
Created short code: XyZ123

4. Redirecting...
HTTP Status: 302 (Expected 302)

Waiting 1s for async analytics to process...
5. Fetching stats...
Stats: {"shortCode":"XyZ123","totalClicks":1}

6. Updating the link...
Link updated.

7. Redirecting again (should see new destination)...
Location: https://example.com/demo-updated

8. Deleting the link...
Link deleted.

9. Redirecting to deleted link (Expected 404)...
HTTP Status: 404

=== Demo Complete ===
```
