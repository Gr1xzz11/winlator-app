package com.winlator;

import com.winlator.core.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** File names inside imported programs must never be normalized or renamed. */
public final class EduRunnerProgramFiles {
    private EduRunnerProgramFiles() {}

    public static File checkedChild(File parent, String name) throws IOException {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") ||
                name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            throw new IOException("Некорректное имя файла: " + name);
        }
        File child = new File(parent, name);
        if (!child.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile())) {
            throw new IOException("Файл находится за пределами выбранной папки");
        }
        if (child.exists()) throw new IOException("Повторяющееся имя файла: " + name);
        return child;
    }

    public static String directoryName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        safe = safe.replaceAll("[. ]+$", "");
        return safe.isEmpty() ? "Program" : safe;
    }

    public static String relative(File root, File file) throws IOException {
        java.nio.file.Path base = root.getCanonicalFile().toPath();
        java.nio.file.Path target = file.getCanonicalFile().toPath();
        if (!target.startsWith(base) || target.equals(base)) throw new IOException("EXE находится за пределами папки программы");
        return base.relativize(target).toString().replace(File.separatorChar, '/');
    }

    public static String windowsPath(String directoryName, String relativeExe) {
        return "C:\\GRXT\\" + directoryName + "\\" + relativeExe.replace('/', '\\');
    }

    public static String desktopEntry(String name, String windowsPath) {
        // Wine's desktop files contain four backslashes per DOS separator and one
        // before a space. Shortcut's two unescape passes require that exact encoding.
        // XServerDisplayActivity adds quotes itself; quotes here become part of the path.
        return "[Desktop Entry]\nType=Application\nName=" + name.replace('\n', ' ').replace('\r', ' ') +
                "\nExec=wine " + StringUtils.escapeDOSPath(windowsPath.replace("\\", "\\\\")) + "\n\n[Extra Data]\nedurunner=t\n";
    }

    public static void writeShortcut(File file, String name, String windowsPath) throws IOException {
        Files.write(file.toPath(), desktopEntry(name, windowsPath).getBytes(StandardCharsets.UTF_8));
    }
}
