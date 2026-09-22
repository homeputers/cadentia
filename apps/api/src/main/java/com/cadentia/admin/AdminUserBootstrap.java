package com.cadentia.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates the first tenant administrator only when deployment explicitly supplies a stable IdP subject. */
@Component
@ConditionalOnProperty(name = "cadentia.auth.bootstrap-admin-subject")
public class AdminUserBootstrap implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final String instanceId;
    private final String subject;

    public AdminUserBootstrap(
            AdminUserRepository repository,
            @Value("${cadentia.instance.id}") String instanceId,
            @Value("${cadentia.auth.bootstrap-admin-subject}") String subject) {
        this.repository = repository;
        this.instanceId = instanceId;
        this.subject = subject;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.findByExternalSubject(instanceId, subject).isEmpty()) {
            repository.create(instanceId, subject, "Cadentia administrator", null, List.of("ADMIN"));
        }
    }
}
