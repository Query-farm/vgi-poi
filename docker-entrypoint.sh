#!/bin/sh
# Copyright 2026 Query Farm LLC - https://query.farm
#
# Dispatch the single vgi-poi image into one of its transports:
#   http   (default) the HTTP server on $PORT (8000), bound 0.0.0.0 so a
#                    published host port reaches it. Serves /health.
#   unix             AF_UNIX launcher on $SOCK (/tmp/vgi-poi.sock by default),
#                    an on-host spawn mode DuckDB connects to.
#   stdio            a worker DuckDB spawns over stdio (on-host execution).
# Any other first argument is exec'd verbatim (escape hatch for debugging).
#
# The worker is stateless (POI parses whatever bytes/paths a query supplies),
# so there is no /data to create and no state env to wire — each mode just
# exec's the fat JAR. The JAR manifest bakes `Add-Opens: java.base/java.nio`
# (Arrow needs it), so a bare `java -jar` works with no extra flags.
set -e

case "${1:-http}" in
  http)
    shift 2>/dev/null || true
    # Bind 0.0.0.0 on a FIXED port so `-p $PORT:$PORT` and the HEALTHCHECK reach
    # the server. The Java SDK's --http serves GET /health at the root.
    exec java -jar /app/worker.jar --http --host 0.0.0.0 --port "${PORT:-8000}" "$@"
    ;;
  unix)
    shift 2>/dev/null || true
    exec java -jar /app/worker.jar --unix "${SOCK:-/tmp/vgi-poi.sock}" "$@"
    ;;
  stdio)
    shift 2>/dev/null || true
    exec java -jar /app/worker.jar "$@"
    ;;
  *)
    exec "$@"
    ;;
esac
