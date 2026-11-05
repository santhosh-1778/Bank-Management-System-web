package service;

import model.Account;
import model.CurrentAccount;
import model.SavingsAccount;

/** Factory pattern: callers ask for a type by name and never call account constructors directly. */
public final class AccountFactory {
    public static final double DEFAULT_SAVINGS_RATE = 4.0;      // percent per year
    public static final double DEFAULT_OVERDRAFT_LIMIT = 5000;

    private AccountFactory() { }

    public static Account create(String type, String accountNo, String customerId) {
        if (type == null) throw new IllegalArgumentException("Account type is required");
        switch (type.trim().toUpperCase()) {
            case "SAVINGS": return new SavingsAccount(accountNo, customerId, 0, DEFAULT_SAVINGS_RATE);
            case "CURRENT": return new CurrentAccount(accountNo, customerId, 0, DEFAULT_OVERDRAFT_LIMIT);
            default: throw new IllegalArgumentException("Unknown account type: " + type + " (use SAVINGS or CURRENT)");
        }
    }
}
