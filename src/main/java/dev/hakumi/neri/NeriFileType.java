package dev.hakumi.neri;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.plugins.textmate.TextMateBackedFileType;
import org.jetbrains.plugins.textmate.TextMateLanguage;

import javax.swing.Icon;

public final class NeriFileType extends LanguageFileType implements TextMateBackedFileType {
    public static final NeriFileType INSTANCE = new NeriFileType();

    private NeriFileType() {
        super(TextMateLanguage.LANGUAGE, true);
    }

    @Override public String getName() { return "Neri"; }
    @Override public String getDescription() { return "Neri source file"; }
    @Override public String getDefaultExtension() { return "hk"; }
    @Override public Icon getIcon() { return AllIcons.FileTypes.Text; }
}
