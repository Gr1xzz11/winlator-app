package com.winlator;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class EduRunnerCrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private EduRunnerCrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void install(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof EduRunnerCrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new EduRunnerCrashHandler(context));
        }
    }

    @Override public void uncaughtException(Thread thread, Throwable throwable) {
        try { writeCrash(thread, throwable); } catch (Throwable ignored) {}
        if (previous != null) previous.uncaughtException(thread, throwable);
        else System.exit(10);
    }

    private void writeCrash(Thread thread, Throwable throwable) throws Exception {
        File dir;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            dir = new File(Environment.getExternalStorageDirectory(), "GRXT");
        } else {
            dir = new File(context.getExternalFilesDir(null), "GRXT");
        }
        if (!dir.exists()) dir.mkdirs();

        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File out = new File(dir, "crash-" + stamp + ".txt");
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(out))) {
            pw.println("GRXT EduRunner crash report");
            pw.println("Time: " + new Date());
            pw.println("Thread: " + thread.getName());
            pw.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Build: " + Build.FINGERPRINT);
            pw.println("App: " + context.getPackageName());
            pw.println();
            throwable.printStackTrace(pw);
        }
    }
}
