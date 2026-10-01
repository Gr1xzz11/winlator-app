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
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
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
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        try {
            writeReport(new File(Environment.getExternalStorageDirectory(), "GRXT"), stamp, thread, throwable);
            return;
        } catch (Exception unavailable) {
            // A granted permission does not guarantee that shared storage is writable.
        }
        File external = context.getExternalFilesDir(null);
        try {
            if (external == null) throw new java.io.IOException("External files unavailable");
            writeReport(new File(external, "GRXT"), stamp, thread, throwable);
        } catch (Exception unavailable) {
            writeReport(new File(context.getFilesDir(), "GRXT"), stamp, thread, throwable);
        }
    }

    private void writeReport(File dir, String stamp, Thread thread, Throwable throwable) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Cannot create " + dir);
        File out = new File(dir, "crash-" + stamp + ".txt");
        try (PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(new FileOutputStream(out), java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("GRXT EduRunner crash report");
            pw.println("Time: " + new Date());
            pw.println("Thread: " + thread.getName());
            pw.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Build: " + Build.FINGERPRINT);
            pw.println("App: " + context.getPackageName());
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            pw.println("Version: " + info.versionName + " (" + info.versionCode + ")");
            pw.println("Exception: " + throwable);
            pw.println();
            throwable.printStackTrace(pw);
            pw.flush();
            if (pw.checkError()) throw new java.io.IOException("Cannot write " + out);
        }
    }
}
