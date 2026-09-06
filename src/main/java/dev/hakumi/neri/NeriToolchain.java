package dev.hakumi.neri;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;

final class NeriToolchain {
    static final String KEY = "neri.compiler";

    static String compiler(Project project) {
        String configured = PropertiesComponent.getInstance(project).getValue(KEY);
        if (configured != null && !configured.isBlank()) return configured;
        Path installed = Path.of(System.getProperty("user.home"), ".neri", "bin", "neri");
        return Files.isExecutable(installed) ? installed.toString() : "neri";
    }
}
