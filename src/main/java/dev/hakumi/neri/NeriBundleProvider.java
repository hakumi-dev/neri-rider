package dev.hakumi.neri;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;

import java.util.List;

public final class NeriBundleProvider implements TextMateBundleProvider {
    @Override
    public List<PluginBundle> getBundles() {
        var plugin = PluginManagerCore.getPlugin(PluginId.getId("dev.hakumi.neri"));
        if (plugin == null) {
            throw new IllegalStateException("Neri plugin descriptor is unavailable");
        }
        return List.of(new PluginBundle("Neri", plugin.getPluginPath().resolve("bundles/neri")));
    }
}
