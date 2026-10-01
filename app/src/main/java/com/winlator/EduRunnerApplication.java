package com.winlator;

import android.app.Application;
import android.content.Context;

public final class EduRunnerApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        EduRunnerCrashHandler.install(this);
        try {
            com.winlator.core.RuntimePaths.initialize(getFilesDir());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot prepare runtime paths", error);
        }
    }
}
