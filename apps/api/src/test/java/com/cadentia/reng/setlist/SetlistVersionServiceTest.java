package com.cadentia.reng.setlist;

import static com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import static com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetlistVersionServiceTest {

    @Mock
    private SetlistVersionRepository repository;

    @Test
    void delegatesCreateBaseline() {
        SetlistVersionService service = new SetlistVersionService(repository);
        CreateSetlistBaselineCommand command = new CreateSetlistBaselineCommand(
                "planner",
                "profile",
                "engine",
                "{}",
                "{}",
                "[]",
                List.of(),
                "LINEAR");
        SetlistVersionSnapshot expected = snapshot(1);
        when(repository.createBaseline(command)).thenReturn(expected);

        SetlistVersionSnapshot actual = service.createBaseline(command);

        assertThat(actual).isEqualTo(expected);
        verify(repository).createBaseline(command);
    }

    @Test
    void delegatesCreateEditAndFinders() {
        SetlistVersionService service = new SetlistVersionService(repository);
        CreateSetlistVersionCommand editCommand = new CreateSetlistVersionCommand(
                UUID.randomUUID(), UUID.randomUUID(), "planner", "profile", "engine", "{}", "{}", "[]", "summary", List.of(), List.of());
        SetlistVersionSnapshot edited = snapshot(2);
        when(repository.createEditedVersion(editCommand)).thenReturn(edited);
        when(repository.findVersion(edited.setlistId(), edited.versionId())).thenReturn(Optional.of(edited));
        when(repository.findVersions(edited.setlistId())).thenReturn(List.of(snapshot(1), edited));

        assertThat(service.createEdit(editCommand)).isEqualTo(edited);
        assertThat(service.findVersion(edited.setlistId(), edited.versionId())).contains(edited);
        assertThat(service.listVersions(edited.setlistId())).hasSize(2);
        verify(repository).createEditedVersion(editCommand);
        verify(repository).findVersion(edited.setlistId(), edited.versionId());
        verify(repository).findVersions(edited.setlistId());
    }

    private static SetlistVersionSnapshot snapshot(int versionNumber) {
        UUID setlistId = UUID.randomUUID();
        return new SetlistVersionSnapshot(
                setlistId,
                UUID.randomUUID(),
                null,
                versionNumber,
                versionNumber == 1 ? "GENERATED_BASELINE" : "MANUAL_EDIT",
                "profile",
                "engine",
                Instant.now(),
                "planner",
                List.of(new SetlistVersionItemSnapshot(UUID.randomUUID(), 0, UUID.randomUUID(), null, null, null, "GENERATED", null)));
    }
}
