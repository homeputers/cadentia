package com.cadentia.team;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import org.springframework.stereotype.Component;

@Component
public class PersonnelDataRedactor {

    private final PersonnelAuthorizationPolicy authorizationPolicy;

    public PersonnelDataRedactor(PersonnelAuthorizationPolicy authorizationPolicy) {
        this.authorizationPolicy = authorizationPolicy;
    }

    public MusicianRecord redact(MusicianRecord musician) {
        if (musician == null) {
            return null;
        }
        boolean canReadContact = authorizationPolicy.canReadPrivateContactData(musician);
        boolean canReadSkillRange = authorizationPolicy.canReadSensitiveSkillAndRangeData(musician);
        return new MusicianRecord(
                musician.musicianId(),
                musician.displayName(),
                canReadContact ? musician.accountPrincipal() : null,
                canReadContact ? musician.email() : null,
                canReadContact ? musician.phone() : null,
                canReadSkillRange ? musician.primaryVocalRangeCode() : null,
                canReadSkillRange ? musician.comfortableLowMidiNote() : null,
                canReadSkillRange ? musician.comfortableHighMidiNote() : null,
                canReadSkillRange ? musician.servingPreferenceCode() : null,
                musician.active());
    }
}
