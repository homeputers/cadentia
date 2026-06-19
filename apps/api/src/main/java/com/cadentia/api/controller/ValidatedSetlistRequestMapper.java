package com.cadentia.api.controller;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import org.springframework.stereotype.Component;

@Component
public class ValidatedSetlistRequestMapper {

    public GenerateSetlistRequest toGenerateSetlistRequest(GenerateSetlistIntent intent) {
        GenerateSetlistSlots slots = intent.slots();
        return new GenerateSetlistRequest()
                .verseText(slots.verseText())
                .scriptureReferences(slots.scriptureReferences())
                .themeHints(slots.themeHints())
                .counts(new SetlistCounts()
                        .praise(slots.counts().praise())
                        .worship(slots.counts().worship()))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(slots.keyPolicy().preferSameKey())
                        .allowRelativeMajorMinor(slots.keyPolicy().allowRelativeMajorMinor())
                        .maxKeyCenters(slots.keyPolicy().maxKeyCenters()))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(slots.tempoPolicy().maxJumpBpm()))
                .language(slots.language())
                .energyArc(GenerateSetlistRequest.EnergyArcEnum.fromValue(slots.energyArc()))
                .excludedSongs(slots.excludedSongs())
                .serviceMoment(GenerateSetlistRequest.ServiceMomentEnum.fromValue(slots.serviceMoment()));
    }
}
