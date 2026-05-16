package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.TagType;
import java.time.Instant;
import java.util.UUID;

public record Tag(
        UUID id,
        TagType tagType,
        String name,
        String slug,
        String description,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
