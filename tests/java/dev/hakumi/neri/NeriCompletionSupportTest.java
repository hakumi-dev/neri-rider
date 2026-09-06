package dev.hakumi.neri;

import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.TextRange;
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
        System.out.println("Neri completion replacement behavior passed.");
    }
}
