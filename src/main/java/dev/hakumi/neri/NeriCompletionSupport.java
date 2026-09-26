package dev.hakumi.neri;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.platform.lsp.api.customization.LspCompletionSupport;
import com.intellij.platform.lsp.util.Lsp4jUtilKt;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;

final class NeriCompletionSupport extends LspCompletionSupport {
    static final NeriCompletionSupport INSTANCE = new NeriCompletionSupport();

    private NeriCompletionSupport() {}

    @Override public boolean shouldResolveCompletionItem(CompletionItem item) {
        // Complete declaration items keep this shape after their insertion format
        // is adapted. Resolving them would restore the SDK's snippet handler.
        return SnippetPlan.parseDeclaration(item) == null;
    }

    @Override public LookupElement createLookupElement(CompletionParameters parameters, CompletionItem item) {
        SnippetPlan snippet = item.getInsertTextFormat() == InsertTextFormat.Snippet
                ? SnippetPlan.parseDeclaration(item) : null;
        // Keep declaration completion from replacing Tab navigation while a
        // live template is editing its fields. Semantic completions still apply.
        if (snippet != null && TemplateManager.getInstance(parameters.getPosition().getProject())
                .getActiveTemplate(parameters.getEditor()) != null) return null;
        if (snippet != null) item.setInsertTextFormat(InsertTextFormat.PlainText);
        ReplacementPlan plan = ReplacementPlan.capture(parameters.getEditor().getDocument(), parameters.getOffset(), item);
        boolean opensArguments = opensEmptyArguments(item);
        LookupElement element = super.createLookupElement(parameters, item);
        if (element == null) return null;

        if (plan == null && snippet == null && !opensArguments) return element;

        return LookupElementDecorator.withDelegateInsertHandler(element, (context, delegate) -> {
            delegate.handleInsert(context);
            if (plan != null) plan.removeStaleSuffix(context.getDocument(), context.getStartOffset(), context.getTailOffset());
            if (snippet != null) {
                snippet.start(context.getProject(), context.getEditor(), context.getStartOffset(), context.getTailOffset());
            } else if (opensArguments) {
                Runnable previous = context.getLaterRunnable();
                context.setLaterRunnable(() -> {
                    if (previous != null) previous.run();
                    int caret = context.getEditor().getCaretModel().getOffset();
                    if (!context.getProject().isDisposed() && !context.getEditor().isDisposed()
                            && opensEmptyArguments(context.getDocument().getCharsSequence(), caret)) {
                        AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
                    }
                });
            }
        });
    }

    static boolean opensEmptyArguments(CompletionItem item) {
        String insertion = item.getTextEdit() == null ? item.getInsertText()
                : item.getTextEdit().map(TextEdit::getNewText, edit -> edit.getNewText());
        if (insertion == null) return false;
        return insertion.contains("()") || insertion.contains("($0)") || insertion.contains("(${0})");
    }

    static boolean opensEmptyArguments(CharSequence text, int caret) {
        return caret > 0 && caret < text.length() && text.charAt(caret - 1) == '(' && text.charAt(caret) == ')';
    }

    static final class SnippetPlan {
        private record Part(String text, int field, boolean end) {}
        private final List<Part> parts;

        private SnippetPlan(List<Part> parts) {
            this.parts = parts;
        }

        static SnippetPlan parseDeclaration(CompletionItem item) {
            if (item.getKind() != CompletionItemKind.Snippet || item.getData() != null
                    || item.getTextEdit() == null || !item.getTextEdit().isLeft()) return null;
            List<Part> parts = parse(StringUtilRt.convertLineSeparators(item.getTextEdit().getLeft().getNewText()));
            return parts == null ? null : new SnippetPlan(parts);
        }

        private static List<Part> parse(String snippet) {
            List<Part> parts = new ArrayList<>();
            StringBuilder literal = new StringBuilder();
            int expectedField = 1;
            boolean sawEnd = false;
            for (int index = 0; index < snippet.length();) {
                char current = snippet.charAt(index);
                if (current == '\\') {
                    if (index + 1 >= snippet.length()) return null;
                    char escaped = snippet.charAt(index + 1);
                    if (escaped != '\\' && escaped != '$' && escaped != '}') return null;
                    literal.append(escaped);
                    index += 2;
                    continue;
                }
                if (current != '$') {
                    literal.append(current);
                    index++;
                    continue;
                }
                flush(parts, literal);
                index++;
                if (index >= snippet.length() || !Character.isDigit(snippet.charAt(index))) {
                    if (index >= snippet.length() || snippet.charAt(index) != '{') return null;
                    index++;
                    int numberStart = index;
                    while (index < snippet.length() && Character.isDigit(snippet.charAt(index))) index++;
                    if (numberStart == index || snippet.charAt(numberStart) == '0') return null;
                    int field;
                    try { field = Integer.parseInt(snippet.substring(numberStart, index)); }
                    catch (NumberFormatException exception) { return null; }
                    if (index < snippet.length() && snippet.charAt(index) == '}') {
                        return null;
                    } else if (index < snippet.length() && snippet.charAt(index) == ':') {
                        if (field != expectedField++) return null;
                        index++;
                        StringBuilder value = new StringBuilder();
                        while (index < snippet.length() && snippet.charAt(index) != '}') {
                            if (snippet.charAt(index) == '\\') {
                                if (++index >= snippet.length()) return null;
                                char escaped = snippet.charAt(index);
                                if (escaped != '\\' && escaped != '$' && escaped != '}') return null;
                                value.append(escaped);
                            } else if (snippet.charAt(index) == '$') {
                                return null;
                            } else {
                                value.append(snippet.charAt(index));
                            }
                            index++;
                        }
                        if (index >= snippet.length()) return null;
                        parts.add(new Part(value.toString(), field, false));
                        index++;
                    } else return null;
                } else {
                    int numberStart = index;
                    while (index < snippet.length() && Character.isDigit(snippet.charAt(index))) index++;
                    if (sawEnd || !snippet.substring(numberStart, index).equals("0")) return null;
                    parts.add(new Part("", 0, true));
                    sawEnd = true;
                }
            }
            flush(parts, literal);
            return sawEnd ? parts : null;
        }

        private static void flush(List<Part> parts, StringBuilder literal) {
            if (literal.length() == 0) return;
            parts.add(new Part(literal.toString(), -1, false));
            literal.setLength(0);
        }

        Template build(TemplateManager manager) {
            Template template = manager.createTemplate("", "neri");
            populate(template);
            return template;
        }

        void populate(Template template) {
            for (Part part : parts) {
                if (part.field < 0) template.addTextSegment(part.text);
                else if (part.end) template.addEndVariable();
                else template.addVariable("NERI_" + part.field, new ConstantNode(part.text), true);
            }
        }

        void start(com.intellij.openapi.project.Project project, com.intellij.openapi.editor.Editor editor,
                   int start, int tail) {
            Document document = editor.getDocument();
            document.deleteString(start, tail);
            editor.getCaretModel().moveToOffset(start);
            TemplateManager manager = TemplateManager.getInstance(project);
            manager.runTemplate(editor, build(manager));
        }
    }

    static final class ReplacementPlan {
        private final int originalLength;
        private final int editStart;
        private final int caret;
        private final String insertedText;
        private final String suffix;

        private ReplacementPlan(int originalLength, int editStart, int caret, String insertedText, String suffix) {
            this.originalLength = originalLength;
            this.editStart = editStart;
            this.caret = caret;
            this.insertedText = insertedText;
            this.suffix = suffix;
        }

        static ReplacementPlan capture(Document document, int caret, CompletionItem item) {
            if (item.getTextEdit() == null || !item.getTextEdit().isLeft()
                    || item.getInsertTextFormat() == InsertTextFormat.Snippet) return null;

            TextRange range = Lsp4jUtilKt.getRangeInDocument(document, item.getTextEdit().getLeft().getRange());
            if (range == null || caret < range.getStartOffset() || caret > range.getEndOffset()) return null;

            String suffix = document.getCharsSequence().subSequence(caret, range.getEndOffset()).toString();
            if (suffix.isEmpty()) return null;
            String insertedText = StringUtilRt.convertLineSeparators(item.getTextEdit().getLeft().getNewText());
            return new ReplacementPlan(document.getTextLength(), range.getStartOffset(), caret, insertedText, suffix);
        }

        boolean removeStaleSuffix(Document document, int insertionStart, int insertionTail) {
            TextRange staleSuffix = staleSuffixRange(document.getCharsSequence(), insertionStart, insertionTail);
            if (staleSuffix == null) return false;
            document.deleteString(staleSuffix.getStartOffset(), staleSuffix.getEndOffset());
            return true;
        }

        TextRange staleSuffixRange(CharSequence text, int insertionStart, int insertionTail) {
            int expectedTail = editStart + insertedText.length();
            int prefixLength = caret - editStart;
            int expectedPrefixOnlyLength = originalLength - prefixLength + insertedText.length();
            if (insertionStart != editStart || insertionTail != expectedTail
                    || text.length() != expectedPrefixOnlyLength) return null;

            int suffixEnd = insertionTail + suffix.length();
            if (suffixEnd > text.length()
                    || !regionEquals(text, insertionStart, insertionTail, insertedText)
                    || !regionEquals(text, insertionTail, suffixEnd, suffix)) return null;
            return new TextRange(insertionTail, suffixEnd);
        }

        private static boolean regionEquals(CharSequence document, int start, int end, String expected) {
            if (end - start != expected.length()) return false;
            for (int i = 0; i < expected.length(); i++) {
                if (document.charAt(start + i) != expected.charAt(i)) return false;
            }
            return true;
        }
    }
}
