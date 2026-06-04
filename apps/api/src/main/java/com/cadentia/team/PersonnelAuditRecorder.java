package com.cadentia.team;

import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import java.util.UUID;

public interface PersonnelAuditRecorder {

    UUID record(PersonnelAuditEvent event);
}
