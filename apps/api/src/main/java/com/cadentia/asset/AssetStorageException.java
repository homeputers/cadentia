package com.cadentia.asset;

public class AssetStorageException extends RuntimeException {

    public AssetStorageException(String message) {
        super(message);
    }

    public AssetStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
