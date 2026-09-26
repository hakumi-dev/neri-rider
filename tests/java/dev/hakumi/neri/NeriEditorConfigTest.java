package dev.hakumi.neri;

import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.editorconfig.common.plugin.EditorConfigParserDefinition;
import com.intellij.editorconfig.common.syntax.lexer.EditorConfigLexerAdapter;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedOptionKey;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigQualifiedKeyPart;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilderFactory;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigListDescriptor;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor;

public final class NeriEditorConfigTest {
    private static List<PsiElement> valueIdentifiers(ASTNode node, EditorConfigParserDefinition definition) {
        var identifiers = new ArrayList<PsiElement>();
        if (node.getElementType().toString().equals("OPTION_VALUE_IDENTIFIER")) identifiers.add(definition.createElement(node));
        if (node.getElementType().toString().equals("ERROR_ELEMENT")) throw new AssertionError("Invalid EditorConfig syntax: " + node.getText());
        for (var child = node.getFirstChildNode(); child != null; child = child.getTreeNext())
            identifiers.addAll(valueIdentifiers(child, definition));
        return identifiers;
    }

    private static List<PsiElement> parseValueIdentifiers(String value, CoreProjectEnvironment environment) {
        var definition = new EditorConfigParserDefinition();
        var builder = PsiBuilderFactory.getInstance().createBuilder(definition, new EditorConfigLexerAdapter(),
                "[*.hk]\nneri_assertion_helpers = " + value + "\n");
        return valueIdentifiers(definition.createParser(environment.getProject()).parse(definition.getFileNodeType(), builder), definition);
    }

    private static <T> T element(Class<T> type, String text) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getText", "toString" -> text;
                case "getTextLength" -> text.length();
                case "getQualifiedKeyPartList" -> Arrays.stream(text.split("\\.")).map(part -> element(EditorConfigQualifiedKeyPart.class, part)).toList();
                case "textMatches" -> text.contentEquals(args[0] instanceof PsiElement psi ? psi.getText() : (CharSequence) args[0]);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }));
    }

    public static void main(String[] args) throws Exception {
        var provider = new NeriEditorConfigProvider();
        var descriptors = provider.getOptionDescriptors(null);
        for (var property : List.of("neri_indentation", "neri_token_spacing")) {
            var descriptor = descriptors.stream().filter(item -> item.getKey().matches(element(EditorConfigFlatOptionKey.class, property))).findFirst().orElseThrow();

            if (!descriptor.getValue().matches(element(EditorConfigOptionValueIdentifier.class, "false"))) {
                throw new AssertionError("Layout rules must be configurable from EditorConfig");
            }
        }
        var key = element(EditorConfigFlatOptionKey.class, "neri_blank_line_after_declarations");
        var option = descriptors.stream().filter(item -> item.getKey().matches(key)).findFirst().orElseThrow();
        if (!option.getValue().matches(element(EditorConfigOptionValueIdentifier.class, "true"))
                || option.getValue().matches(element(EditorConfigOptionValueIdentifier.class, "sometimes"))
                || descriptors.stream().anyMatch(item -> item.getKey().matches(element(EditorConfigFlatOptionKey.class, "neri_typo")))) {
            throw new AssertionError("EditorConfig must recognize Neri properties and reject invalid boolean values and unknown keys");
        }
        var severityKey = element(EditorConfigQualifiedOptionKey.class, "neri_diagnostic.NRSTYLE999.severity");
        var severity = descriptors.stream().filter(item -> item.getKey().matches(severityKey)).findFirst().orElseThrow();
        var helperKey = element(EditorConfigFlatOptionKey.class, "neri_assertion_helpers");
        var helpers = descriptors.stream().filter(item -> item.getKey().matches(helperKey)).findFirst().orElseThrow();
        if (!severity.getValue().matches(element(EditorConfigOptionValueIdentifier.class, "warning"))
                || severity.getValue().matches(element(EditorConfigOptionValueIdentifier.class, "fatal"))
                || !helpers.getValue().matches(element(EditorConfigOptionValueList.class, "test/assert*, assert"))
                || !(helpers.getValue() instanceof EditorConfigUnionDescriptor union)
                // Every free-form entry resolves to the same text descriptor, so the uniqueness inspection must allow repetitions.
                || union.getChildren().stream().filter(EditorConfigListDescriptor.class::isInstance)
                    .map(EditorConfigListDescriptor.class::cast).noneMatch(EditorConfigListDescriptor::getAllowRepetitions)) {
            throw new AssertionError("EditorConfig must support extensible diagnostic identifiers and assertion helper lists");
        }
        var disposable = Disposer.newDisposable();
        try {
            var application = new CoreApplicationEnvironment(disposable);
            var environment = new CoreProjectEnvironment(disposable, application);
            var list = parseValueIdentifiers("test/assert*, assert, styleExit, Verify/Exact, Check/*", environment);
            var singleton = parseValueIdentifiers("assert", environment).getFirst();
            var unset = parseValueIdentifiers("unset", environment).getFirst();
            var listDescriptor = ((EditorConfigUnionDescriptor) helpers.getValue()).getChildren().stream()
                    .filter(EditorConfigListDescriptor.class::isInstance).map(EditorConfigListDescriptor.class::cast).findFirst().orElseThrow();
            if (!list.stream().map(PsiElement::getText).toList().equals(List.of("test/assert*", "assert", "styleExit", "Verify/Exact", "Check/*"))
                    || list.stream().anyMatch(item -> !listDescriptor.getChildren().getFirst().matches(item))
                    || !helpers.getValue().matches(singleton) || !helpers.getValue().matches(unset)) {
                throw new AssertionError("Rider must parse assertion helpers without partial-value recovery");
            }
        } finally {
            Disposer.dispose(disposable);
        }
        System.out.println("Neri EditorConfig property recognition and value validation passed.");
    }
}
