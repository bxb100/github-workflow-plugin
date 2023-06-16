package com.github.yunabraska.githubworkflow.exception;

import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Consumer;
import io.sentry.Sentry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Optional;

public class SentryErrorReportSubmitter extends ErrorReportSubmitter {

    static {

        Sentry.init(options -> {
            options.setDsn("https://59ddce6d427c4bbf98a181132188723d@o326501.ingest.sentry.io/1834496");
            // Set tracesSampleRate to 1.0 to capture 100% of transactions for performance monitoring.
            // We recommend adjusting this value in production.
            options.setTracesSampleRate(1.0);
            // When first trying Sentry it's good to see what the SDK is doing:
            options.setDebug(false);
            options.setEnableUncaughtExceptionHandler(false);
        });
    }

    @Override
    public @NotNull @NlsActions.ActionText String getReportActionText() {
        return "Report to Developer";
    }

    @Override
    public boolean submit(IdeaLoggingEvent @NotNull [] events, @Nullable String additionalInfo, @NotNull Component parentComponent, @NotNull Consumer<? super SubmittedReportInfo> consumer) {
        DataContext context = DataManager.getInstance().getDataContext(parentComponent);
        Project project = CommonDataKeys.PROJECT.getData(context);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Sending error report") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                for (IdeaLoggingEvent event : events) {

                    Sentry.captureException(event.getThrowable(), scope -> {
                        scope.setTag("GitHubVersion", Optional
                                .ofNullable(PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.plugins.github")))
                                .map(PluginDescriptor::getVersion).orElse("unknown"));
                        scope.setTag("IntelliJVersion", ApplicationInfoEx.getInstanceEx().getVersionName());
                        scope.setExtra("throwableText", event.getThrowableText() + " ");
                        scope.setExtra("additionalInfo", additionalInfo == null ? "" : additionalInfo);
                    });
                    Sentry.flush(3000);
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showInfoMessage(parentComponent, "Thank you for submitting your report!", "Error Report");
                    consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
                });
            }
        });
        return true;
    }
}
