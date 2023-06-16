package com.github.yunabraska.githubworkflow.api;

import com.github.yunabraska.githubworkflow.util.ToolUtils;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiResponse;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28">repos content</a>
 * We use this get YAML file
 */
public class RepositoryContentRequest extends GithubApiRequest.Get<String> {

    public RepositoryContentRequest(@NotNull String url) {
        super(url, "application/vnd.github.raw");
    }

    // FIXME
    public static String execute(Project project, RepositoryContentRequest request) throws IOException {


        try {
            java.util.Optional<GithubAccount> optional = ToolUtils.getAccount();

            if (optional.isPresent()) {
                return GithubApiRequestExecutor.Factory.Companion.getInstance().create(
                        Objects.requireNonNull(GHCompatibilityUtil.getOrRequestToken(optional.get(), project))
                ).execute(request);
            }

            if (project.isDisposed()) {
                return null;
            }
        } catch (NullPointerException e) {
            // ignore
        }

        NotificationGroupManager.getInstance()
                .getNotificationGroup("GitHub Token NotExist")
                .createNotification("No GitHub account found", NotificationType.ERROR)
                .notify(project);
        return null;
    }

    public static @NotNull RepositoryContentRequest get(
            @NotNull String owner,
            @NotNull String repo,
            @NotNull String path,
            String ref
    ) {
        // https://api.github.com/repos/OWNER/REPO/contents/PATH
        String url = String.format(
                "https://api.github.com/repos/%s/%s/contents/%s",
                owner,
                repo,
                path
        );
        if (StringUtils.isNotBlank(ref)) {
            url += "?ref=" + ref;
        }
        return new RepositoryContentRequest(url);
    }

    @Override
    public String extractResult(@NotNull GithubApiResponse githubApiResponse) throws IOException {
        return githubApiResponse.handleBody(body -> new String(body.readAllBytes(), StandardCharsets.UTF_8));
    }
}
