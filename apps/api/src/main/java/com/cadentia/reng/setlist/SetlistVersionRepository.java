package com.cadentia.reng.setlist;

import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SetlistVersionRepository {

    SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command);

    SetlistVersionSnapshot createEditedVersion(CreateSetlistVersionCommand command);

    Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId);

    List<SetlistVersionSnapshot> findVersions(UUID setlistId);
}
