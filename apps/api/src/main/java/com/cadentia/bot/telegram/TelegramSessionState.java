package com.cadentia.bot.telegram;

public enum TelegramSessionState {
    IDLE,
    NEW_SETLIST_ACTIVE,
    PENDING_CONFIRMATION,
    CANCELLED,
    COMPLETED,
    EXPIRED
}
