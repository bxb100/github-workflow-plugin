package com.github.yunabraska.githubworkflow.notify;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.GithubUtil;

public class ShowGithubSettingsAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
                e.getProject(), GithubUtil.SERVICE_DISPLAY_NAME
        );
    }
}
