package service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import model.SessionExpiredException;
import model.User;

/** A logged-in user. Sessions expire after a period of inactivity (non-functional requirement: security). */
public class Session {
    private final User user;
    private final Duration timeout;
    private final Clock clock;
    private Instant lastActive;
    private boolean loggedOut;

    public Session(User user, Duration timeout, Clock clock) {
        this.user = user;
        this.timeout = timeout;
        this.clock = clock;
        this.lastActive = clock.instant();
    }

    /** Called by the service before every operation: throws if expired, otherwise refreshes the idle timer. */
    public void validate() {
        if (loggedOut || Duration.between(lastActive, clock.instant()).compareTo(timeout) > 0) {
            loggedOut = true;
            throw new SessionExpiredException();
        }
        lastActive = clock.instant();
    }

    public void logout() { loggedOut = true; }

    public User getUser() { return user; }
}
