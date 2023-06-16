package com.github.yunabraska.githubworkflow.util;

import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

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
}
