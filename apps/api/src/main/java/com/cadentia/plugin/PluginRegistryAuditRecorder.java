package com.cadentia.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PluginRegistryAuditRecorder {
    private final List<PluginRegistryAuditEvent> events = Collections.synchronizedList(new ArrayList<>());

    public void record(String action, UUID targetId, String actor) {
        events.add(new PluginRegistryAuditEvent(action, targetId, actor));
    }

    public List<PluginRegistryAuditEvent> events() {
        return List.copyOf(events);
    }

    public record PluginRegistryAuditEvent(String action, UUID targetId, String actor) {
    }
}
