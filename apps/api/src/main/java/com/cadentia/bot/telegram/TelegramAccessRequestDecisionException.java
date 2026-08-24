package com.cadentia.bot.telegram;

public class TelegramAccessRequestDecisionException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        ALREADY_DECIDED
    }

    private final Reason reasonKind;

    public TelegramAccessRequestDecisionException(Reason reasonKind, String message) {
        super(message);
        this.reasonKind = reasonKind;
    }

    public Reason reasonKind() {
        return reasonKind;
    }
}
