package com.cadentia.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class SpiCoreAdapterGuard {
    private final SpiContractValidator validator;
    private final SpiCompatibilityPolicy compatibilityPolicy;

    public SpiCoreAdapterGuard(SpiContractValidator validator, SpiCompatibilityPolicy compatibilityPolicy) {
        this.validator = validator;
        this.compatibilityPolicy = compatibilityPolicy;
    }

    public void validateBeforeInvocation(String extensionPoint, JsonNode inputPayload) {
        String spiVersion = inputPayload.path("envelope").path("spiVersion").asText();
        if (compatibilityPolicy.status(spiVersion) == SpiCompatibilityPolicy.CompatibilityStatus.UNSUPPORTED) {
            throw new SpiContractValidationException(java.util.List.of("unsupported SPI version: " + spiVersion));
        }
        validator.validateInput(extensionPoint, inputPayload);
    }

    public void validateBeforeUse(String extensionPoint, JsonNode outputPayload) {
        validator.validateOutput(extensionPoint, outputPayload);
    }
}
