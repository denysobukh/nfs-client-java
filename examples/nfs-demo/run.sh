#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build/reports/nfs-demo
save_server_report() {
    docker compose -f examples/nfs-demo/compose.yaml ps -a > build/reports/nfs-demo/server-status.txt || true
    docker compose -f examples/nfs-demo/compose.yaml logs --no-color > build/reports/nfs-demo/server.log || true
}
trap save_server_report EXIT
./gradlew --console=plain stageNfsDemo
docker compose -f examples/nfs-demo/compose.yaml up --build --wait --wait-timeout 60
docker compose -f examples/nfs-demo/compose.yaml run --build --rm client "$@" 2>&1 | tee build/reports/nfs-demo/client-report.txt
