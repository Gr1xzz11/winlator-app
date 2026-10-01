package com.winlator;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import com.winlator.core.Callback;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persist process stderr as well as Java startup stages, even when no window appears. */
public final class EduRunnerRunLog implements Callback<String> {
    private PrintWriter writer;
    private File file;
    private int lines;

    public EduRunnerRunLog(Context context) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File external = context.getExternalFilesDir(null);
        File[] roots = {new File(Environment.getExternalStorageDirectory(), "GRXT"),
                external != null ? new File(external, "GRXT") : new File(context.getFilesDir(), "GRXT"),
                new File(context.getFilesDir(), "GRXT")};
        for (File root : roots) {
            try {
                if (!root.isDirectory() && !root.mkdirs()) continue;
                File candidate = new File(root, "runtime-" + stamp + ".txt");
                writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(candidate), java.nio.charset.StandardCharsets.UTF_8), true);
                file = candidate;
                break;
            } catch (Exception ignored) {}
        }
        call("GRXT EduRunner runtime report: " + new Date());
        call("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "; Android " + Build.VERSION.RELEASE + "; SDK " + Build.VERSION.SDK_INT);
    }

    @Override public synchronized void call(String message) {
        if (writer != null && lines++ < 10000) writer.println(new Date() + " " + message);
    }

    public String path() { return file != null ? file.getPath() : "журнал недоступен"; }
    public synchronized void close() { if (writer != null) { writer.close(); writer = null; } }
}
