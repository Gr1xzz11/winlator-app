package com.winlator;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** SAF URIs end at this boundary. Consumers receive only local files. */
public final class EduRunnerFolderImporter {
    public static final class Result {
        public final String name;
        public final File root;
        public final ArrayList<File> executables = new ArrayList<>();
        private Result(String name, File root) { this.name = name; this.root = root; }
    }

    private final ContentResolver resolver;

    public EduRunnerFolderImporter(ContentResolver resolver) { this.resolver = resolver; }

    public Result copy(Uri tree, File localRoot) throws IOException {
        String id = DocumentsContract.getTreeDocumentId(tree);
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        String name;
        try (Cursor cursor = resolver.query(document, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Не удалось прочитать выбранную папку");
            if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1))) throw new IOException("Выберите папку программы");
            name = cursor.getString(0);
        }
        if (name == null || name.trim().isEmpty()) name = "Program";
        Result result = new Result(name, localRoot);
        copyDirectory(tree, id, localRoot, result.executables, new HashSet<>());
        result.executables.sort((a, b) -> a.getPath().compareToIgnoreCase(b.getPath()));
        return result;
    }

    private void copyDirectory(Uri tree, String id, File destination, ArrayList<File> executables,
                               HashSet<String> visited) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Импорт прерван");
        if (!visited.add(id)) throw new IOException("Повторяющаяся папка в источнике");
        if (!destination.isDirectory() && !destination.mkdirs()) throw new IOException("Не удалось создать " + destination);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        try (Cursor cursor = resolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null) throw new IOException("Нет доступа к содержимому папки");
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                File target = EduRunnerProgramFiles.checkedChild(destination, cursor.getString(1));
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    copyDirectory(tree, childId, target, executables, visited);
                } else {
                    copyFile(DocumentsContract.buildDocumentUriUsingTree(tree, childId), target);
                    if (target.getName().toLowerCase(Locale.ROOT).endsWith(".exe")) executables.add(target);
                }
            }
        }
    }

    private void copyFile(Uri uri, File target) throws IOException {
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Не удалось открыть " + target.getName());
            byte[] buffer = new byte[65536];
            int size;
            while ((size = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Импорт прерван");
                output.write(buffer, 0, size);
            }
        }
    }
}
