package com.winlator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;
import android.content.Intent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

public class EduRunnerActivity extends AppCompatActivity {
    private static final int STORAGE_PERMISSION_REQUEST = 1001;
    private boolean setupStarted;
    private boolean homeShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EduRunnerCrashHandler.install(this);
        AppUtils.setActivityTheme(this);
        super.onCreate(null);
        setContentView(R.layout.edurunner_activity);
        enterImmersiveMode();
        requestCrashLogStorageAccess();

        // Discard restored fragments: they could create a manager during an interrupted install.
        if (savedInstanceState != null) {
            for (androidx.fragment.app.Fragment fragment : getSupportFragmentManager().getFragments()) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
            }
        }
        showLoading();
        if (!requestAppPermissions()) startRuntimeSetup();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void requestCrashLogStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (Exception unavailable) { /* Crash reports still have an app-private fallback. */ }
            }
        }
    }

    private void startRuntimeSetup() {
        if (setupStarted) return;
        setupStarted = true;
        showLoading();
        RootFSInstaller.installIfNeeded(this, success -> {
            if (isFinishing() || isDestroyed()) return;
            setupStarted = false;
            if (success) showHome();
            else new android.app.AlertDialog.Builder(this)
                    .setTitle("Не удалось подготовить среду")
                    .setMessage("Проверьте свободное место и повторите установку.")
                    .setCancelable(false)
                    .setPositiveButton("Повторить", (dialog, which) -> startRuntimeSetup())
                    .setNegativeButton("Закрыть", (dialog, which) -> finish()).show();
        });
    }

    private void showLoading() {
        TextView loading = new TextView(this);
        loading.setText("GRXT EduRunner\n\nПодготовка среды запуска…");
        loading.setTextColor(0xFFFFFFFF);
        loading.setTextSize(20);
        loading.setGravity(android.view.Gravity.CENTER);
        loading.setBackgroundColor(0xFF090E1A);
        ((android.widget.FrameLayout) findViewById(R.id.EduRunnerFragmentContainer)).removeAllViews();
        ((android.widget.FrameLayout) findViewById(R.id.EduRunnerFragmentContainer)).addView(loading,
                new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void showHome() {
        if (homeShown || isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().isStateSaved()) return;
        homeShown = true;
        ((android.widget.FrameLayout) findViewById(R.id.EduRunnerFragmentContainer)).removeAllViews();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.EduRunnerFragmentContainer, new EduRunnerHomeFragment())
                .commit();
    }

    private boolean requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) return false;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_REQUEST);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRuntimeSetup();
        } else if (requestCode == STORAGE_PERMISSION_REQUEST) {
            // SAF import and RootFS in internal storage do not need legacy storage permission.
            startRuntimeSetup();
        }
    }

    @Override protected void onResumeFragments() {
        super.onResumeFragments();
        if (!homeShown && !setupStarted) startRuntimeSetup();
    }
}
