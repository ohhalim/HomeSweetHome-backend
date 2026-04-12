#!/usr/bin/env sh

set -eu

GRAFANA_URL="${GRAFANA_URL:-http://grafana:3000}"
ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-admin}"
DASHBOARD_IDS="${GRAFANA_DASHBOARD_IDS:-11378,4701,7362}"
DATASOURCE_NAME="${GRAFANA_DASHBOARD_DATASOURCE:-Prometheus}"

echo "Waiting for Grafana at ${GRAFANA_URL}"
for i in $(seq 1 30); do
  if curl -sf "${GRAFANA_URL}/api/health" >/dev/null; then
    break
  fi
  sleep 2
done

if ! curl -sf "${GRAFANA_URL}/api/health" >/dev/null; then
  echo "Grafana is not ready after 60 seconds"
  exit 1
fi

echo "Grafana is ready. Bootstrap dashboards..."
for id in $(printf "%s" "${DASHBOARD_IDS}" | tr "," " "); do
  desired_uid=""
  dashboard_title=""
  case "$id" in
    11378)
      desired_uid="spring_boot_21"
      dashboard_title="Spring Boot 2.1 System Monitor"
      ;;
    4701)
      desired_uid="jvm_micrometer"
      dashboard_title="JVM (Micrometer)"
      ;;
    7362)
      desired_uid="mysql_overview"
      dashboard_title="MySQL Overview"
      ;;
  esac

  response="$(curl -fsS "https://grafana.com/api/dashboards/${id}/revisions/latest/download" \
    | jq -c \
      --arg ds "$DATASOURCE_NAME" \
      --arg uid "$desired_uid" \
      '{dashboard: (. | .id = null | .uid = $uid | .version = null | walk(if type == "string" then gsub("\\$\\{DS_PROMETHEUS\\}"; $ds) else . end)), overwrite: true}' \
    | curl -sS -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
      -H "Content-Type: application/json" \
      -X POST "${GRAFANA_URL}/api/dashboards/db" \
      --data-binary @-)"

  status="$(printf "%s" "$response" | jq -r '.status // "error"')"
  if [ "$status" != "success" ]; then
    echo "Dashboard ${id} import failed: ${response}"
    exit 1
  fi

  echo "Dashboard ${id} imported"

  if [ -n "$desired_uid" ] && [ -n "$dashboard_title" ]; then
    encoded_title="$(printf '%s' "$dashboard_title" | jq -r -sR @uri)"
    duplicates="$(curl -sf -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
      "${GRAFANA_URL}/api/search?type=dash-db&query=${encoded_title}" \
      | jq -r --arg uid "$desired_uid" '.[] | select(.uid != $uid) | .uid')"
    for old_uid in ${duplicates}; do
      echo "Removing duplicate dashboard uid=${old_uid} for title=${dashboard_title}"
      curl -sf -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
        -X DELETE "${GRAFANA_URL}/api/dashboards/uid/${old_uid}" >/dev/null
    done
  fi
done

echo "Grafana dashboards bootstrap complete"
