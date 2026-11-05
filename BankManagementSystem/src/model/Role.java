package model;

/** Roles for role-based access control (FR18). */
public enum Role {
    CUSTOMER, TELLER, MANAGER;

    /** Tellers and managers are bank staff. A manager can do everything a teller can. */
    public boolean isStaff() { return this != CUSTOMER; }
}
