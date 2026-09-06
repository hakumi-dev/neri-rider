package dev.hakumi.neri;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;

final class NeriToolchain {
    static final String KEY = "neri.compiler";
    static final String DOCUMENTATION_KEY = "neri.documentation";

    static boolean documentation(Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(DOCUMENTATION_KEY, true);
    }

    static String compiler(Project project) {
        String configured = PropertiesComponent.getInstance(project).getValue(KEY);
        if (configured != null && !configured.isBlank()) return configured;
        String executable = System.getProperty("os.name").startsWith("Windows") ? "neri.exe" : "neri";
        Path installed = Path.of(System.getProperty("user.home"), ".neri", "bin", executable);
        return Files.isExecutable(installed) ? installed.toString() : "neri";
    }
}
