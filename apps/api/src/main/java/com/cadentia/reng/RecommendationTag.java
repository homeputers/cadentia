package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import java.util.UUID;

public record RecommendationTag(
        UUID id,
        TagType tagType,
        String name,
        String slug) {
}
