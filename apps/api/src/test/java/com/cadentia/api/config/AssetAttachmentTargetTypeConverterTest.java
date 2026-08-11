package com.cadentia.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.generated.model.AssetAttachmentTargetType;
import org.junit.jupiter.api.Test;

class AssetAttachmentTargetTypeConverterTest {

    private final AssetAttachmentTargetTypeConverter converter = new AssetAttachmentTargetTypeConverter();

    @Test
    void convertsOpenApiLowercaseTargetTypeValues() {
        assertThat(converter.convert("song")).isEqualTo(AssetAttachmentTargetType.SONG);
        assertThat(converter.convert("arrangement")).isEqualTo(AssetAttachmentTargetType.ARRANGEMENT);
    }

    @Test
    void rejectsUnknownTargetTypeValues() {
        assertThatThrownBy(() -> converter.convert("SONG"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected value 'SONG'");
    }
}
