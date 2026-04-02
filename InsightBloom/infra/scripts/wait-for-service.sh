#!/usr/bin/env bash
HOST=${1:?host required}
PORT=${2:?port required}
TIMEOUT=${3:-30}
echo "Waiting for $HOST:$PORT..."
for i in $(seq 1 $TIMEOUT); do
  if nc -z "$HOST" "$PORT" 2>/dev/null; then
    echo "$HOST:$PORT is ready."
    exit 0
  fi
  sleep 1
done
echo "Timeout waiting for $HOST:$PORT"
exit 1
