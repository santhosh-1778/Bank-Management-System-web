package model;

/** Thrown for wrong credentials, a locked user or a disabled user. */
public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException(String message) { super(message); }
}
