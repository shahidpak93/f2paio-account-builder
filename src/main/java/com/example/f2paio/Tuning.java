package com.example.f2paio;

public final class Tuning
{
    public static final String VERSION = "0.14.0";
    public static final long POLL_MS = 200;
    public static final long MINUTE = 60_000;
    public static final long RETRY_MS = 45_000;
    public static final long REROUTE_MS = 240_000;
    public static final long ABANDON_MS = 20 * MINUTE;
    public static final long LOGOUT_MS = 60 * MINUTE;
    public static final long ACTION_SETTLE_MS = 2_400;
    public static final long AGENT_TIMEOUT_MS = 45_000;
    public static final int AGENT_HOURLY_CAP = 10;
    public static final int AGENT_DAILY_CAP = 40;
    public static final int DIALOGUE_REPEAT_CAP = 40;
    public static final int INTERACTION_RANGE = 12;
    public static final int TRAVEL_RADIUS = 6;
    public static final int INVENTORY_CAPACITY = 28;
    public static final int MAX_RESPONSE_BYTES = 262_144;
    public static final int RECORDER_CAPACITY = 900;
    public static final int TAN_SOFT_ALL = 21233788;
    public static final int TAN_HARD_ALL = 21233789;

    private Tuning() { }
}

