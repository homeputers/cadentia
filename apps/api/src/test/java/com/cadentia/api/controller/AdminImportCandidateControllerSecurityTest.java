package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.cadentia.api.config.MethodSecurityConfig;
import com.cadentia.catalog.repository.InMemorySongRepository;
import com.cadentia.catalog.repository.SongRepository;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.ModerationFlagType;
import com.cadentia.generated.model.OpenModerationFlagRequest;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.AdminSongImportService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        AdminImportCandidateController.class,
        MethodSecurityConfig.class,
        AdminImportCandidateControllerSecurityTest.TestConfig.class
})
class AdminImportCandidateControllerSecurityTest {

    @Configuration
    static class TestConfig {
        @Bean
        SongRepository songRepository() {
            return new InMemorySongRepository();
        }

        @Bean
        AdminImportReviewService adminImportReviewService(SongRepository songRepository) {
            return new AdminImportReviewService(songRepository);
        }

        @Bean
        AdminSongImportService adminSongImportService() {
            return new AdminSongImportService(null, null);
        }
    }

    @Autowired
    private AdminReviewApi controller;

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openModerationFlagDeniesViewerAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "viewer",
                "n/a",
                List.of(new SimpleGrantedAuthority("catalog.admin.view"))));

        OpenModerationFlagRequest request = new OpenModerationFlagRequest()
                .type(ModerationFlagType.LICENSING_CONCERN)
                .openedBy("reviewer@example.test")
                .reason("Needs review")
                .excludeFromRecommendation(false);

        assertThatThrownBy(() -> controller.openAdminModerationFlag(UUID.randomUUID(), "\"candidate-version\"", request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void openModerationFlagAllowsReviewerAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reviewer",
                "n/a",
                List.of(new SimpleGrantedAuthority("catalog.admin.review"))));
        OpenModerationFlagRequest request = new OpenModerationFlagRequest()
                .type(ModerationFlagType.LICENSING_CONCERN)
                .openedBy("reviewer@example.test")
                .reason("Needs review")
                .excludeFromRecommendation(false);

        assertThatThrownBy(() -> controller.openAdminModerationFlag(UUID.randomUUID(), "\"candidate-version\"", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown import candidate");
    }
}
