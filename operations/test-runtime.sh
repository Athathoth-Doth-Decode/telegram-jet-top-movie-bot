#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: test-runtime.sh APP_IMAGE POSTGRES_IMAGE}
postgres_image=${2:?PostgreSQL image is required}
engine=${CONTAINER_ENGINE:-docker}
network="jet-smoke-${RANDOM}-$$"
database="${network}-db"
app="${network}-app"

cleanup() {
  status=$?
  if (( status != 0 )); then
    "$engine" logs "$app" 2>&1 || true
  fi
  "$engine" rm -fv "$app" "$database" >/dev/null 2>&1 || true
  "$engine" network rm "$network" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

# Run the application against an isolated PostgreSQL database with fake credentials.
# The internal network prevents all Telegram polling and external API requests.
# Starting twice proves that migrations complete and existing application data survives.
"$engine" network create --internal "$network" >/dev/null
"$engine" run -d --pull=never --name "$database" --network "$network" \
  -e POSTGRES_DB=jet_smoke -e POSTGRES_USER=jet_smoke \
  -e POSTGRES_PASSWORD=runtime-test-only "$postgres_image" >/dev/null
for attempt in {1..30}; do
  if "$engine" exec "$database" pg_isready -U jet_smoke -d jet_smoke >/dev/null; then
    break
  fi
  sleep 1
done
"$engine" exec "$database" pg_isready -U jet_smoke -d jet_smoke >/dev/null

"$engine" run -d --pull=never --name "$app" --network "$network" \
  --read-only --tmpfs /tmp:rw,nosuid,nodev,size=128m \
  --cap-drop ALL --security-opt no-new-privileges --memory 768m \
  -e TELEGRAM_BOT_ENABLED=false -e TELEGRAM_BOT_TOKEN=123:test-only \
  -e TELEGRAM_BOT_NAME=runtime-test -e KINOPOISK_TOKEN=test-only \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${database}:5432/jet_smoke" \
  -e SPRING_DATASOURCE_USERNAME=jet_smoke -e SPRING_DATASOURCE_PASSWORD=runtime-test-only \
  "$image" >/dev/null

health() {
  "$engine" exec "$app" curl --noproxy '*' --fail --silent --show-error \
    --retry 30 --retry-connrefused --retry-delay 2 --max-time 3 \
    http://127.0.0.1:8080/actuator/health
}
test "$("$engine" exec "$app" id -u)" = 1000
health
test "$("$engine" exec "$database" psql -U jet_smoke -d jet_smoke -Atc \
  "select count(*) from flyway_schema_history where version='1' and success")" = 1
"$engine" exec "$database" psql -U jet_smoke -d jet_smoke -v ON_ERROR_STOP=1 -c \
  'insert into will_view(user_id,movie_id) values (5000000007,123)' >/dev/null
"$engine" restart "$app" >/dev/null
health
test "$("$engine" exec "$database" psql -U jet_smoke -d jet_smoke -Atc \
  'select movie_id from will_view where user_id=5000000007')" = 123
test "$("$engine" exec "$database" psql -U jet_smoke -d jet_smoke -Atc \
  "select count(*) from flyway_schema_history where version='1' and success")" = 1
printf '\nNon-root startup, PostgreSQL migration and restart checks passed.\n'
