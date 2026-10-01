package com.winlator;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.container.Shortcut;
import com.winlator.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EduRunnerHomeFragment extends Fragment {
    private static final String DEV_PIN = "1283256";
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RecyclerView recyclerView;
    private View emptyView;
    private ContainerManager manager;
    private ProgressDialog progress;
    private boolean busy;
    private boolean developerUnlocked;
    private int versionTaps;
    private long lastVersionTap;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null || busy) return;
                // Only the read grant is needed. Copy immediately even if persistence is unsupported.
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {}
                importFolder(uri);
            });

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.edurunner_home_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        manager = new ContainerManager(requireContext().getApplicationContext());
        recyclerView = view.findViewById(R.id.ProgramGrid);
        emptyView = view.findViewById(R.id.EmptyPrograms);
        int width = getResources().getConfiguration().screenWidthDp;
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), Math.max(2, Math.min(4, width / 260))));
        view.findViewById(R.id.SettingsButton).setOnClickListener(v -> showSettings());
        refresh();
    }

    private void showSettings() {
        String[] items = developerUnlocked ? new String[]{"О приложении", "Developer Mode"} : new String[]{"О приложении"};
        new AlertDialog.Builder(requireContext()).setTitle("Настройки")
                .setItems(items, (d, which) -> { if (which == 0) showAbout(); else showDeveloperPanel(); })
                .setNegativeButton("Закрыть", null).show();
    }

    private void showAbout() {
        versionTaps = 0;
        TextView version = new TextView(requireContext());
        String versionName = "";
        try { versionName = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        version.setText("GRXT EduRunner\n\nВерсия " + versionName);
        version.setTextColor(0xFFFFFFFF);
        version.setTextSize(18);
        version.setGravity(android.view.Gravity.CENTER);
        int pad = (int)(28 * getResources().getDisplayMetrics().density);
        version.setPadding(pad, pad, pad, pad);
        AlertDialog about = new AlertDialog.Builder(requireContext()).setTitle("О приложении")
                .setView(version).setPositiveButton("Готово", null).create();
        version.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - lastVersionTap > 2500) versionTaps = 0;
            lastVersionTap = now;
            if (++versionTaps >= 7) {
                versionTaps = 0;
                about.dismiss();
                showDeveloperPin();
            }
        });
        about.show();
    }

    private void showDeveloperPin() {
        EditText input = new EditText(requireContext());
        input.setHint("PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle("Developer Mode")
                .setMessage("Введите PIN разработчика").setView(input).setNegativeButton("Отмена", null)
                .setPositiveButton("Войти", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (DEV_PIN.equals(input.getText().toString())) {
                developerUnlocked = true;
                dialog.dismiss();
                showDeveloperPanel();
            } else input.setError("Неверный PIN");
        }));
        dialog.show();
    }

    private void showDeveloperPanel() {
        if (!developerUnlocked || busy) return;
        String[] items = {"Добавить программу", "Программы", "Контейнеры / Runtime", "Совместимость", "Отладка"};
        new AlertDialog.Builder(requireContext()).setTitle("Developer Mode").setItems(items, (d, which) -> {
            if (which == 0) {
                try { folderPicker.launch(null); }
                catch (Exception error) { showError("Выбор папки недоступен", error); }
            } else if (which == 1) showPrograms();
            else if (which == 2) openRuntimeSettings(R.id.menu_item_containers);
            else if (which == 3) openRuntimeSettings(R.id.menu_item_settings);
            else showDebugInfo();
        }).setNegativeButton("Закрыть", null).show();
    }

    private void openRuntimeSettings(int menuItem) {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra("selected_menu_item_id", menuItem);
        startActivity(intent);
    }

    private void showPrograms() {
        ArrayList<Shortcut> programs = manager.loadShortcuts(null);
        programs.removeIf(s -> s.file.isDirectory());
        if (programs.isEmpty()) {
            Toast.makeText(requireContext(), "Программы пока не установлены", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[programs.size()];
        for (int i = 0; i < programs.size(); i++) names[i] = displayName(programs.get(i));
        new AlertDialog.Builder(requireContext()).setTitle("Программы").setItems(names, (d, which) -> {
            Shortcut shortcut = programs.get(which);
            new AlertDialog.Builder(requireContext()).setTitle(displayName(shortcut))
                    .setMessage("EXE: " + shortcut.path + "\nContainer: " + shortcut.container.id)
                    .setPositiveButton("Запустить", (dialog, button) -> run(shortcut))
                    .setNeutralButton("Удалить карточку", (dialog, button) -> {
                        shortcut.remove();
                        refresh();
                    }).setNegativeButton("Закрыть", null).show();
        }).setNegativeButton("Закрыть", null).show();
    }

    private void showDebugInfo() {
        Context context = requireContext();
        new AlertDialog.Builder(context).setTitle("Отладка")
                .setMessage("Основные crash reports: /storage/emulated/0/GRXT/\n\nРезервный каталог: " +
                        context.getExternalFilesDir(null) + "/GRXT/\n\nКонтейнеров: " + manager.getContainers().size())
                .setPositiveButton("Закрыть", null).show();
    }

    private void importFolder(Uri treeUri) {
        final Context context = requireContext().getApplicationContext();
        busy = true;
        showProgress("Копирование папки программы…");
        worker.execute(() -> {
            File localRoot = null;
            try {
                File staging = new File(context.getFilesDir(), "edurunner-imports");
                if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Не удалось создать каталог импорта");
                localRoot = Files.createTempDirectory(staging.toPath(), "program-").toFile();
                EduRunnerFolderImporter.Result imported = new EduRunnerFolderImporter(context.getContentResolver()).copy(treeUri, localRoot);
                if (imported.executables.isEmpty()) throw new IOException("В выбранной папке не найдено файлов .exe");
                main.post(() -> {
                    if (!hasScreen()) { FileUtils.delete(imported.root); return; }
                    closeProgress();
                    if (imported.executables.size() == 1) createProgram(imported, imported.executables.get(0));
                    else chooseExecutable(imported);
                });
            } catch (Exception error) {
                if (localRoot != null) FileUtils.delete(localRoot);
                main.post(() -> finishImport(error));
            }
        });
    }

    private void chooseExecutable(EduRunnerFolderImporter.Result imported) {
        String[] names = new String[imported.executables.size()];
        try {
            for (int i = 0; i < names.length; i++) names[i] = EduRunnerProgramFiles.relative(imported.root, imported.executables.get(i));
            new AlertDialog.Builder(requireContext()).setTitle("Выберите главный EXE")
                    .setItems(names, (d, which) -> createProgram(imported, imported.executables.get(which)))
                    .setNegativeButton("Отмена", (d, which) -> discardImport(imported))
                    .setOnCancelListener(d -> discardImport(imported)).show();
        } catch (IOException error) {
            discardImport(imported);
            finishImport(error);
        }
    }

    private void discardImport(EduRunnerFolderImporter.Result imported) {
        busy = false;
        worker.execute(() -> FileUtils.delete(imported.root));
    }

    private void createProgram(EduRunnerFolderImporter.Result imported, File exe) {
        showProgress("Подготовка программы…");
        try {
            // Keys and defaults follow ContainerDetailFragment and Container.loadData.
            JSONObject data = new JSONObject();
            data.put("name", imported.name);
            data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
            data.put("dxwrapper", DXWrappers.WINED3D);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(requireContext()));
            manager.createContainerAsync(data, container -> {
                if (container == null) {
                    worker.execute(() -> FileUtils.delete(imported.root));
                    finishImport(new IOException("Не удалось создать среду. Проверьте свободное место."));
                    return;
                }
                worker.execute(() -> {
                    try {
                        installProgram(container, imported, exe);
                        main.post(() -> {
                            if (!hasScreen()) return;
                            finishImport(null);
                            refresh();
                            Toast.makeText(requireContext(), "Готово: " + imported.name, Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception error) {
                        FileUtils.delete(imported.root);
                        manager.removeContainerAsync(container, () -> finishImport(error));
                    }
                });
            });
        } catch (Exception error) {
            worker.execute(() -> FileUtils.delete(imported.root));
            finishImport(error);
        }
    }

    private void installProgram(Container container, EduRunnerFolderImporter.Result imported, File exe) throws IOException {
        // Calculate this while the staging files still exist, before moving the root.
        String relativeExe = EduRunnerProgramFiles.relative(imported.root, exe);
        String directoryName = EduRunnerProgramFiles.directoryName(imported.name);
        File parent = new File(container.getRootDir(), ".wine/drive_c/GRXT");
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Не удалось создать каталог программы");
        File programDir = new File(parent, directoryName);
        if (!imported.root.renameTo(programDir)) {
            if (!FileUtils.copy(imported.root, programDir)) throw new IOException("Не удалось скопировать программу в среду");
            FileUtils.delete(imported.root);
        }
        if (!new File(programDir, relativeExe).isFile()) throw new IOException("Главный EXE не скопирован");
        if (!container.getConfigFile().isFile()) throw new IOException("Конфигурация среды не сохранена");
        File desktop = new File(container.getUserDir(), "Desktop");
        if (!desktop.isDirectory() && !desktop.mkdirs()) throw new IOException("Не удалось создать каталог карточек");
        File shortcutFile = new File(desktop, directoryName + ".desktop");
        String windowsPath = EduRunnerProgramFiles.windowsPath(directoryName, relativeExe);
        EduRunnerProgramFiles.writeShortcut(shortcutFile, imported.name, windowsPath);
        Shortcut shortcut = new Shortcut(container, shortcutFile);
        if (!windowsPath.equals(shortcut.path) || shortcut.isLinkPath()) {
            throw new IOException("Неверный путь ярлыка: " + shortcut.path);
        }
        shortcut.putExtra("edurunnerName", imported.name.replace('\n', ' ').replace('\r', ' '));
        shortcut.saveData();
    }

    private boolean hasScreen() { return isAdded() && getView() != null && !isRemoving(); }

    private void showProgress(String message) {
        closeProgress();
        progress = new ProgressDialog(requireContext());
        progress.setMessage(message);
        progress.setCancelable(false);
        progress.show();
    }

    private void closeProgress() {
        if (progress != null) { progress.dismiss(); progress = null; }
    }

    private void finishImport(Exception error) {
        busy = false;
        if (!hasScreen()) return;
        closeProgress();
        if (error != null) showError("Ошибка импорта", error);
    }

    private void showError(String title, Exception error) {
        android.util.Log.e("EduRunner", title, error);
        new AlertDialog.Builder(requireContext()).setTitle(title)
                .setMessage(error.getMessage() != null ? error.getMessage() : error.toString())
                .setPositiveButton("Закрыть", null).show();
    }

    @Override public void onResume() {
        super.onResume();
        if (manager != null && !busy) {
            manager = new ContainerManager(requireContext().getApplicationContext());
            refresh();
        }
    }

    @Override public void onDestroyView() {
        closeProgress();
        recyclerView = null;
        emptyView = null;
        super.onDestroyView();
    }

    private static String displayName(Shortcut shortcut) { return shortcut.getExtra("edurunnerName", shortcut.name); }

    private void refresh() {
        if (!hasScreen() || emptyView == null) return;
        ArrayList<Shortcut> items = manager.loadShortcuts(null);
        items.removeIf(s -> s.file.isDirectory());
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        recyclerView.setAdapter(new Adapter(items));
    }

    private void run(Shortcut shortcut) {
        if (busy) return;
        if (shortcut.path == null || shortcut.path.isEmpty() || shortcut.path.contains("://")) {
            Toast.makeText(requireContext(), "Программу нужно повторно установить через администратора", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        intent.putExtra("edurunner_launcher", true);
        startActivity(intent);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final ArrayList<Shortcut> items;
        Adapter(ArrayList<Shortcut> items) { this.items = items; }
        class Holder extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            TextView title, subtitle;
            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.ProgramIcon);
                title = view.findViewById(R.id.ProgramTitle);
                subtitle = view.findViewById(R.id.ProgramSubtitle);
            }
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.edurunner_program_card, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Shortcut shortcut = items.get(position);
            if (shortcut.icon != null) holder.icon.setImageBitmap(shortcut.icon);
            else holder.icon.setImageResource(R.mipmap.ic_launcher);
            holder.title.setText(displayName(shortcut));
            holder.subtitle.setText("Запустить");
            holder.itemView.setOnClickListener(v -> run(shortcut));
        }
        @Override public int getItemCount() { return items.size(); }
    }
}
