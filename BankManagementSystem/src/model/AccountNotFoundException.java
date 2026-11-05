package model;

/** Thrown when an account number does not exist. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountNo) {
        super("Account not found: " + accountNo);
    }
}
