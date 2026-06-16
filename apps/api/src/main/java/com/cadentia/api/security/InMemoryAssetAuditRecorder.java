package com.cadentia.api.security;

import com.cadentia.api.security.AssetAuthorizationPolicy.AssetAuditRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InMemoryAssetAuditRecorder implements AssetAuditRecorder {

    private final List<AssetAuditRecord> records = new ArrayList<>();

    @Override
    public synchronized void record(AssetAuditRecord record) {
        records.add(record);
    }

    public synchronized List<AssetAuditRecord> records() {
        return List.copyOf(records);
    }

    public synchronized void clear() {
        records.clear();
    }
}
