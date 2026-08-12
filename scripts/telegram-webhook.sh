#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/telegram-webhook.sh set
  scripts/telegram-webhook.sh info
  scripts/telegram-webhook.sh delete

Required environment:
  CADENTIA_TELEGRAM_BOT_TOKEN       Telegram bot token from BotFather
  CADENTIA_TELEGRAM_WEBHOOK_SECRET  Secret sent by Telegram in X-Telegram-Bot-Api-Secret-Token

Webhook URL environment:
  CADENTIA_TELEGRAM_WEBHOOK_URL     Full webhook URL, preferred when set

or:
  CADENTIA_TELEGRAM_PUBLIC_BASE_URL Public HTTPS tunnel base URL
  CADENTIA_TELEGRAM_BOT_ID          Opaque local bot id, defaults to local

The script automatically loads .env first, then .env.local, when present.
USAGE
}

load_env_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$file"
    set +a
  fi
}

load_env_file ".env"
load_env_file ".env.local"

command="${1:-}"
if [[ -z "$command" || "$command" == "-h" || "$command" == "--help" ]]; then
  usage
  exit 0
fi

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

telegram_api() {
  local method="$1"
  echo "https://api.telegram.org/bot${CADENTIA_TELEGRAM_BOT_TOKEN}/${method}"
}

curl_with_config() {
  local config_file
  config_file="$(mktemp)"
  chmod 600 "$config_file"
  trap 'rm -f "$config_file"' RETURN

  "$@" > "$config_file"
  curl -sS --config "$config_file"
  rm -f "$config_file"
  trap - RETURN
}

webhook_url() {
  if [[ -n "${CADENTIA_TELEGRAM_WEBHOOK_URL:-}" ]]; then
    echo "$CADENTIA_TELEGRAM_WEBHOOK_URL"
    return
  fi

  require_env "CADENTIA_TELEGRAM_PUBLIC_BASE_URL"
  local base="${CADENTIA_TELEGRAM_PUBLIC_BASE_URL%/}"
  local bot_id="${CADENTIA_TELEGRAM_BOT_ID:-local}"
  echo "${base}/telegram/webhooks/${bot_id}"
}

case "$command" in
  set)
    require_env "CADENTIA_TELEGRAM_BOT_TOKEN"
    require_env "CADENTIA_TELEGRAM_WEBHOOK_SECRET"
    url="$(webhook_url)"
    curl_with_config printf '%s\n' \
      "url = \"$(telegram_api setWebhook)\"" \
      "request = \"POST\"" \
      "data = \"url=${url}\"" \
      "data = \"secret_token=${CADENTIA_TELEGRAM_WEBHOOK_SECRET}\"" \
      "data = \"drop_pending_updates=false\""
    echo
    ;;
  info)
    require_env "CADENTIA_TELEGRAM_BOT_TOKEN"
    curl_with_config printf '%s\n' \
      "url = \"$(telegram_api getWebhookInfo)\""
    echo
    ;;
  delete)
    require_env "CADENTIA_TELEGRAM_BOT_TOKEN"
    curl_with_config printf '%s\n' \
      "url = \"$(telegram_api deleteWebhook)\"" \
      "request = \"POST\"" \
      "data = \"drop_pending_updates=false\""
    echo
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
