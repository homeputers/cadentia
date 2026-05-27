package com.cadentia.intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DefaultSessionMergeService implements SessionMergeService {

    private static final Counts DEFAULT_COUNTS = new Counts(10, 5);
    private static final IntentKeyPolicy DEFAULT_KEY_POLICY = new IntentKeyPolicy(true, true, 2);
    private static final IntentTempoPolicy DEFAULT_TEMPO_POLICY = new IntentTempoPolicy(12);

    @Override
    public SessionMergeResult merge(GenerateSetlistSlots baseline, SessionSlotUpdate update) {
        Map<String, Object> baseMap = toMap(baseline);
        Map<String, Object> updateMap = toMap(update.slots());
        Map<String, SlotValueSource> slotSources = new HashMap<>();
        List<SessionMergeEvent> events = new ArrayList<>();
        List<SessionMergeConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : updateMap.entrySet()) {
            String key = entry.getKey();
            Object attempted = entry.getValue();
            Object existing = baseMap.get(key);
            if (isEmptyValue(attempted)) {
                continue;
            }

            boolean apply = shouldApply(update.source(), existing, attempted, update.preferInferredValueOnConflict());
            if (apply) {
                if (!Objects.equals(existing, attempted)) {
                    events.add(new SessionMergeEvent(key, existing, attempted, update.source()));
                }
                baseMap.put(key, attempted);
                slotSources.put(key, update.source());
            } else if (!Objects.equals(existing, attempted)) {
                conflicts.add(new SessionMergeConflict(key, existing, slotSources.getOrDefault(key, SlotValueSource.DEFAULT), attempted));
            }
        }

        applyDefaults(baseMap, slotSources, events);
        return new SessionMergeResult(fromMap(baseMap), Map.copyOf(slotSources), List.copyOf(events), List.copyOf(conflicts));
    }

    private static boolean shouldApply(SlotValueSource source, Object existing, Object attempted, boolean preferInferred) {
        if (source == SlotValueSource.USER_EDIT) {
            return true;
        }
        if (source == SlotValueSource.MENU) {
            return true;
        }
        if (source == SlotValueSource.FREE_TEXT) {
            return isEmptyValue(existing) || preferInferred;
        }
        return isEmptyValue(existing) && !isEmptyValue(attempted);
    }

    private static boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static Map<String, Object> toMap(GenerateSetlistSlots slots) {
        Map<String, Object> map = new HashMap<>();
        map.put("verseText", slots.verseText());
        map.put("scriptureReferences", slots.scriptureReferences());
        map.put("themeHints", slots.themeHints());
        map.put("counts", slots.counts());
        map.put("keyPolicy", slots.keyPolicy());
        map.put("tempoPolicy", slots.tempoPolicy());
        map.put("language", slots.language());
        map.put("energyArc", slots.energyArc());
        map.put("excludedSongs", slots.excludedSongs());
        map.put("serviceMoment", slots.serviceMoment());
        return map;
    }

    private static GenerateSetlistSlots fromMap(Map<String, Object> map) {
        return new GenerateSetlistSlots(
                (String) map.get("verseText"),
                (List<String>) map.get("scriptureReferences"),
                (List<String>) map.get("themeHints"),
                (Counts) map.get("counts"),
                (IntentKeyPolicy) map.get("keyPolicy"),
                (IntentTempoPolicy) map.get("tempoPolicy"),
                (String) map.get("language"),
                (String) map.get("energyArc"),
                (List<String>) map.get("excludedSongs"),
                (String) map.get("serviceMoment"));
    }

    private static void applyDefaults(Map<String, Object> baseMap, Map<String, SlotValueSource> sources, List<SessionMergeEvent> events) {
        applyDefault(baseMap, sources, events, "counts", DEFAULT_COUNTS);
        applyDefault(baseMap, sources, events, "keyPolicy", DEFAULT_KEY_POLICY);
        applyDefault(baseMap, sources, events, "tempoPolicy", DEFAULT_TEMPO_POLICY);
    }

    private static void applyDefault(
            Map<String, Object> baseMap,
            Map<String, SlotValueSource> sources,
            List<SessionMergeEvent> events,
            String key,
            Object defaultValue) {
        if (baseMap.get(key) == null) {
            events.add(new SessionMergeEvent(key, null, defaultValue, SlotValueSource.DEFAULT));
            baseMap.put(key, defaultValue);
            sources.put(key, SlotValueSource.DEFAULT);
        }
    }
}
