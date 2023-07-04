package com.github.yunabraska.githubworkflow.util;

import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToolUtils {
    // https://regex101.com/r/DSlmSb/1
    static final Pattern PATTERN = Pattern.compile("^(?<name>.+?)/(?<repo>.+?)(/(?<path>.+))?@(?<ref>.+)");

    public static Optional<GithubAccount> getGitHubAccount() {
        return GHAccountsUtil.getAccounts()
            .stream()
            .filter(account -> account.getServer().isGithubDotCom())
            .findFirst();
    }

    public static GitHubUsesPart extractGitHubWorkflowUses(String uses) {

        Matcher matcher = PATTERN.matcher(uses);
        if (!matcher.matches()) {
            return null;
        }
        return new GitHubUsesPart() {
            {
                username = matcher.group("name");
                repo = matcher.group("repo");
                path = matcher.group("path");
                ref = matcher.group("ref");
            }
        };
    }

    public static class GitHubUsesPart {

        String username;
        String repo;
        String path;
        String ref;

        public String getUsername() {
            return username;
        }

        public String getRepo() {
            return repo;
        }

        public String getPath() {
            return path;
        }

        public String getRef() {
            return ref;
        }
    }
}
