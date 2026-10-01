package com.winlator.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Relocate Box64's ELF interpreter so both initial launch and Wine re-exec work. */
public final class GuestRuntimeCommand {
    private GuestRuntimeCommand() {}

    public static String create(File rootDir, String guestExecutable) {
        File loader = new File(rootDir, "usr/lib/ld-linux-aarch64.so.1");
        File box64 = new File(rootDir, "usr/local/bin/box64");
        if (!loader.isFile() || !box64.isFile()) {
            throw new IllegalStateException("Runtime files missing: loader=" + loader + "; Box64=" + box64);
        }
        try {
            relocateInterpreter(box64, loader.getAbsolutePath());
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot relocate Box64 interpreter: " + box64, e);
        }
        return box64.getAbsolutePath() + " " + guestExecutable;
    }

    private static synchronized void relocateInterpreter(File executable, String loader) throws IOException {
        byte[] target = (loader + "\0").getBytes(StandardCharsets.UTF_8);
        try (RandomAccessFile file = new RandomAccessFile(executable, "rw")) {
            byte[] bytes = new byte[64];
            file.readFully(bytes);
            ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (header.getInt(0) != 0x464c457f || bytes[4] != 2 || bytes[5] != 1 || header.getShort(18) != 183) {
                throw new IOException("Expected ARM64 little-endian ELF64");
            }
            long table = header.getLong(32);
            int stride = Short.toUnsignedInt(header.getShort(54));
            int count = Short.toUnsignedInt(header.getShort(56));
            if (stride < 56 || count == 0 || table < 64 || table > file.length() - (long)stride * count) {
                throw new IOException("Invalid ELF program headers");
            }
            for (int i = 0; i < count; i++) {
                long position = table + (long)i * stride;
                byte[] programBytes = new byte[56];
                file.seek(position);
                file.readFully(programBytes);
                ByteBuffer program = ByteBuffer.wrap(programBytes).order(ByteOrder.LITTLE_ENDIAN);
                if (program.getInt(0) != 3) continue; // PT_INTERP
                long offset = program.getLong(8), size = program.getLong(32);
                if (size <= 0 || size > 4096 || offset < 0 || offset > file.length() - size) {
                    throw new IOException("Invalid ELF interpreter");
                }
                byte[] current = new byte[(int)size];
                file.seek(offset);
                file.readFully(current);
                if (Arrays.equals(current, target)) return;
                // Append rather than overwrite adjacent ELF sections when the new path is longer.
                long newOffset = file.length();
                file.seek(newOffset);
                file.write(target);
                file.getFD().sync();
                program.putLong(8, newOffset);
                program.putLong(32, target.length);
                program.putLong(40, target.length);
                file.seek(position);
                file.write(programBytes);
                file.getFD().sync();
                return;
            }
            throw new IOException("ELF interpreter missing");
        }
    }
}
