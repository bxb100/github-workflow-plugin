package com.github.yunabraska.githubworkflow.util;

import com.github.yunabraska.githubworkflow.api.RepositoryContentRequest;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class ToolUtils {
    static Predicate<GithubAccount> personalGitHub = account -> account.getServer().isGithubDotCom();

    public static Optional<GithubAccount> getAccount() {
        return GHAccountsUtil.getAccounts()
                .stream()
                .filter(personalGitHub)
                .findFirst();
    }

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
}
