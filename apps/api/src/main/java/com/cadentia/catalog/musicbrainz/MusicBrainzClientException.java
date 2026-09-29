package com.cadentia.catalog.musicbrainz;

public class MusicBrainzClientException extends RuntimeException {
    private final int status;

    public MusicBrainzClientException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public MusicBrainzClientException(int status, String message) {
        this(status, message, null);
    }

    public int status() {
        return status;
    }
}
