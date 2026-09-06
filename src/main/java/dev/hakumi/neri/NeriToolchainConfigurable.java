package dev.hakumi.neri;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

public final class NeriToolchainConfigurable implements Configurable {
    private final Project project;
    private JTextField compiler;

    public NeriToolchainConfigurable(Project project) { this.project = project; }

    @Override public String getDisplayName() { return "Neri"; }

    @Override public JComponent createComponent() {
        compiler = new JTextField(NeriToolchain.compiler(project), 40);
        return FormBuilder.createFormBuilder().addLabeledComponent("Compiler executable:", compiler)
                .addComponent(new JLabel("Use a Neri toolchain launcher supporting 'neri lsp'."))
                .addComponent(new JLabel("Used by live diagnostics and new Run/Build/Check configurations."))
                .getPanel();
    }

    @Override public boolean isModified() {
        return compiler != null && !compiler.getText().trim().equals(NeriToolchain.compiler(project));
    }

    @Override public void apply() throws ConfigurationException {
        String value = compiler.getText().trim();
        if (value.isEmpty()) throw new ConfigurationException("Select a Neri compiler executable.");
        PropertiesComponent.getInstance(project).setValue(NeriToolchain.KEY, value);
        if (!project.isDefault()) {
            LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(NeriLspIntegrationProvider.class);
            for (var file : com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getOpenFiles()) {
                NeriLspIntegrationProvider.ensureStarted(project, file);
            }
        }
    }

    @Override public void reset() {
        if (compiler != null) compiler.setText(NeriToolchain.compiler(project));
    }

    @Override public void disposeUIResources() { compiler = null; }
}
