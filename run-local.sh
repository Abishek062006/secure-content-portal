#!/usr/bin/env bash
# Loads .env into the environment and runs the app locally.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found — copy .env.example to .env and fill in real values first." >&2
  exit 1
fi

set -a
source .env
set +a

mvn spring-boot:run
