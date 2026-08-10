package com.cadentia.api.config;

import com.cadentia.generated.model.AssetAttachmentTargetType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AssetAttachmentTargetTypeConverter implements Converter<String, AssetAttachmentTargetType> {

    @Override
    public AssetAttachmentTargetType convert(String source) {
        return AssetAttachmentTargetType.fromValue(source);
    }
}
