package com.github.yunabraska.githubworkflow;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;

public class MyBundle extends DynamicBundle {

    public static MyBundle INSTANCE = new MyBundle("messages.MyBundle");

    public MyBundle(@NotNull String pathToBundle) {
        super(pathToBundle);
    }
}
