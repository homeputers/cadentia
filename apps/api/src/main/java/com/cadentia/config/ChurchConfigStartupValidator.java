package com.cadentia.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChurchConfigStartupValidator implements ApplicationRunner {
    private final ObjectMapper objectMapper;
    private final ChurchConfigPackageValidator validator;
    private final String packagePath;
    private final String applicationVersion;

    public ChurchConfigStartupValidator(
            ObjectMapper objectMapper,
            @Value("${cadentia.church-config.path:}") String packagePath,
            @Value("${cadentia.application.version:0.1.0}") String applicationVersion) {
        this.objectMapper = objectMapper;
        this.validator = new ChurchConfigPackageValidator();
        this.packagePath = packagePath;
        this.applicationVersion = applicationVersion;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!StringUtils.hasText(packagePath)) {
            return;
        }
        JsonNode root = objectMapper.readTree(Files.readString(Path.of(packagePath)));
        validator.validate(root, applicationVersion);
    }
}
