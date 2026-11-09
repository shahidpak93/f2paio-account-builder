package com.example.f2paio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public final class SecretStore
{
    private static final Path FILE = FilesStore.ROOT.resolve("llm-key.dpapi");

    public static void save(String secret) throws IOException, InterruptedException
    {
        String protectedText = transform(secret, false);
        FilesStore.write(FILE, protectedText);
    }

    public static String load() throws IOException, InterruptedException
    {
        if (!Files.exists(FILE)) return "";
        return transform(FilesStore.read(FILE, String.class), true);
    }

    public static void forget() throws IOException { Files.deleteIfExists(FILE); }

    private static String transform(String input, boolean decrypt) throws IOException, InterruptedException
    {
        if (!System.getProperty("os.name").startsWith("Windows"))
            throw new IOException("Remember key requires Windows DPAPI");
        String preamble = "$s=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([Console]::ReadLine()));";
        String script = decrypt
            ? preamble + "$v=ConvertTo-SecureString $s; $p=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($v);"
                + "try{$o=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($p);"
                + "[Console]::Write([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($o)))}"
                + "finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($p)}"
            : preamble + "[Console]::Write(($s | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString))";
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
            "-WindowStyle", "Hidden", "-Command", script).redirectError(ProcessBuilder.Redirect.DISCARD);
        // PowerShell 7 module paths inherited by Java can break Windows PowerShell's DPAPI cmdlets.
        builder.environment().remove("PSModulePath");
        Process process = builder.start();
        try
        {
            try (var stdin = process.getOutputStream())
            {
                stdin.write((Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8))
                    + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("DPAPI operation timed out");
            if (process.exitValue() != 0) throw new IOException("DPAPI operation failed");
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            return decrypt ? new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) : value;
        }
        finally
        {
            process.destroyForcibly();
        }
    }

    private SecretStore() { }
}
