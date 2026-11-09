package com.example.f2paio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public final class Persona
{
    public String id;
    public long seed;
    public double speed;
    public double patience;
    public double curiosity;
    public double endurance;
    public String archetype;

    public static Persona load(String account, String override) throws IOException
    {
        String id = hash(account.trim().toLowerCase(java.util.Locale.ROOT));
        Path path = FilesStore.ROOT.resolve("personas").resolve(id + ".json");
        if (Files.exists(path))
        {
            Persona result = FilesStore.read(path, Persona.class);
            if (result == null || !id.equals(result.id) || !Double.isFinite(result.speed)
                || result.speed <= 0 || result.speed > 2 || !Double.isFinite(result.endurance)
                || result.endurance < 0 || result.endurance > 1)
                throw new IOException("Persona file invalid; repair it before starting");
            return result;
        }
        Persona result = new Persona();
        result.id = id;
        result.seed = (override.isBlank() ? id : hash(override)).hashCode();
        Random random = new Random(result.seed);
        result.speed = Math.max(0.55, Math.min(1.5, 0.95 + random.nextGaussian() * 0.18));
        result.patience = random.nextDouble();
        result.curiosity = random.nextDouble();
        result.endurance = random.nextDouble();
        result.archetype = new String[]{"SKILLER", "CASUAL", "MONEY", "QUESTER"}[random.nextInt(4)];
        FilesStore.write(path, result);
        return result;
    }

    public static String hash(String value)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : bytes) text.append(String.format("%02x", b & 255));
            return text.substring(0, 24);
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
