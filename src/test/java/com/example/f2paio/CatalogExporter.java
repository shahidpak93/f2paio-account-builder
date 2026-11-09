package com.example.f2paio;

import java.nio.file.Path;

public final class CatalogExporter
{
    public static void main(String[] args) throws Exception
    {
        FilesStore.write(Path.of(args[0]), Methods.all());
        System.out.println("Catalogue: " + Methods.all().size() + " executable method definitions");
    }
}
