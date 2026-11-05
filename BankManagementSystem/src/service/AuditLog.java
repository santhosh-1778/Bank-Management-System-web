package service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Records critical actions with user and timestamp (non-functional requirement: audit). */
public class AuditLog implements Serializable {
    public static class Entry implements Serializable {
        private final LocalDateTime time = LocalDateTime.now();
        private final String userId;
        private final String action;

        Entry(String userId, String action) { this.userId = userId; this.action = action; }

        public LocalDateTime getTime() { return time; }
        public String getUserId() { return userId; }
        public String getAction() { return action; }

        @Override
        public String toString() { return time.withNano(0) + " | " + userId + " | " + action; }
    }

    private final List<Entry> entries = new ArrayList<>();

    synchronized void log(String userId, String action) { entries.add(new Entry(userId, action)); }

    public synchronized List<Entry> getEntries() { return new ArrayList<>(entries); }
}
