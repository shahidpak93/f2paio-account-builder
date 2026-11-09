package com.example.f2paio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class FilesStore
{
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path ROOT = Path.of(System.getProperty("user.home"), ".runelite", "f2paio");

    private FilesStore() { }

    public static <T> T read(Path path, Class<T> type) throws IOException
    {
        return JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
    }

    public static void write(Path path, Object value) throws IOException
    {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "aio-", ".tmp");
        try
        {
            Files.writeString(temporary, JSON.toJson(value), StandardCharsets.UTF_8);
            try
            {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ex)
            {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    public static synchronized void append(Path path, Object value) throws IOException
    {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new Gson().toJson(value) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
