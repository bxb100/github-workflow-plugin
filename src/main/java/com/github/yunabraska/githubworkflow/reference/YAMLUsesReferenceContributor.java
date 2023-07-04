package com.github.yunabraska.githubworkflow.reference;

import com.github.yunabraska.githubworkflow.util.ToolUtils;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * two scenarios:
 * <p>
 * - [x] `uses: actions/checkout@v3`
 * - [ ] `./.github/actions/setup`
 *
 * @see org.jetbrains.yaml.YAMLWebReferenceContributor
 */
@SuppressWarnings("JavadocReference")
public class YAMLUsesReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            psiElement(YAMLPlainTextImpl.class)
                .withParent(psiElement(YAMLKeyValue.class).withText(StandardPatterns.string().contains("uses: ")))
                .withoutText(StandardPatterns.string().startsWith("."))
            ,
            new PsiReferenceProvider() {
                @Override
                public boolean acceptsTarget(@NotNull PsiElement target) {
                    return false;
                }

                @Override
                public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                    if (!(element instanceof YAMLScalar scalarElement)) return PsiReference.EMPTY_ARRAY;

                    LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = scalarElement.createLiteralTextEscaper();
                    if (!escaper.isOneLine()) return PsiReference.EMPTY_ARRAY;

                    TextRange textRange = escaper.getRelevantTextRange();
                    if (textRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

                    String textValue = scalarElement.getTextValue();

                    ToolUtils.GitHubUsesPart part = ToolUtils.extractGitHubWorkflowUses(textValue);

                    if (part == null) {
                        return PsiReference.EMPTY_ARRAY;
                    }

                    String url = String.format("https://github.com/%s/%s", part.getUsername(), part.getRepo());
                    if (part.getRef() != null) {
                        url += "/tree/" + part.getRef();
                    }
                    return new PsiReference[]{new WebReference(scalarElement, textRange, url)};
                }
            }, PsiReferenceRegistrar.LOWER_PRIORITY);
    }
}
