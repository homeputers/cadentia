package com.cadentia.asset;

public class AssetUploadException extends RuntimeException {

    private final AssetUploadErrorCode errorCode;

    public AssetUploadException(AssetUploadErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AssetUploadException(AssetUploadErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AssetUploadErrorCode errorCode() {
        return errorCode;
    }
}
