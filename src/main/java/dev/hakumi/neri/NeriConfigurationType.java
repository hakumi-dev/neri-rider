package dev.hakumi.neri;

import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;

public final class NeriConfigurationType extends ConfigurationTypeBase {
    public NeriConfigurationType() {
        super("Neri", "Neri", "Build, check or run a Neri program", AllIcons.Actions.Execute);
        addFactory(new ConfigurationFactory(this) {
            @Override public String getId() { return "Neri"; }
            @Override public RunConfiguration createTemplateConfiguration(Project project) {
                return new NeriRunConfiguration(project, this, "Neri");
            }
        });
    }

    public static ConfigurationFactory factory() {
        return ConfigurationTypeUtil.findConfigurationType(NeriConfigurationType.class).getConfigurationFactories()[0];
    }
}
