package com.github.yunabraska.githubworkflow.api;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import com.github.yunabraska.githubworkflow.util.ToolUtils;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

public class GitHubRequestService {

    public <T> T request(Project project, GithubApiRequest<T> request) throws IOException {
        try {
            Optional<GithubAccount> gitHubAccount = ToolUtils.getGitHubAccount();
            if (gitHubAccount.isPresent()) {
                return GithubApiRequestExecutor.Factory.Companion.getInstance().create(
                    Objects.requireNonNull(GHCompatibilityUtil.getOrRequestToken(gitHubAccount.get(), project))
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
}
