CREATE TABLE telegram_webhook_update_acceptance (
    bot_id varchar(128) NOT NULL,
    channel_id varchar(128) NOT NULL,
    update_id bigint NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (bot_id, channel_id, update_id)
);

CREATE INDEX telegram_webhook_update_acceptance_accepted_at_idx
    ON telegram_webhook_update_acceptance (accepted_at);
