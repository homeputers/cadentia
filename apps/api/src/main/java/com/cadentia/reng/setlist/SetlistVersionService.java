package com.cadentia.reng.setlist;

import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SetlistVersionService {

    private final SetlistVersionRepository repository;

    public SetlistVersionService(SetlistVersionRepository repository) {
        this.repository = repository;
    }

    public SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command) {
        return repository.createBaseline(command);
    }

    public SetlistVersionSnapshot createEdit(CreateSetlistVersionCommand command) {
        return repository.createEditedVersion(command);
    }

    public Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId) {
        return repository.findVersion(setlistId, versionId);
    }

    public List<SetlistVersionSnapshot> listVersions(UUID setlistId) {
        return repository.findVersions(setlistId);
    }
}
