package dev.hakumi.neri;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor;
import org.editorconfig.language.schema.parser.EditorConfigOptionDescriptorJsonDeserializer;

/** Describes Neri options to EditorConfig's completion and validation services. */
public final class NeriEditorConfigProvider implements EditorConfigOptionDescriptorProvider {
    private final List<EditorConfigOptionDescriptor> descriptors = loadDescriptors();

    private List<EditorConfigOptionDescriptor> loadDescriptors() {
        try (var resource = getClass().getClassLoader().getResourceAsStream("schemas/neri-editorconfig.json")) {
            if (resource == null) throw new IllegalStateException("Neri EditorConfig schema is missing");
            var schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            var gson = EditorConfigOptionDescriptorJsonDeserializer.Companion.buildGson(Logger.getInstance(NeriEditorConfigProvider.class));
            var result = Arrays.stream(gson.fromJson(schema, EditorConfigOptionDescriptor[].class)).toList();
            Logger.getInstance(NeriEditorConfigProvider.class).info("Loaded " + result.size() + " Neri EditorConfig options directly from plugin resources");
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Neri EditorConfig schema", exception);
        }
    }

    @Override public List<EditorConfigOptionDescriptor> getOptionDescriptors(Project project) {
        return descriptors;
    }

    @Override public boolean requiresFullSupport() {
        return false;
    }
}
