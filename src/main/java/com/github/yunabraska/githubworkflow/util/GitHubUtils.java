package com.github.yunabraska.githubworkflow.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class GitHubUtils {

    static GithubAuthenticationManager githubAuthenticationManager = GithubAuthenticationManager.getInstance();

    static Predicate<GithubAccount> personalGitHub = account -> account.getServer().isGithubDotCom();

    public static Optional<GithubAccount> getAccount(Project project) {

        // first get default github account
        GithubAccount defaultAccount = project == null ? null : githubAuthenticationManager.getDefaultAccount(project);

        return Optional.ofNullable(defaultAccount)
                .filter(personalGitHub)
                .or(() -> {
                    Set<GithubAccount> accounts = githubAuthenticationManager.getAccounts();

                    return accounts.stream().filter(personalGitHub).findFirst();
                });
    }
}
