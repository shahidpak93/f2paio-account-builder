package com.example.f2paio;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class F2pAioConfig
{
    public boolean autonomous = true;
    public boolean accountBuilder = true;
    public boolean allowPvp = false;
    public int gearReserveCoins = 1000;
    public String llmBackend = "OLLAMA";
    public String llmEndpoint = "http://127.0.0.1:8092/api/generate";
    public String llmModel = "osrs-builder:2b";
    public String personalitySeed = "";
    public String preset = "MAIN";
    public String taskMode = "AIO";
    public String selectedMethod = "trees";
    public String inventoryMode = "BANK";
    public String food = "Trout";
    public int eatAt = 55;
    public int targetLevel = 30;
    public int sessionMax = 90;
    public int breakMax = 20;
    public int alternateMinutes = 15;
    public int idlePct = 12;
    public int activeFrom = 0;
    public int activeTo = 24;
    public boolean llmEnabled = true;
    public boolean localFallbackEnabled = true;
    public String localFallbackModel = "osrs-builder:2b";
    public boolean dropProductsOnly = true;
    public List<String> enabledMethods = new ArrayList<>(List.of("trees", "copper", "shrimp"));
    // GE flipping (requires coins >= flipMinBudget to activate between sessions)
    public boolean enableFlipping = false;
    public int flipMinBudget = 10_000;
    public int flipMinCoinsAfter = 5_000;

    public F2pAioConfig copy()
    {
        return FilesStore.JSON.fromJson(FilesStore.JSON.toJson(this), F2pAioConfig.class);
    }

    public void validate()
    {
        if (!List.of("MAIN", "PURE", "ZERKER", "SKILL_PURE", "MONEY_ALT").contains(preset))
            throw new IllegalArgumentException("Unknown account preset");
        if (!List.of("AIO", "SKILLER", "MONEY", "QUESTER", "PVM").contains(taskMode))
            throw new IllegalArgumentException("Unknown task mode");
        if (!List.of("BANK", "DROP").contains(inventoryMode))
            throw new IllegalArgumentException("Unknown inventory mode");
        if (targetLevel < 1 || targetLevel > 99 || sessionMax < 1 || sessionMax > 480
            || breakMax < 1 || breakMax > 180 || alternateMinutes < 1 || alternateMinutes > 120
            || idlePct < 0 || idlePct > 40 || eatAt < 10 || eatAt > 90
            || activeFrom < 0 || activeFrom > 23 || activeTo < 1 || activeTo > 24)
            throw new IllegalArgumentException("Settings outside supported range");
        if (flipMinBudget < 1000 || flipMinCoinsAfter < 0 || flipMinCoinsAfter >= flipMinBudget)
            throw new IllegalArgumentException("Flip start budget must exceed the Finish threshold (minimum start 1000 GP)");
        if (!autonomous && (enabledMethods == null || enabledMethods.isEmpty()))
            throw new IllegalArgumentException("Enable at least one activity");
        if (!autonomous && !enabledMethods.contains(selectedMethod))
            throw new IllegalArgumentException("Selected method must be enabled");
        if (enabledMethods != null) for (String id : enabledMethods) Methods.get(id);
        if (localFallbackModel==null || !localFallbackModel.matches("[A-Za-z0-9._:/-]{1,160}"))
            throw new IllegalArgumentException("Invalid local fallback model");
        if (llmEnabled)
        {
            if (!List.of("OLLAMA", "LM_STUDIO", "GEMINI", "OPENAI_COMPATIBLE").contains(llmBackend))
                throw new IllegalArgumentException("Unknown LLM backend");
            URI uri = URI.create(llmEndpoint.replace("{model}", "model"));
            boolean local = uri.getHost() != null
                && List.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost());
            if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme()))))
                throw new IllegalArgumentException("Use HTTPS or localhost HTTP, without credentials/query/fragment");
            if ("GEMINI".equals(llmBackend) && !"generativelanguage.googleapis.com".equals(uri.getHost()))
                throw new IllegalArgumentException("Gemini requires its Google API endpoint");
            if (llmModel == null || !llmModel.matches("[A-Za-z0-9._:/-]{1,160}"))
                throw new IllegalArgumentException("Invalid model name");
        }
    }
}
