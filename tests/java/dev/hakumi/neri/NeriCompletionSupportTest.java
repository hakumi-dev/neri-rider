package dev.hakumi.neri;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public final class NeriCompletionSupportTest {
    private static DocumentImpl document(String text) {
        return new DocumentImpl(text, true);
    }

    private static CompletionItem replacement(int start, int end, String text) {
        CompletionItem item = new CompletionItem(text);
        item.setTextEdit(Either.forLeft(new TextEdit(
                new Range(new Position(0, start), new Position(0, end)), text)));
        return item;
    }

    private static NeriCompletionSupport.ReplacementPlan plan(DocumentImpl document, int caret, int start, int end, String text) {
        var result = NeriCompletionSupport.ReplacementPlan.capture(document, caret, replacement(start, end, text));
        if (result == null) throw new AssertionError("Expected a replacement plan");
        return result;
    }

    private static boolean removeStaleSuffix(NeriCompletionSupport.ReplacementPlan plan, StringBuilder text,
                                             int insertionStart, int insertionTail) {
        TextRange suffix = plan.staleSuffixRange(text, insertionStart, insertionTail);
        if (suffix == null) return false;
        text.delete(suffix.getStartOffset(), suffix.getEndOffset());
        return true;
    }

    private static void riderSdkRendersStructuredSnippets() {
        String snippet = "def ${1:name}(${2:value: Int}): ${3:Void}\n"
                + "\t$0text = \\$0 + \\${1:fake} + C:\\\\temp + ${4:a\\}b\\$c\\\\d}\nend";
        CompletionItem item = replacement(0, 0, snippet);
        item.setInsertTextFormat(org.eclipse.lsp4j.InsertTextFormat.Snippet);
        item.setKind(org.eclipse.lsp4j.CompletionItemKind.Snippet);
        var plan = NeriCompletionSupport.SnippetPlan.parseDeclaration(item);

        if (plan == null) throw new AssertionError("Expected a structured declaration snippet");
        if (NeriCompletionSupport.INSTANCE.shouldResolveCompletionItem(item)) {
            throw new AssertionError("Complete declaration snippets must retain their insertion adapter");
        }
        item.setInsertTextFormat(org.eclipse.lsp4j.InsertTextFormat.PlainText);

        if (NeriCompletionSupport.INSTANCE.shouldResolveCompletionItem(item)) {
            throw new AssertionError("SDK adaptation must not allow resolve to restore snippet conversion");
        }
        var template = new TemplateImpl("", "neri");
        plan.populate(template);
        Rendered rendered = renderInitialTemplate(template);
        String expected = "def name(value: Int): Void\n\ttext = $0 + ${1:fake} + C:\\temp + a}b$c\\d\nend";

        if (!rendered.text.equals(expected)) {
            throw new AssertionError("Final template text differs: " + rendered.text);
        }
        if (rendered.caret != "def name(value: Int): Void\n\t".length()) {
            throw new AssertionError("Final caret must retain its position before later editable fields");
        }
        if (template.getVariables().size() != 4) {
            throw new AssertionError("Escaped placeholder-like text must remain literal");
        }
        for (int index = 0; index < template.getVariables().size(); index++) {
            if (!template.getVariables().get(index).getName().equals("NERI_" + (index + 1))) {
                throw new AssertionError("Editable fields must follow their numeric order");
            }
        }
        item.setData("semantic-resolution");

        if (!NeriCompletionSupport.INSTANCE.shouldResolveCompletionItem(item)) {
            throw new AssertionError("Semantic completion resolution must remain available");
        }
    }

    private record Rendered(String text, int caret) {}

    private static Rendered renderInitialTemplate(TemplateImpl template) {
        StringBuilder result = new StringBuilder();
        String text = template.getTemplateText();
        int previous = 0;
        int caret = -1;
        for (int index = 0; index < template.getSegmentsCount(); index++) {
            int offset = template.getSegmentOffset(index);
            result.append(text, previous, offset);
            String name = template.getSegmentName(index);
            if (name.equals("END")) {
                caret = result.length();
            } else {
                for (var variable : template.getVariables()) {
                    if (variable.getName().equals(name)) {
                        result.append(variable.getDefaultValueExpression().calculateQuickResult(null));
                    }
                }
            }
            previous = offset;
        }
        return new Rendered(result.append(text, previous, text.length()).toString(), caret);
    }

    private static void plainTextFallbackRemainsLiteral() {
        var original = document("defx");
        CompletionItem item = replacement(0, 4, "def $value(C:\\temp)");
        item.setInsertTextFormat(org.eclipse.lsp4j.InsertTextFormat.PlainText);
        var plan = NeriCompletionSupport.ReplacementPlan.capture(original, 3, item);
        if (plan == null || !item.getTextEdit().getLeft().getNewText().equals("def $value(C:\\temp)")) {
            throw new AssertionError("Plain-text completion must preserve literal snippet characters");
        }
    }

    private static void callableCompletionOpensItsArgumentCompletion() {
        CompletionItem callable = replacement(0, 1, "active($0)");
        callable.setInsertTextFormat(org.eclipse.lsp4j.InsertTextFormat.Snippet);

        if (!NeriCompletionSupport.opensEmptyArguments(callable)
                || !NeriCompletionSupport.opensEmptyArguments("active()", 7)) {
            throw new AssertionError("A returned empty-argument snippet and its final caret must request completion");
        }
        if (NeriCompletionSupport.opensEmptyArguments(replacement(0, 1, "active"))) {
            throw new AssertionError("A completion that retains existing arguments must not request completion");
        }
        if (NeriCompletionSupport.opensEmptyArguments("active(value)", 7)) {
            throw new AssertionError("Non-empty arguments must not request completion");
        }
    }

    private static void enterReplacesTheWholeIdentifier() {
        var original = document("let message = greeting(\"Neri\")");
        var plan = plan(original, 17, 14, 22, "greeting");
        var inserted = new StringBuilder(original.getCharsSequence());
        inserted.replace(14, 17, "greeting"); // Rider's prefix-only Enter insertion.
        if (!removeStaleSuffix(plan, inserted, 14, 22)
                || !inserted.toString().equals("let message = greeting(\"Neri\")")) {
            throw new AssertionError("Enter must honor the complete LSP TextEdit range: " + inserted);
        }
    }

    private static void anExistingReplaceIsNotAppliedTwice() {
        var original = document("greetingeting!");
        var plan = plan(original, 3, 0, 8, "greeting");
        var inserted = new StringBuilder(original.getCharsSequence());
        inserted.replace(0, 8, "greeting"); // Tab already replaced the full identifier.
        if (removeStaleSuffix(plan, inserted, 0, 8) || !inserted.toString().equals("greetingeting!")) {
            throw new AssertionError("A completed replace must not delete identical following text: " + inserted);
        }
    }

    private static void lspUtf16OffsetsArePreserved() {
        var original = document("🙂 greeting(\"Neri\")");
        var plan = plan(original, 6, 3, 11, "greeting");
        var inserted = new StringBuilder(original.getCharsSequence());
        inserted.replace(3, 6, "greeting");
        if (!removeStaleSuffix(plan, inserted, 3, 11)
                || !inserted.toString().equals("🙂 greeting(\"Neri\")")) {
            throw new AssertionError("UTF-16 replacement offsets were not preserved: " + inserted);
        }
    }

    public static void main(String[] args) {
        enterReplacesTheWholeIdentifier();
        anExistingReplaceIsNotAppliedTwice();
        lspUtf16OffsetsArePreserved();
        riderSdkRendersStructuredSnippets();
        plainTextFallbackRemainsLiteral();
        callableCompletionOpensItsArgumentCompletion();
        System.out.println("Neri completion replacement behavior passed.");
    }
}
