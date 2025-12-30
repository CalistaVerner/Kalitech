package org.foxesworld.kalitech.engine.perf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public final class PerfFileWriter implements Closeable {

    private final BufferedWriter out;

    public PerfFileWriter(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        this.out = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        // header
        writeRaw("{\"type\":\"meta\",\"started\":\"" + Instant.now() + "\"}");
    }

    public synchronized void writeRaw(String jsonLine) {
        try {
            out.write(jsonLine);
            out.newLine();
        } catch (IOException ignored) {}
    }

    public synchronized void flush() {
        try { out.flush(); } catch (IOException ignored) {}
    }

    @Override
    public synchronized void close() {
        try {
            writeRaw("{\"type\":\"meta\",\"ended\":\"" + Instant.now() + "\"}");
            out.flush();
            out.close();
        } catch (IOException ignored) {}
    }
}