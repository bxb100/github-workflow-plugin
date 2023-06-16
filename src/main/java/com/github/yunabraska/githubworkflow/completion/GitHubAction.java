package com.github.yunabraska.githubworkflow.completion;

import com.github.yunabraska.githubworkflow.api.RepositoryContentRequest;
import com.github.yunabraska.githubworkflow.model.DownloadException;
import com.github.yunabraska.githubworkflow.util.ToolUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowCompletionContributor.project;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.downloadAction;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static java.util.Optional.ofNullable;

public class GitHubAction {

    private static final Logger LOGGER = Logger.getInstance(GitHubAction.class);

    // https://regex101.com/r/DSlmSb/1
    private static final Pattern PATTERN = Pattern.compile("^(?<name>.+?)/(?<repo>.+?)(/(?<path>.+))?@(?<ref>.+)");
    private final Map<String, String> inputs = new ConcurrentHashMap<>();
    private final Map<String, String> outputs = new ConcurrentHashMap<>();
    private final AtomicLong expiration = new AtomicLong(0);
    private final AtomicReference<String> name = new AtomicReference<>(null);
    private final AtomicReference<String> repo = new AtomicReference<>(null);
    private final AtomicReference<String> path = new AtomicReference<>(null);
    private final AtomicReference<String> ref = new AtomicReference<>(null);
    private boolean local;

    /**
     * There are some cases:
     * <br>
     * <a href="https://docs.github.com/en/actions/using-workflows/reusing-workflows">Calling a reusable workflow</a>
     * <br>
     * 1. `{owner}/{repo}/.github/workflows/{filename}@{ref}`
     * <br>
     * 2. `./.github/workflows/{filename}`
     * <br>
     * <a href="https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#jobsjob_idstepsuses">Use action</a>
     * <p>
     * 1. `{owner}/{repo}@{ref}`
     * <p>
     * 2. `{owner}/{repo}/{path}@{ref}`
     * <p>
     * 3. `./path/to/dir`
     * <p>
     * 4. `docker://{image}:{tag}`
     * <p>
     * 5. ⛔`docker://{host}/{image}:{tag}`
     * <p>
     * 6. ⛔`docker://{host}/{image}:{tag}`
     * <p>
     * so we can set a map to self define GitHub Token with repo and have a default to mapping else
     */
    private GitHubAction(final String uses) {
        if (uses != null) {

            if (uses.contains("docker")) {
                return;
            }

            boolean isAction = !uses.contains("/workflows/");
            if (uses.charAt(0) == '.') {
                String path = uses;
                if (isAction) {
                    path += "/action.yml";
                }
                this.name.set("local");
                this.repo.set(project.get().getName());
                this.path.set(path);
                this.local = true;
                // relative path, can directly download
                setActionParameters(isAction);
                return;
            }

            Matcher matcher = PATTERN.matcher(uses);
            if (!matcher.matches()) {
                return;
            }
            String username = matcher.group("name");
            String repo = matcher.group("repo");
            String path = matcher.group("path");
            String ref = matcher.group("ref");

            this.local = false;
            this.name.set(username);
            this.repo.set(repo);
            this.ref.set(ref);
            // compatible with workflow and action
            if (path == null) {
                path = "action.yml";
            } else if (!path.endsWith(".yml") && !path.endsWith(".yaml")) {
                path += "/action.yml";
            }
            this.path.set(path);
            setActionParameters(isAction);
        }
    }

    private static String getLocalFile(String relativePath) {
        VirtualFile root = ProjectUtil.guessProjectDir(project.get());
        if (root == null) {
            return null;
        }
        VirtualFile action = root.findFileByRelativePath(relativePath);
        if (action == null) {
            return null;
        }
        return LoadTextUtil.loadText(action).toString();
    }

    public static GitHubAction getGitHubAction(final String uses) {
        try {
            GitHubAction gitHubAction = ACTION_CACHE.getOrDefault(uses, null);
            if (gitHubAction == null || gitHubAction.expiration() < System.currentTimeMillis()) {
                gitHubAction = new GitHubAction(uses);
                ACTION_CACHE.put(uses, gitHubAction);
            }
            return gitHubAction;
        } catch (Exception e) {
            return new GitHubAction(null);
        }
    }

    public String name() {
        return name.get();
    }

    public String repo() {
        return repo.get();
    }

    public String path() {
        return path.get();
    }

    public Map<String, String> inputs() {
        return inputs;
    }

    public Map<String, String> outputs() {
        return outputs;
    }

    public long expiration() {
        return expiration.get();
    }

    public String ref() {
        return ref.get();
    }

    private void setActionParameters(final boolean isAction) {
        try {
            Supplier<String> downloader;

            if (this.local) {
                downloader = () -> getLocalFile(this.path());
            } else {
                downloader = () -> {
                    RepositoryContentRequest request = RepositoryContentRequest.request(
                            this.name(),
                            this.repo(),
                            this.path(),
                            this.ref()
                    );
                    try {
                        return ToolUtils.execute(project.get(), request);
                    } catch (IOException e) {
                        LOGGER.error("Failed to download action.yml", e);
                        throw new DownloadException(e);
                    }
                };
            }

            extractActionParameters(downloadAction(downloader, this), isAction);
            expiration.set(System.currentTimeMillis() + CACHE_ONE_DAY);
        } catch (Exception e) {
            expiration.set(System.currentTimeMillis() + CACHE_TEN_MINUTES);
        }
    }

    private void extractActionParameters(final String content, final boolean isAction) {
        final WorkflowFile workflowFile = WorkflowFile.workflowFileOf(toString(), content);
        inputs.putAll(getActionParameters(workflowFile, FIELD_INPUTS, isAction));
        outputs.putAll(getActionParameters(workflowFile, FIELD_OUTPUTS, isAction));
    }


    private Map<String, String> getActionParameters(final WorkflowFile workflowFile, final String node, final boolean action) {
        return workflowFile.nodesToMap(
                node, n -> action || (ofNullable(n.parent()).map(YamlNode::parent).map(YamlNode::parent).filter(parent -> "on".equals(parent.name) || "true".equals(parent.name)).isPresent()),
                n -> orEmpty(n.name()),
                GitHubWorkflowUtils::getDescription
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                this.name() + "_" + this.repo()
        );
        if (this.path() != null) {
            sb.append(this.path().replace("action.yml", "")
                    .replace("/", "_").replace(".", "_"));
        }
        if (this.ref() != null) {
            sb.append("_").append(this.ref());
        }
        return sb.toString();
    }
}
