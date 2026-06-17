package com.cadentia.plugin.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.plugin.spi.SpiCompatibilityPolicy.CompatibilityStatus;
import org.junit.jupiter.api.Test;

class SpiCompatibilityPolicyTest {
    private final SpiCompatibilityPolicy policy = new SpiCompatibilityPolicy();

    @Test
    void classifiesSupportedDeprecatedAndUnsupportedVersions() {
        assertThat(policy.status("1.0.0")).isEqualTo(CompatibilityStatus.SUPPORTED);
        assertThat(policy.status("1.0.0-rc.1")).isEqualTo(CompatibilityStatus.DEPRECATED);
        assertThat(policy.status("2.0.0")).isEqualTo(CompatibilityStatus.UNSUPPORTED);
    }
}
