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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean homeShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EduRunnerCrashHandler.install(this);
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edurunner_activity);
        requestCrashLogStorageAccess();

        if (runtimeReady()) {
            showHome();
        } else if (!requestAppPermissions()) {
            startRuntimeSetup();
        }
    }

    private void requestCrashLogStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }
    }

    private boolean runtimeReady() {
        RootFS rootFS = RootFS.find(this);
        return rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION;
    }

    private void startRuntimeSetup() {
        showLoading();
        RootFSInstaller.installIfNeeded(this);
        waitForRuntime();
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

    private void waitForRuntime() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (runtimeReady()) showHome();
                else handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private void showHome() {
        if (homeShown || isFinishing() || isDestroyed()) return;
        homeShown = true;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.EduRunnerFragmentContainer, new EduRunnerHomeFragment())
                .commitAllowingStateLoss();
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
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
