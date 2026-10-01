package com.winlator;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.json.JSONObject;
import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;

import java.util.ArrayList;

public class EduRunnerHomeFragment extends Fragment {
    private static final String DEV_PIN = "1283256";
    private static final int PICK_EXE = 2201;
    private static final int PICK_FOLDER = 2202;
    private RecyclerView recyclerView;
    private View emptyView;
    private ContainerManager manager;
    private int versionTaps = 0;
    private long lastVersionTap = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.edurunner_home_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        manager = new ContainerManager(requireContext());
        recyclerView = view.findViewById(R.id.ProgramGrid);
        emptyView = view.findViewById(R.id.EmptyPrograms);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(),
                getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 3 : 2));

        view.findViewById(R.id.SettingsButton).setOnClickListener(v -> showSettings());
        refresh();
    }

    private void showSettings() {
        String[] items = {"Программы", "Runtime", "Экран", "Совместимость", "Отладка", "О приложении"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Настройки")
                .setItems(items, (d, which) -> {
                    if (which == 0) showProgramManager();
                    else if (which == 5) showAbout();
                    else Toast.makeText(requireContext(), items[which] + " — параметры будут добавлены после рабочего запуска", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showProgramManager() {
        String[] items = {"Добавить папку программы", "Добавить одиночный EXE"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Программы")
                .setItems(items, (d, which) -> {
                    if (which == 0) pickFolder();
                    else pickExe();
                })
                .setNegativeButton("Назад", null)
                .show();
    }

    private void showAbout() {
        TextView version = new TextView(requireContext());
        version.setText("GRXT EduRunner\n\nВерсия 0.1.0-winlator11.2");
        version.setTextColor(0xFF111827);
        version.setTextSize(18);
        version.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (28 * getResources().getDisplayMetrics().density);
        version.setPadding(pad, pad, pad, pad);
        version.setOnClickListener(v -> onVersionTap());

        new AlertDialog.Builder(requireContext())
                .setTitle("О приложении")
                .setView(version)
                .setPositiveButton("Готово", null)
                .show();
    }

    private void onVersionTap() {
        long now = System.currentTimeMillis();
        if (now - lastVersionTap > 2500) versionTaps = 0;
        lastVersionTap = now;
        versionTaps++;
        if (versionTaps >= 7) {
            versionTaps = 0;
            showDeveloperPin();
        }
    }

    private void showDeveloperPin() {
        final EditText input = new EditText(requireContext());
        input.setHint("PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Developer Mode")
                .setMessage("Введите PIN разработчика")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Войти", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (DEV_PIN.equals(input.getText().toString())) {
                dialog.dismiss();
                showDeveloperPanel();
            } else {
                input.setError("Неверный PIN");
            }
        }));
        dialog.show();
    }

    private void showDeveloperPanel() {
        String[] items = {"Добавить программу (.exe)", "Контейнеры и Runtime", "Экран и графика", "Совместимость", "Отладка", "Расширенные настройки"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Developer Mode")
                .setItems(items, (d, which) -> {
                    if (which == 0) pickExe();
                    else Toast.makeText(requireContext(), items[which] + " — позже", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }


    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER);
    }

    private void pickExe() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_EXE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_EXE) {
            importExe(data.getData());
        } else if (requestCode == PICK_FOLDER) {
            Uri tree = data.getData();
            try { requireContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            importFolder(tree);
        }
    }


    private void importFolder(Uri treeUri) {
        Toast.makeText(requireContext(), "Копирование программы…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String folderName = getTreeName(treeUri);
                JSONObject config = new JSONObject();
                config.put("name", folderName);
                config.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
                requireActivity().runOnUiThread(() -> {
                    try {
                        manager.createContainerAsync(config, container -> {
                            if (container == null) {
                                Toast.makeText(requireContext(), "Не удалось создать контейнер", Toast.LENGTH_LONG).show();
                                return;
                            }
                            new Thread(() -> copyTreeIntoContainer(treeUri, container, folderName)).start();
                        });
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Ошибка контейнера: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void copyTreeIntoContainer(Uri treeUri, Container container, String programName) {
        try {
            File appDir = new File(container.getRootDir(), ".wine/drive_c/GRXT/" + safeName(programName));
            if (!appDir.exists() && !appDir.mkdirs()) throw new Exception("Не удалось создать папку программы");
            String[] exePath = new String[1];
            copyDocumentChildren(treeUri, treeUri, appDir, exePath);
            if (exePath[0] == null) throw new Exception("В выбранной папке не найден .exe");
            createShortcut(container, programName, new File(exePath[0]));
            requireActivity().runOnUiThread(() -> {
                manager = new ContainerManager(requireContext());
                refresh();
                Toast.makeText(requireContext(), programName + " установлен", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Ошибка установки: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void copyDocumentChildren(Uri treeUri, Uri parentUri, File dest, String[] exePath) throws Exception {
        String parentId = DocumentsContract.getDocumentId(parentUri);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
        try (Cursor cursor = requireContext().getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            if (cursor == null) throw new Exception("Не удалось прочитать папку");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                Uri child = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                File out = new File(dest, safeName(name));
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!out.exists()) out.mkdirs();
                    copyDocumentChildren(treeUri, child, out, exePath);
                } else {
                    try (InputStream in = requireContext().getContentResolver().openInputStream(child);
                         FileOutputStream fos = new FileOutputStream(out)) {
                        if (in == null) throw new Exception("Не удалось открыть " + name);
                        byte[] buffer = new byte[131072];
                        int n;
                        while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
                    }
                    if (exePath[0] == null && name.toLowerCase().endsWith(".exe")) exePath[0] = out.getAbsolutePath();
                }
            }
        }
    }

    private String getTreeName(Uri treeUri) {
        String id = DocumentsContract.getTreeDocumentId(treeUri);
        int p = id.lastIndexOf(':');
        String name = p >= 0 ? id.substring(p + 1) : id;
        p = name.lastIndexOf('/');
        if (p >= 0) name = name.substring(p + 1);
        return name.isEmpty() ? "Windows Program" : name;
    }

    private String safeName(String name) {
        return name.replaceAll("[\\\\/:*?\\\"<>|]", "_");
    }

    private void createShortcut(Container container, String programName, File exe) {
        File desktop = new File(container.getUserDir(), "Desktop");
        if (!desktop.exists()) desktop.mkdirs();
        File shortcut = new File(desktop, safeName(programName) + ".desktop");
        String winPath = "C:\\\\GRXT\\\\" + safeName(programName) + "\\\\" + exe.getName();
        String content = "[Desktop Entry]\\n" +
                "Name=" + programName + "\\n" +
                "Exec=env WINEPREFIX=\\\"$HOME/.wine\\\" wine " + winPath + "\\n" +
                "Type=Application\\n" +
                "\\n[Extra Data]\\n" +
                "grxtExecPath=" + exe.getAbsolutePath() + "\\n";
        FileUtils.writeString(shortcut, content);
    }

    private void importExe(Uri uri) {
        final String displayName = getDisplayName(uri);
        if (!displayName.toLowerCase().endsWith(".exe")) {
            Toast.makeText(requireContext(), "Выберите файл .exe", Toast.LENGTH_LONG).show();
            return;
        }
        final String programName = displayName.substring(0, displayName.length() - 4);
        Toast.makeText(requireContext(), "Установка " + programName + "…", Toast.LENGTH_SHORT).show();

        try {
            JSONObject config = new JSONObject();
            config.put("name", programName);
            config.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            manager.createContainerAsync(config, container -> {
                if (container == null) {
                    Toast.makeText(requireContext(), "Не удалось создать контейнер", Toast.LENGTH_LONG).show();
                    return;
                }
                new Thread(() -> {
                    try {
                        File appDir = new File(container.getRootDir(), ".wine/drive_c/GRXT");
                        if (!appDir.exists() && !appDir.mkdirs()) throw new Exception("Cannot create C:\\GRXT");
                        File exe = new File(appDir, displayName.replaceAll("[^a-zA-Z0-9._ -]", "_"));
                        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                             FileOutputStream out = new FileOutputStream(exe)) {
                            if (in == null) throw new Exception("Cannot open selected file");
                            byte[] buffer = new byte[1024 * 128];
                            int n;
                            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                        }

                        createShortcut(container, programName, exe);

                        requireActivity().runOnUiThread(() -> {
                            manager = new ContainerManager(requireContext());
                            refresh();
                            Toast.makeText(requireContext(), programName + " установлен", Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Ошибка установки: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getDisplayName(Uri uri) {
        String name = "program.exe";
        Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index);
            } finally {
                cursor.close();
            }
        }
        return name;
    }

    @Override public void onResume() {
        super.onResume();
        if (manager != null) refresh();
    }

    private void refresh() {
        ArrayList<Shortcut> items = manager.loadShortcuts(null);
        items.removeIf(s -> s.file.isDirectory());
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        recyclerView.setAdapter(new Adapter(items));
    }

    private void run(Shortcut shortcut) {
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        String grxtExecPath = shortcut.getExtra("grxtExecPath");
        if (!grxtExecPath.isEmpty()) {
            intent.putExtra("exec_path", grxtExecPath);
        } else {
            intent.putExtra("shortcut_path", shortcut.file.getPath());
        }
        startActivity(intent);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final ArrayList<Shortcut> items;
        Adapter(ArrayList<Shortcut> items) { this.items = items; }
        class Holder extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            TextView title, subtitle;
            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.ProgramIcon);
                title = v.findViewById(R.id.ProgramTitle);
                subtitle = v.findViewById(R.id.ProgramSubtitle);
            }
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.edurunner_program_card, p, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            Shortcut s = items.get(pos);
            if (s.icon != null) h.icon.setImageBitmap(s.icon); else h.icon.setImageResource(R.mipmap.ic_launcher);
            h.title.setText(s.name);
            h.subtitle.setText("Нажмите для запуска");
            h.itemView.setOnClickListener(v -> run(s));
        }
        @Override public int getItemCount() { return items.size(); }
    }
}
