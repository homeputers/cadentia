package com.cadentia.reng;

import java.util.List;

public record CandidateSearchCriteria(
        String language,
        List<String> allowedKeys,
        Integer minBpm,
        Integer maxBpm,
        List<String> requiredTags) {
}
