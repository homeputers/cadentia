package com.cadentia.reng.setlist;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.RecommendationExplanation;
import com.cadentia.generated.model.RecommendationExplanationEntry;
import com.cadentia.generated.model.RecommendationExplanationSubject;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistItemCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SetlistProposalPersistenceService {
    private static final String CREATED_BY_RENG = "recommendation-engine";
    private static final String ENGINE_VERSION = "RecommendationEngine:recommendation_explanation.v1";

    private final SetlistVersionService setlistVersionService;
    private final ObjectMapper objectMapper;

    public SetlistProposalPersistenceService(SetlistVersionService setlistVersionService, ObjectMapper objectMapper) {
        this.setlistVersionService = setlistVersionService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public Optional<SetlistVersionSnapshot> persistGeneratedBaseline(
            GenerateSetlistRequest request,
            SetlistProposalResponse proposal) {
        if (proposal == null || proposal.getExplanation() == null) {
            return Optional.empty();
        }
        RecommendationExplanation explanation = proposal.getExplanation();
        List<CreateSetlistItemCommand> items = selectedItems(explanation);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        SetlistVersionSnapshot snapshot = setlistVersionService.createBaseline(new CreateSetlistBaselineCommand(
                CREATED_BY_RENG,
                explanation.getScoringProfileVersion(),
                ENGINE_VERSION,
                json(request),
                json(Map.of(
                        "intent", "GENERATE_SETLIST",
                        "source", "validated_request",
                        "requestId", nullToEmpty(proposal.getRequestId()),
                        "recommendationResultId", nullToEmpty(proposal.getRecommendationResultId()))),
                json(explanation),
                items,
                "LINEAR"));
        return Optional.of(snapshot);
    }

    private List<CreateSetlistItemCommand> selectedItems(RecommendationExplanation explanation) {
        if (explanation.getSelectedSongs() == null) {
            return List.of();
        }
        return explanation.getSelectedSongs().stream()
                .map(this::selectedItem)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(CreateSetlistItemCommand::positionIndex))
                .toList();
    }

    private Optional<CreateSetlistItemCommand> selectedItem(RecommendationExplanationEntry entry) {
        RecommendationExplanationSubject subject = entry.getSubject();
        if (subject == null || subject.getArrangementId() == null) {
            return Optional.empty();
        }
        return Optional.of(new CreateSetlistItemCommand(
                Math.max(0, position(entry) - 1),
                UUID.fromString(subject.getArrangementId()),
                null,
                null,
                null,
                "GENERATED",
                entry.getDefaultText()));
    }

    private int position(RecommendationExplanationEntry entry) {
        Object value = entry.getValues() == null ? null : entry.getValues().get("position");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (entry.getSubject() != null && entry.getSubject().getItemId() != null) {
            return Integer.parseInt(entry.getSubject().getItemId());
        }
        return 1;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize setlist proposal baseline payload.", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
