package dev.hakumi.neri;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.util.ui.FormBuilder;
import javax.swing.*;

public final class NeriSettingsEditor extends SettingsEditor<NeriRunConfiguration> {
    private final JTextField compiler = new JTextField();
    private final JTextField sources = new JTextField();
    private final JTextField directory = new JTextField();
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"run", "build", "check"});
    private final JTextField arguments = new JTextField();
    private final JTextField output = new JTextField();
    private final JCheckBox release = new JCheckBox("Release optimization");
    private final JPanel panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Neri executable:", compiler)
        .addLabeledComponent("Source files (quote paths with spaces):", sources)
        .addLabeledComponent("Working directory:", directory)
        .addLabeledComponent("Action:", mode)
        .addLabeledComponent("Program arguments (Run):", arguments)
        .addLabeledComponent("Output executable (Build):", output)
        .addComponent(release).getPanel();

    @Override protected JComponent createEditor() { return panel; }
    @Override protected void resetEditorFrom(NeriRunConfiguration config) {
        compiler.setText(config.compiler);
        sources.setText(config.sources);
        directory.setText(config.workingDirectory);
        mode.setSelectedItem(config.mode);
        arguments.setText(config.arguments);
        output.setText(config.output);
        release.setSelected(config.release);
    }
    @Override protected void applyEditorTo(NeriRunConfiguration config) {
        config.compiler = compiler.getText().trim();
        config.sources = sources.getText().trim();
        config.workingDirectory = directory.getText().trim();
        config.mode = (String) mode.getSelectedItem();
        config.arguments = arguments.getText();
        config.output = output.getText().trim();
        config.release = release.isSelected();
    }
}
