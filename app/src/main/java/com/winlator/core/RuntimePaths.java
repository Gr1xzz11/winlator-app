package com.winlator.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Relocate bundled runtime paths without changing binary offsets or string lengths. */
public final class RuntimePaths {
    private static final byte[] ORIGINAL = "/data/data/com.winlator/files/rootfs".getBytes(StandardCharsets.UTF_8);
    private static File root;
    private static byte[] replacement;

    private RuntimePaths() {}

    public static synchronized void initialize(File filesDir) throws IOException {
        root = new File(filesDir, "rootfs");
        Path alias = new File(filesDir.getParentFile(), "r").toPath();
        String path = alias.toAbsolutePath().toString();
        byte[] value = path.getBytes(StandardCharsets.UTF_8);
        if (value.length > ORIGINAL.length) throw new IOException("Runtime alias is too long: " + path);
        if (Files.isSymbolicLink(alias)) {
            if (!Files.readSymbolicLink(alias).equals(root.toPath())) {
                Files.delete(alias);
                Files.createSymbolicLink(alias, root.toPath());
            }
        } else if (Files.exists(alias)) {
            throw new IOException("Runtime alias already occupied: " + path);
        } else {
            Files.createSymbolicLink(alias, root.toPath());
        }
        replacement = new byte[ORIGINAL.length];
        Arrays.fill(replacement, (byte)'/');
        System.arraycopy(value, 0, replacement, 0, value.length);
    }

    public static synchronized void migrate() throws IOException {
        if (replacement == null) throw new IOException("Runtime paths not initialized");
        File marker = new File(root, ".edurunner-paths-v1");
        if (marker.isFile()) return;
        for (String directory : new String[]{"usr", "opt", "etc", "var"}) migrateTree(new File(root, directory));
        Files.write(marker.toPath(), replacement);
    }

    private static void migrateTree(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) {
            Path old = Files.readSymbolicLink(file.toPath());
            String updated = relocatePath(old.toString());
            if (!updated.equals(old.toString())) {
                Files.delete(file.toPath());
                Files.createSymbolicLink(file.toPath(), new File(updated).toPath());
            }
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file);
            for (File child : children) migrateTree(child);
        } else if (file.isFile()) patchExtracted(file);
    }

    public static String relocatePath(String path) {
        byte[] target = replacement;
        return target == null ? path : path.replace(new String(ORIGINAL, StandardCharsets.UTF_8),
                new String(target, StandardCharsets.UTF_8));
    }

    public static void patchExtracted(File file) throws IOException {
        byte[] target = replacement;
        if (target == null || !file.getAbsolutePath().startsWith(root.getAbsolutePath() + File.separator)) return;
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile()) return;
        patch(file, ORIGINAL, target);
    }

    // Read bounded overlapping blocks, including matches crossing a block boundary.
    public static void patch(File file, byte[] source, byte[] target) throws IOException {
        if (source.length == 0 || source.length != target.length) throw new IOException("Path sizes differ");
        try (RandomAccessFile data = new RandomAccessFile(file, "rw")) {
            byte[] block = new byte[65536 + source.length - 1];
            long position = 0, length = data.length();
            while (position < length) {
                int size = (int)Math.min(block.length, length - position);
                data.seek(position);
                data.readFully(block, 0, size);
                int limit = (int)Math.min(65536, length - position - source.length + 1);
                for (int i = 0; i < limit; i++) {
                    if (block[i] != source[0]) continue;
                    int j = 1;
                    while (j < source.length && block[i + j] == source[j]) j++;
                    if (j == source.length) {
                        data.seek(position + i);
                        data.write(target);
                        i += source.length - 1;
                    }
                }
                position += 65536;
            }
        }
    }
}
