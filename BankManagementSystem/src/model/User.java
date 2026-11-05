package model;

import java.io.Serializable;

/** Login credentials and role (FR17-FR19). Customer and Employee stay free of password logic. */
public class User implements Serializable {
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final String userId;
    private final Role role;
    private final String customerId; // only for CUSTOMER users
    private final String salt = PasswordHasher.newSalt();
    private String passwordHash;
    private int failedAttempts;
    private boolean locked;
    private boolean enabled = true;

    public User(String userId, String password, Role role, String customerId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User ID is required");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
        if (role == Role.CUSTOMER && (customerId == null || customerId.isBlank())) {
            throw new IllegalArgumentException("A customer user must be linked to a customer");
        }
        this.userId = userId.trim();
        this.role = role;
        this.customerId = role == Role.CUSTOMER ? customerId : null;
        this.passwordHash = PasswordHasher.hash(password, salt);
    }

    /** Returns true on success. Three wrong passwords in a row lock the user. */
    public boolean login(String password) {
        if (locked || !enabled) return false;
        if (password != null && PasswordHasher.matches(password, salt, passwordHash)) {
            failedAttempts = 0;
            return true;
        }
        if (++failedAttempts >= MAX_FAILED_ATTEMPTS) lock();
        return false;
    }

    public void lock() { locked = true; }

    public void unlock() { locked = false; failedAttempts = 0; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getCustomerId() { return customerId; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isLocked() { return locked; }
    public boolean isEnabled() { return enabled; }
    public String getPasswordHash() { return passwordHash; }
}
