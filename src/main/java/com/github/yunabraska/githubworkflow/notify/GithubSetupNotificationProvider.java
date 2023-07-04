package com.github.yunabraska.githubworkflow.notify;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.*;

import com.github.yunabraska.githubworkflow.MyBundle;
import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.util.ToolUtils;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.yaml.YAMLFileType;

public class GithubSetupNotificationProvider implements EditorNotificationProvider, DumbAware {

    static MyBundle INSTANCE = MyBundle.INSTANCE;

    @RequiresEdt
    private static @NotNull EditorNotificationPanel createPanel(
        @NotNull Project project,
        @NotNull @NlsContexts.LinkLabel String message,
        @NotNull FileEditor fileEditor
    ) {
        EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
        panel.setText(message);
        panel.createActionLabel(INSTANCE.getMessage("notify.github.setup"), "githubworkflow.show.github.settings");
        panel.createActionLabel("Refresh", () -> {
            // there is no way to have a setting change listener, so we have to refresh manually
            // I checked the GitHub plugin code, it not exposes neither event nor listener
            // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206128419-How-to-listen-for-changed-settings
            // other way is to use custom setting with fire settings change event to some TOPIC inner message bus, but not now
            EditorNotifications.getInstance(project).updateNotifications(fileEditor.getFile());
        });
        return panel;
    }

    @Override
    @Nullable
    public Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
        @NotNull Project project,
        @NotNull VirtualFile file
    ) {
        if (!YAMLFileType.YML.equals(file.getFileType())) {
            return null;
        }

        boolean isWorkflowFile = Optional.of(file.getPath())
            .map(Path::of)
            .map(GitHubWorkflowUtils::isWorkflowPath)
            .orElse(false);
        if (!isWorkflowFile) {
            return null;
        }

        Optional<GithubAccount> account = ToolUtils.getGitHubAccount();
        if (account.isPresent()) {
            return null;
        }

        return editor -> createPanel(project, INSTANCE.getMessage("notify.github.account.not.exist"), editor);
    }
}
