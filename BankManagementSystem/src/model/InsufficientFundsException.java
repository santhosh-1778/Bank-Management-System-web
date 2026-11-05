package model;

/** Checked exception: a withdrawal or transfer would break the balance, minimum balance or overdraft rule. */
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String accountNo, double amount) {
        super("Insufficient funds in " + accountNo + " for amount " + amount);
    }
}
