package com.github.yunabraska.githubworkflow.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import java.util.Optional;
import java.util.function.Predicate;

public class GitHubUtils {

    static GithubAuthenticationManager githubAuthenticationManager = GithubAuthenticationManager.getInstance();

    static Predicate<GithubAccount> personalGitHub = account -> account.getServer().isGithubDotCom();

    public static Optional<GithubAccount> getAccount() {
        return githubAuthenticationManager.getAccounts().stream().filter(personalGitHub).findFirst();
    }
}
