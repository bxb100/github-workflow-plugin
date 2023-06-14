package com.github.yunabraska.githubworkflow.notify;

import com.github.yunabraska.githubworkflow.MyBundle;
import com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils;
import com.github.yunabraska.githubworkflow.util.GitHubUtils;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.yaml.YAMLFileType;

import javax.swing.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

public class GithubSetupNotificationProvider implements EditorNotificationProvider, DumbAware {

    static MyBundle INSTANCE = MyBundle.INSTANCE;

    @RequiresEdt
    @SuppressWarnings("UnstableApiUsage")
    private static @NotNull EditorNotificationPanel createPanel(
            @NotNull @NlsContexts.LinkLabel String message,
            @NotNull FileEditor fileEditor
    ) {
        EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
        panel.setText(message);
        panel.createActionLabel(MyBundle.INSTANCE.getMessage("notify.github.setup"), "ShowGithubSettings");
        return panel;
    }

    @Override
    public @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile file
    ) {
        if (!YAMLFileType.YML.equals(file.getFileType())) {
            return CONST_NULL;
        }

        boolean isWorkflowFile = Optional.of(file.getPath())
                .map(Path::of)
                .map(GitHubWorkflowUtils::isWorkflowPath)
                .orElse(false);
        if (!isWorkflowFile) {
            return CONST_NULL;
        }

        Optional<GithubAccount> account = GitHubUtils.getAccount(project);
        if (account.isPresent()) {
            return CONST_NULL;
        }

        return editor -> createPanel(INSTANCE.getMessage("notify.github.account.not.exist"), editor);
    }
}
