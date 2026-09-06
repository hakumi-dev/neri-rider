package dev.hakumi.neri;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;

public final class NeriPluginListener implements DynamicPluginListener {
    @Override
    public void pluginLoaded(IdeaPluginDescriptor descriptor) {
        if (descriptor.getPluginId().getIdString().equals("dev.hakumi.neri")) {
            NeriHighlighting.reload(null);
        }
    }
}
