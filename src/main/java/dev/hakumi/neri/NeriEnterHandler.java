package dev.hakumi.neri;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.platform.lsp.api.LspClientManager;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;

import java.util.List;

/** Neri owns block syntax; this adapter keeps the caret on the new body line. */
public final class NeriEnterHandler implements EnterHandlerDelegate {
    private static final Logger LOG = Logger.getInstance(NeriEnterHandler.class);

    @Override public Result postProcessEnter(PsiFile file, Editor editor, DataContext context) {
        var project = file.getProject();
        var virtualFile = file.getVirtualFile();
        if (project.isDefault() || project.isDisposed() || editor.isDisposed() || virtualFile == null
                || !"hk".equals(virtualFile.getExtension()) || editor.isViewer()
                || editor.getCaretModel().getCaretCount() != 1 || editor.getSelectionModel().hasSelection()
                || LookupManager.getActiveLookup(editor) != null
                || TemplateManager.getInstance(project).getActiveTemplate(editor) != null
                || !CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return Result.Continue;
        Document document = editor.getDocument();
        Pending pending = Pending.capture(document, editor.getCaretModel().getOffset());
        if (pending == null) return Result.Continue;
        var indent = CodeStyle.getIndentOptions(file);
        for (var client : LspClientManager.getInstance(project).getClients(NeriLspIntegrationProvider.class)) {
            var initialized = client.getInitializeResult();
            var provider = initialized == null ? null
                    : initialized.getCapabilities().getDocumentOnTypeFormattingProvider();
            if (provider == null || !"\n".equals(provider.getFirstTriggerCharacter())
                    || !client.getDescriptor().isSupportedFile(virtualFile)) continue;
            var params = new DocumentOnTypeFormattingParams(client.getDocumentIdentifier(virtualFile),
                    new FormattingOptions(indent.INDENT_SIZE, !indent.USE_TAB_CHARACTER),
                    new Position(pending.line(), pending.caret() - pending.start()), "\n");
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    List<? extends TextEdit> edits = client.sendRequestSync(10000,
                            server -> server.getTextDocumentService().onTypeFormatting(params));
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed() || editor.isDisposed()
                                || editor.getCaretModel().getCaretCount() != 1
                                || editor.getSelectionModel().hasSelection()
                                || LookupManager.getActiveLookup(editor) != null
                                || TemplateManager.getInstance(project).getActiveTemplate(editor) != null) return;
                        WriteCommandAction.runWriteCommandAction(project, "Complete Neri block", null, () -> {
                            int caret = pending.apply(document, editor.getCaretModel().getOffset(), edits);
                            if (caret >= 0) editor.getCaretModel().moveToOffset(caret);
                        });
                    }, project.getDisposed());
                } catch (ProcessCanceledException canceled) {
                    throw canceled;
                } catch (Exception failure) {
                    // A stopped or busy server must leave ordinary Enter usable.
                    LOG.debug("Neri block completion was unavailable", failure);
                }
            });
            break;
        }
        return Result.Continue;
    }

    record Pending(long stamp, int caret, int line, int start, int end) {
        static Pending capture(Document document, int caret) {
            if (caret < 0 || caret > document.getTextLength()) return null;
            int line = document.getLineNumber(caret);
            if (line == 0) return null;
            int start = document.getLineStartOffset(line);
            int end = document.getLineEndOffset(line);
            for (int i = start; i < end; i++) {
                char ch = document.getCharsSequence().charAt(i);
                if (ch != ' ' && ch != '\t') return null;
            }
            return new Pending(document.getModificationStamp(), caret, line, start, end);
        }

        int apply(Document document, int currentCaret, List<? extends TextEdit> edits) {
            if (document.getModificationStamp() != stamp || currentCaret != caret
                    || edits == null || edits.size() != 1) return -1;
            var edit = edits.getFirst();
            var range = edit.getRange();
            // Enter edits own only the blank current line, never existing code.
            if (range.getStart().getLine() != line || range.getEnd().getLine() != line
                    || range.getStart().getCharacter() != 0
                    || range.getEnd().getCharacter() != end - start) return -1;
            String text = edit.getNewText();
            int bodyEnd = text.indexOf('\n');
            if (bodyEnd < 0) bodyEnd = text.length();
            if (bodyEnd > 0 && text.charAt(bodyEnd - 1) == '\r') bodyEnd--;
            for (int i = 0; i < bodyEnd; i++) {
                if (text.charAt(i) != ' ' && text.charAt(i) != '\t') return -1;
            }
            document.replaceString(start, end, text);
            return start + bodyEnd;
        }
    }
}
