package model;

/** Thrown when a user's role does not allow an operation (role-based access control). */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
