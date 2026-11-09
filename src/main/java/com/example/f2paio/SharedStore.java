package com.example.f2paio;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class SharedStore
{
    public static final class Lease
    {
        public String owner;
        public long expires;
        Lease(String owner, long expires) { this.owner = owner; this.expires = expires; }
    }

    public static final class State
    {
        public Map<String, Lease> leases = new HashMap<>();
        public Map<String, Long> failures = new HashMap<>();
    }

    private final Path directory;
    public SharedStore(Path directory) { this.directory = directory; }

    public boolean lease(String spot, String owner, long ttl, long now) throws IOException
    {
        return update(state -> {
            Lease lease = state.leases.get(spot);
            if (lease != null && lease.expires > now && !lease.owner.equals(owner)) return false;
            state.leases.put(spot, new Lease(owner, now + ttl));
            return true;
        }, now);
    }

    public void release(String owner) throws IOException
    {
        update(state -> {
            state.leases.values().removeIf(lease -> lease.owner.equals(owner));
            return true;
        }, System.currentTimeMillis());
    }

    public boolean quarantined(String method, long now) throws IOException
    {
        return update(state -> state.failures.getOrDefault(method, 0L) > now, now);
    }

    public void failure(String method, long now) throws IOException
    {
        update(state -> { state.failures.put(method, now + Tuning.ABANDON_MS); return true; }, now);
    }

    private static final Object PROCESS_LOCK = new Object();
    private boolean update(java.util.function.Function<State, Boolean> operation, long now) throws IOException
    {
        synchronized (PROCESS_LOCK)
        {
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(directory.resolve("facts.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock())
            {
                Path file = directory.resolve("facts.json");
                State state = Files.exists(file) ? FilesStore.read(file, State.class) : new State();
                if (state == null || state.leases == null || state.failures == null)
                    throw new IOException("Shared facts file invalid");
                state.leases.values().removeIf(lease -> lease.expires <= now);
                state.failures.values().removeIf(expiry -> expiry <= now);
                boolean result = operation.apply(state);
                FilesStore.write(file, state);
                return result;
            }
        }
    }
}
