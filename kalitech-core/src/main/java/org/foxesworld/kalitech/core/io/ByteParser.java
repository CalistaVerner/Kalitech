package org.foxesworld.kalitech.core.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Abstract base for parsing byte data from various sources into objects of type T.
 * Provides overloads for byte arrays, streams, files, and paths.
 * <p>
 * - All external inputs are checked for null.
 * - Exceptions are logged.
 * - All resources are closed safely.
 * - Supports subclass testability and extension.
 */
public abstract class ByteParser<T> {
    private static final Logger logger = LoggerFactory.getLogger(ByteParser.class);

    /**
     * Parse the given byte array into an object of type T.
     * @param data must not be null
     * @return parsed object
     * @throws IOException on parse error
     */
    protected abstract T parseBytes(byte[] data) throws IOException;

    /**
     * Parse from byte array.
     */
    public T parse(byte[] data) throws IOException {
        Objects.requireNonNull(data, "Data byte array cannot be null");
        try {
            return parseBytes(data);
        } catch (IOException | RuntimeException ex) {
            logger.error("Failed to parse from byte array ({} bytes): {}", data.length, ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Parse from InputStream. Stream will be closed automatically.
     */
    public T parse(InputStream input) throws IOException {
        Objects.requireNonNull(input, "InputStream cannot be null");
        try (InputStream in = input) {
            byte[] data = in.readAllBytes();
            return parseBytes(data);
        } catch (IOException | RuntimeException ex) {
            logger.error("Failed to parse from InputStream: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Parse from File. File must exist and be readable.
     */
    public T parse(File file) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");
        if (!file.exists()) {
            logger.error("File does not exist: {}", file.getAbsolutePath());
            throw new FileNotFoundException("File does not exist: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            logger.error("File cannot be read: {}", file.getAbsolutePath());
            throw new IOException("File cannot be read: " + file.getAbsolutePath());
        }
        try (InputStream in = new FileInputStream(file)) {
            return parse(in);
        }
    }

    /**
     * Parse from Path. Path must exist and be readable.
     */
    public T parse(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        if (!Files.exists(path)) {
            logger.error("Path does not exist: {}", path);
            throw new FileNotFoundException("Path does not exist: " + path);
        }
        if (!Files.isReadable(path)) {
            logger.error("Path is not readable: {}", path);
            throw new IOException("Path is not readable: " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in);
        }
    }
}