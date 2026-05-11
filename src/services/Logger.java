package services;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;

public class Logger {
    private static PrintWriter out;
    private static final Object lock = new Object();
    private static boolean verbose = false;

    public static void init() {
        init("app");
    }

    public static void init(String name) {
        synchronized (lock) {
            try {
                Path dataDir = Paths.get("data");
                if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
                Path logsDir = dataDir.resolve("logs");
                if (!Files.exists(logsDir)) Files.createDirectories(logsDir);
                String ts = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").format(LocalDateTime.now());
                Path logFile = logsDir.resolve(name + "-" + ts + ".log");
                out = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), true)), true);
                log("---- Log started: " + LocalDateTime.now() + " ----");
            } catch (IOException ex) {
                ex.printStackTrace();
                out = new PrintWriter(System.err, true);
            }
        }
    }

    public static void log(String msg) {
        synchronized (lock) {
            if (out == null) init();
            String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            out.println(ts + " " + Thread.currentThread().getName() + " - " + msg);
        }
    }

    // Verbose logging (disabled by default) for high-volume debug messages
    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static void logVerbose(String msg) {
        synchronized (lock) {
            if (!verbose) return;
            if (out == null) init();
            String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            out.println(ts + " " + Thread.currentThread().getName() + " - " + msg);
        }
    }

    public static void close() {
        synchronized (lock) {
            if (out != null) {
                out.flush();
                out.close();
                out = null;
            }
        }
    }
}
