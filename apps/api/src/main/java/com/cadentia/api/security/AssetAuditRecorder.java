package com.cadentia.api.security;

import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuditRecord;

public interface AssetAuditRecorder {

    void record(AssetAuditRecord record);
}
