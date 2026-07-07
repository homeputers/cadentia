package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.SearchProjectionEventReason;
import com.cadentia.search.ApprovedSearchModels.SearchProjectionInvalidation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SearchProjectionInvalidationService {

    public static final Duration REFRESH_TARGET = Duration.ofMinutes(5);

    private final Clock clock;
    private final List<SearchProjectionInvalidation> invalidations = new ArrayList<>();

    public SearchProjectionInvalidationService() {
        this(Clock.systemUTC());
    }

    public SearchProjectionInvalidationService(Clock clock) {
        this.clock = clock;
    }

    public SearchProjectionInvalidation recordEligibilityChange(UUID sourceEntityId, SearchProjectionEventReason reason) {
        SearchProjectionInvalidation invalidation = new SearchProjectionInvalidation(
                sourceEntityId,
                reason,
                Instant.now(clock).plus(REFRESH_TARGET));
        invalidations.add(invalidation);
        return invalidation;
    }

    public List<SearchProjectionInvalidation> pendingInvalidations() {
        return List.copyOf(invalidations);
    }
}
