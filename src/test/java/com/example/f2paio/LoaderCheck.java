package com.example.f2paio;

import java.nio.file.Path;
import java.util.jar.JarFile;

public final class LoaderCheck
{
    public static void main(String[] args) throws Exception
    {
        Path file = Path.of(args[0]);
        try (JarFile jar = new JarFile(file.toFile()))
        {
            if (!"com.example.f2paio.F2pAioPlugin".equals(jar.getManifest().getMainAttributes()
                .getValue("Microbot-Plugin-Class"))) throw new AssertionError("Plugin manifest missing");
            var entries = jar.entries();
            while (entries.hasMoreElements())
            {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                if (!entry.getName().startsWith("com/example/f2paio/"))
                    throw new AssertionError("Shared dependency bundled: " + entry.getName());
                try (var input = jar.getInputStream(entry))
                {
                    byte[] header = input.readNBytes(8);
                    if (((header[6] & 255) << 8 | (header[7] & 255)) != 55)
                        throw new AssertionError("Expected Java 11 bytecode");
                }
            }
        }
        Class<?> artifact = Class.forName("net.runelite.client.plugins.microbot.externalplugins.LivePluginArtifact");
        var read = artifact.getDeclaredMethod("read", Path.class); read.setAccessible(true);
        try (AutoCloseable loaded = (AutoCloseable) read.invoke(null, file))
        {
            System.out.println("PASS Microbot native artifact loader, manifest and Java 11 packaging");
        }
    }
}
