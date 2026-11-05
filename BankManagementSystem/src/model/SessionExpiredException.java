package model;

/** Thrown when a session has been idle for longer than the timeout. */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException() { super("Session timed out due to inactivity. Please log in again."); }
}
