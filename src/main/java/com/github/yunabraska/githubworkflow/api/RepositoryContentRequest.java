package com.github.yunabraska.githubworkflow.api;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28">repos content</a>
 * We use this get YAML file
 */
public class RepositoryContentRequest extends GithubApiRequest.Get<String> {

    public RepositoryContentRequest(@NotNull String url) {
        super(url, "application/vnd.github.raw");
    }

    public static @NotNull RepositoryContentRequest request(
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
