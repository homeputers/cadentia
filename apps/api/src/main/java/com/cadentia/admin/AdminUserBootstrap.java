package com.cadentia.admin;

import com.cadentia.api.auth.FirstPartyAuthClient;
import com.cadentia.api.auth.FirstPartyAuthUser;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the first tenant administrator from either a provider subject or a first-party email. */
@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final ObjectProvider<FirstPartyAuthClient> firstPartyAuthClient;
    private final String instanceId;
    private final String authProvider;
    private final String bootstrapSubject;
    private final String bootstrapEmail;

    public AdminUserBootstrap(
            AdminUserRepository repository,
            ObjectProvider<FirstPartyAuthClient> firstPartyAuthClient,
            @Value("${cadentia.instance.id}") String instanceId,
            @Value("${cadentia.auth.provider:local}") String authProvider,
            @Value("${cadentia.auth.bootstrap-admin-subject:}") String bootstrapSubject,
            @Value("${cadentia.auth.bootstrap-admin-email:}") String bootstrapEmail) {
        this.repository = repository;
        this.firstPartyAuthClient = firstPartyAuthClient;
        this.instanceId = instanceId;
        this.authProvider = authProvider;
        this.bootstrapSubject = bootstrapSubject;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        String subject = bootstrapSubject;
        String displayName = "Cadentia administrator";
        String email = null;
        if ("first-party".equals(authProvider)) {
            if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
                return;
            }
            FirstPartyAuthClient authClient = firstPartyAuthClient.getIfAvailable();
            if (authClient == null) {
                return;
            }
            FirstPartyAuthUser authUser = authClient.findByEmail(bootstrapEmail);
            subject = authUser.userId().toString();
            displayName = authUser.displayName();
            email = authUser.email();
        } else if (subject == null || subject.isBlank()) {
            return;
        }
        if (repository.findByExternalSubject(instanceId, subject).isEmpty()) {
            repository.create(instanceId, subject, displayName, email, List.of("ADMIN"));
        }
    }
}
