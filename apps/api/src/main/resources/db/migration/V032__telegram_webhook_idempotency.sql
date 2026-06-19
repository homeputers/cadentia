CREATE TABLE telegram_webhook_update_acceptance (
    bot_id varchar(128) NOT NULL,
    channel_id varchar(128) NOT NULL,
    update_id bigint NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (bot_id, channel_id, update_id)
);

CREATE INDEX telegram_webhook_update_acceptance_accepted_at_idx
    ON telegram_webhook_update_acceptance (accepted_at);

COMMENT ON TABLE telegram_webhook_update_acceptance IS
    'Durable Telegram webhook idempotency ledger keyed by bot, channel, and Telegram update ID.';

COMMENT ON COLUMN telegram_webhook_update_acceptance.bot_id IS
    'Cadentia Telegram bot identifier from the webhook route; not a bot token.';
COMMENT ON COLUMN telegram_webhook_update_acceptance.channel_id IS
    'Telegram chat/channel identifier derived from the update payload and stored for idempotency scope.';
COMMENT ON COLUMN telegram_webhook_update_acceptance.update_id IS
    'Telegram update_id accepted for the bot/channel scope.';
COMMENT ON COLUMN telegram_webhook_update_acceptance.accepted_at IS
    'UTC timestamp when Cadentia first accepted the update for asynchronous processing.';
