package com.winlator.core;

import java.io.File;

/** Launch the bundled glibc executable without its original package-specific ELF interpreter. */
public final class GuestRuntimeCommand {
    private GuestRuntimeCommand() {}

    public static String create(File rootDir, String guestExecutable) {
        File loader = new File(rootDir, "usr/lib/ld-linux-aarch64.so.1");
        File box64 = new File(rootDir, "usr/local/bin/box64");
        if (!loader.isFile() || !box64.isFile()) {
            throw new IllegalStateException("Runtime files missing: loader=" + loader + "; Box64=" + box64);
        }
        return loader.getAbsolutePath() + " " + box64.getAbsolutePath() + " " + guestExecutable;
    }
}
