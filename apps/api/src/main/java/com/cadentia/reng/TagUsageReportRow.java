package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import java.util.UUID;

public record TagUsageReportRow(
        UUID tagId,
        TagType tagType,
        String slug,
        String name,
        int arrangementCount) {
}
