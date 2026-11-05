package service;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.InvalidLoginException;
import model.Role;
import model.User;

/** Login, lock-out and user storage (FR17, FR19). */
public class AuthService implements Serializable {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final Map<String, User> users = new LinkedHashMap<>();

    public synchronized Session login(String userId, String password) {
        return login(userId, password, Clock.systemDefaultZone(), DEFAULT_TIMEOUT);
    }

    public synchronized Session login(String userId, String password, Clock clock, Duration timeout) {
        User u = userId == null ? null : users.get(userId.trim());
        if (u == null) throw new InvalidLoginException("Invalid user ID or password");
        if (!u.isEnabled()) throw new InvalidLoginException("This user is disabled");
        if (u.isLocked()) throw new InvalidLoginException("This user is locked. Ask a manager to unlock it");
        if (!u.login(password)) {
            if (u.isLocked()) throw new InvalidLoginException("Too many failed attempts. The user is now locked");
            throw new InvalidLoginException("Invalid user ID or password");
        }
        return new Session(u, timeout, clock);
    }

    synchronized User add(User u) {
        if (users.containsKey(u.getUserId())) throw new IllegalArgumentException("User ID already exists: " + u.getUserId());
        users.put(u.getUserId(), u);
        return u;
    }

    synchronized User find(String userId) {
        User u = users.get(userId);
        if (u == null) throw new IllegalArgumentException("No such user: " + userId);
        return u;
    }

    synchronized boolean isEmpty() { return users.isEmpty(); }

    synchronized List<User> list() { return new ArrayList<>(users.values()); }

    synchronized User create(String userId, String password, Role role, String customerId) {
        return add(new User(userId, password, role, customerId));
    }
}
