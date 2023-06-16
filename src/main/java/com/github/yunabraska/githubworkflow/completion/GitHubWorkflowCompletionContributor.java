package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.CompletionItem.completionItemOf;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.completionItemsOf;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listEnvs;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listInputs;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listJobNeeds;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listJobOutputs;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listJobs;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listNeeds;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listSecrets;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listStepOutputs;
import static com.github.yunabraska.githubworkflow.completion.CompletionItem.listSteps;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_DEFAULT;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.addLookupElements;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_OUTPUT;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class GitHubWorkflowCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(GitHubWorkflowCompletionContributor.class);
    // FIXME
    public static AtomicReference<Project> project = new AtomicReference<>(null);

    public GitHubWorkflowCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider<>() {
            @Override
            public void addCompletions(
                    @NotNull final CompletionParameters parameters,
                    @NotNull final ProcessingContext context,
                    @NotNull final CompletionResultSet resultSet
            ) {
                PsiElement position = parameters.getPosition();
                project.set(position.getProject());
                if (!position.isValid()) {
                    LOG.error("current PsiElement not valid: " + position);
                    return;
                }

                // parameters 是当前指针的 PSI
                getWorkflowFile(position).ifPresent(path -> {
                    //CACHE USE ONLY ON NEED
                    // FIXME: 我感觉这里应该有更优雅的 cache 方式
                    final AtomicReference<WorkflowFile> partCache = new AtomicReference<>(null);
                    final AtomicReference<WorkflowFile> fullCache = new AtomicReference<>(null);
                    final Supplier<WorkflowFile> partFile = fromPartCache(partCache, path, parameters);
                    final Supplier<WorkflowFile> fullFile = fromFullCache(partCache, fullCache, path, parameters);

                    final String[] prefix = new String[]{""};
                    final Optional<String[]> caretBracketItem = getCaretBracketItem(parameters, partFile, prefix);
                    final CompletionResultSet resultSetPrefix = resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix[0]));
                    caretBracketItem.ifPresent(cbi -> {
                        final Map<Integer, List<CompletionItem>> completionResultMap = new HashMap<>();
                        for (int i = 0; i < cbi.length; i++) {
                            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
                            final List<CompletionItem> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
                            final int index = i;
                            if (i != 0 && (previousCompletions.isEmpty() || previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index])))) {
                                return;
                            } else {
                                addCompletionItems(cbi, i, partFile, fullFile, completionResultMap);
                            }
                        }
                        //ADD LOOKUP ELEMENTS
                        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                                .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                .ifPresent(resultSetPrefix::addAllElements);
                    });
                    //ACTIONS && WORKFLOWS
                    if (caretBracketItem.isEmpty()) {
                        if (FIELD_NEEDS.equals(partFile.get().getCurrentNode().name())) {
                            Optional.of(listNeeds(partFile, fullFile)).filter(cil -> !cil.isEmpty())
                                    .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                    .ifPresent(resultSetPrefix::addAllElements);
                        } else {
                            // skip `a: *<crater>` PS: `with:(EOF)` (just discuss normal YAML)
                            YAMLKeyValue parent = PsiTreeUtil.getParentOfType(position, YAMLKeyValue.class);
                            if (parent == null) {
                                return;
                            }
                            // idea: https://intellij-support.jetbrains.com/hc/en-us/community/posts/360000333580-How-to-find-value-by-key-in-YML-file
                            String qualifiedName = YAMLUtil.getConfigFullName(parent);
                            if (!qualifiedName.contains(DUMMY_IDENTIFIER_TRIMMED) && !qualifiedName.endsWith("with")) {
                                return;
                            }
                            //TODO: AutoCompletion middle?
                            partFile.get().getActionInputs().ifPresent(map -> addLookupElements(resultSet, map, NodeIcon.ICON_INPUT, ':'));
                        }
                    }
                });
            }

            private Supplier<WorkflowFile> fromPartCache(final AtomicReference<WorkflowFile> partCache, final Path path, final CompletionParameters parameters) {
                return () -> {
                    if (partCache.get() == null) {
                        final int caretOffset = parameters.getOffset();
                        final String wholeText = parameters.getOriginalFile().getText();
                        final int endIndex = Math.max(wholeText.indexOf("\n", caretOffset), wholeText.indexOf("\r", caretOffset));
                        partCache.set(WorkflowFile.workflowFileOf("part_" + path, wholeText.substring(0, endIndex != -1 ? endIndex : caretOffset)));
                    }
                    return partCache.get();
                };
            }

            private Supplier<WorkflowFile> fromFullCache(final AtomicReference<WorkflowFile> partCache, final AtomicReference<WorkflowFile> fullCache, final Path path, final CompletionParameters parameters) {
                return () -> {
                    if (fullCache.get() == null) {
                        fullCache.set(ofNullable(WorkflowFile.workflowFileOf("complete_" + path, parameters.getOriginalFile().getText())).orElse(fromPartCache(partCache, path, parameters).get()));
                    }
                    return fullCache.get();
                };
            }
        };
    }

    private static void addCompletionItems(final String[] cbi, final int i, final Supplier<WorkflowFile> partFile, final Supplier<WorkflowFile> fullFile, final Map<Integer, List<CompletionItem>> completionItemMap) {
        if (i == 0) {
            switch (cbi[0]) {
                case FIELD_STEPS -> completionItemMap.put(i, listSteps(partFile, fullFile));
                case FIELD_JOBS -> completionItemMap.put(i, listJobs(partFile, fullFile));
                case FIELD_ENVS -> completionItemMap.put(i, listEnvs(partFile, fullFile));
                case FIELD_GITHUB ->
                        completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get(), ICON_ENV));
                case FIELD_INPUTS -> completionItemMap.put(i, listInputs(partFile, fullFile));
                case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(partFile, fullFile));
                case FIELD_NEEDS -> completionItemMap.put(i, listJobNeeds(partFile, fullFile));
                default -> {
                    //ON.workflow_call.outputs
                    if (partFile.get().isOutputTriggerNode()) {
                        completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                    } else if (!"runs-on".equals(partFile.get().getCurrentNode().name()) && !"os".equals(partFile.get().getCurrentNode().name())) {
                        //DEFAULT
                        ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                                .map(Supplier::get)
                                .map(map -> completionItemsOf(map, ICON_NODE))
                                .ifPresent(items -> completionItemMap.put(i, items));
                    }
                }
            }
        } else if (i == 1) {
            switch (cbi[0]) {
                case FIELD_JOBS, FIELD_NEEDS, FIELD_STEPS ->
                        completionItemMap.put(i, singletonList(completionItemOf(FIELD_OUTPUTS, "", ICON_OUTPUT)));
                default -> {
                }
            }
        } else if (i == 2) {
            switch (cbi[0]) {
                case FIELD_JOBS, FIELD_NEEDS -> completionItemMap.put(i, listJobOutputs(cbi[1], partFile, fullFile));
                case FIELD_STEPS -> completionItemMap.put(i, listStepOutputs(cbi[1], partFile, fullFile));
                default -> {
                }
            }
        }
    }

    @NotNull
    private static List<LookupElement> toLookupItems(final List<CompletionItem> items) {
        return items.stream().map(CompletionItem::toLookupElement).collect(Collectors.toList());
    }
}
