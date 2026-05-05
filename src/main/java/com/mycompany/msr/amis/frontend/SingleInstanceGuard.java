package com.mycompany.msr.amis;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SingleInstanceGuard {

    private static final Path LOCK_FILE = Path.of(System.getProperty("user.home"), ".msr-amis", "msr-amis.lock");

    private static FileChannel channel;
    private static FileLock lock;

    private SingleInstanceGuard() {
    }

    public static boolean acquire() {
        try {
            Files.createDirectories(LOCK_FILE.getParent());
            channel = FileChannel.open(
                    LOCK_FILE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
            lock = channel.tryLock();
            return lock != null;
        } catch (IOException | RuntimeException exception) {
            release();
            return false;
        }
    }

    public static void release() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
        } finally {
            lock = null;
        }

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
        } finally {
            channel = null;
        }
    }
}
