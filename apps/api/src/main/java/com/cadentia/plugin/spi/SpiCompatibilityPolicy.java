package com.cadentia.plugin.spi;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SpiCompatibilityPolicy {
    private static final Set<String> SUPPORTED = Set.of("1.0.0");
    private static final Set<String> DEPRECATED = Set.of("1.0.0-rc.1");

    public CompatibilityStatus status(String spiVersion) {
        if (SUPPORTED.contains(spiVersion)) {
            return CompatibilityStatus.SUPPORTED;
        }
        if (DEPRECATED.contains(spiVersion)) {
            return CompatibilityStatus.DEPRECATED;
        }
        return CompatibilityStatus.UNSUPPORTED;
    }

    public enum CompatibilityStatus {
        SUPPORTED,
        DEPRECATED,
        UNSUPPORTED
    }
}
