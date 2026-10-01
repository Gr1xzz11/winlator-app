package com.winlator;

import android.app.Application;
import android.content.Context;

public final class EduRunnerApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        EduRunnerCrashHandler.install(this);
    }
}
