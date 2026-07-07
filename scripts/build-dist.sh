#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -d frontend ]]; then
  (cd frontend && npm install && npm run build)
fi

mvn -q test package
mkdir -p target/dist
cp target/web-sim-*.jar target/dist/web-sim.jar
rm -rf target/dist/config
cp -R config target/dist/config
cp README.md target/dist/README.md

echo "Distribution written to target/dist"
