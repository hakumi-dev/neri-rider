package dev.hakumi.neri;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

public final class NeriConfigurationProducer extends LazyRunConfigurationProducer<NeriRunConfiguration> {
    @Override public ConfigurationFactory getConfigurationFactory() { return NeriConfigurationType.factory(); }

    private VirtualFile source(ConfigurationContext context) {
        var location = context.getLocation();
        VirtualFile file = location == null ? null : location.getVirtualFile();
        return file != null && "hk".equals(file.getExtension()) ? file : null;
    }

    @Override protected boolean setupConfigurationFromContext(NeriRunConfiguration config,
            ConfigurationContext context, Ref<PsiElement> element) {
        VirtualFile file = source(context);
        if (file == null) return false;
        config.sources = "\"" + file.getPath().replace("\"", "\\\"") + "\"";
        config.workingDirectory = file.getParent().getPath();
        config.setName(file.getNameWithoutExtension());
        return true;
    }

    @Override public boolean isConfigurationFromContext(NeriRunConfiguration config, ConfigurationContext context) {
        VirtualFile file = source(context);
        return file != null && config.mode.equals("run") &&
            com.intellij.util.execution.ParametersListUtil.parse(config.sources).equals(java.util.List.of(file.getPath()));
    }
}
