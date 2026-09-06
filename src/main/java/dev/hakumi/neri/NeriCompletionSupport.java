package dev.hakumi.neri;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.platform.lsp.api.customization.LspCompletionSupport;
import com.intellij.platform.lsp.util.Lsp4jUtilKt;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;

final class NeriCompletionSupport extends LspCompletionSupport {
    static final NeriCompletionSupport INSTANCE = new NeriCompletionSupport();

    private NeriCompletionSupport() {}

    @Override public LookupElement createLookupElement(CompletionParameters parameters, CompletionItem item) {
        LookupElement element = super.createLookupElement(parameters, item);
        if (element == null) return null;

        ReplacementPlan plan = ReplacementPlan.capture(parameters.getEditor().getDocument(), parameters.getOffset(), item);
        if (plan == null) return element;

        return LookupElementDecorator.withDelegateInsertHandler(element, (context, delegate) -> {
            delegate.handleInsert(context);
            plan.removeStaleSuffix(context.getDocument(), context.getStartOffset(), context.getTailOffset());
        });
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
