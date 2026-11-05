package model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for all accounts.
 * Encapsulation: the balance is protected and changes only through deposit/withdraw/transfer methods.
 * Polymorphism: each subclass decides {@link #canWithdraw(double)} (template-method hook).
 */
public abstract class Account implements Serializable {
    private static final double EPS = 0.005;

    protected final String accountNo;
    private final String customerId;
    protected double balance;
    protected final LocalDate openDate = LocalDate.now();
    private AccountStatus status = AccountStatus.ACTIVE;
    private LocalDateTime lastActivity = LocalDateTime.now();
    private final List<Transaction> history = new ArrayList<>();

    protected Account(String accountNo, String customerId, double openingBalance) {
        this.accountNo = accountNo;
        this.customerId = customerId;
        this.balance = openingBalance;
    }

    // ---- operations -------------------------------------------------------------------------

    public void deposit(double amount) { credit(amount, TransactionType.DEPOSIT); }

    public void withdraw(double amount) throws InsufficientFundsException { debit(amount, TransactionType.WITHDRAW); }

    public void transferIn(double amount) { credit(amount, TransactionType.TRANSFER_IN); }

    public void transferOut(double amount) throws InsufficientFundsException { debit(amount, TransactionType.TRANSFER_OUT); }

    /** Money paid out by the bank when a loan is disbursed into this account. */
    public void receiveLoan(double amount) { credit(amount, TransactionType.LOAN_DISBURSEMENT); }

    /** Loan instalment paid from this account. */
    public void payLoan(double amount) throws InsufficientFundsException { debit(amount, TransactionType.LOAN_REPAYMENT); }

    protected void credit(double amount, TransactionType type) {
        validateAmount(amount);
        ensureUsable();
        balance += amount;
        record(type, amount);
    }

    private void debit(double amount, TransactionType type) throws InsufficientFundsException {
        validateAmount(amount);
        ensureUsable();
        if (!canWithdraw(amount)) throw new InsufficientFundsException(accountNo, amount);
        balance -= amount;
        record(type, amount);
    }

    /** Subclasses define the withdrawal rule (minimum balance, overdraft ...). */
    protected abstract boolean canWithdraw(double amount);

    /** "SAVINGS" or "CURRENT". */
    public abstract String getType();

    /** Throws if this account cannot take part in a transaction, without changing anything. */
    public void ensureUsable() {
        if (status == AccountStatus.CLOSED || status == AccountStatus.FROZEN) {
            throw new IllegalStateException("Account " + accountNo + " is " + status);
        }
        if (status == AccountStatus.DORMANT) status = AccountStatus.ACTIVE; // a customer transaction wakes it up
    }

    /** Removes the most recent entry and restores the balance. Used to roll back a half-finished transfer. */
    public void undoLast() {
        if (history.isEmpty()) throw new IllegalStateException("Nothing to undo");
        Transaction t = history.remove(history.size() - 1);
        balance += t.getType().isCredit() ? -t.getAmount() : t.getAmount();
    }

    private void record(TransactionType type, double amount) {
        history.add(new Transaction(type, amount, balance));
        lastActivity = LocalDateTime.now();
    }

    private static void validateAmount(double amount) {
        if (!(amount > 0) || Double.isInfinite(amount)) throw new IllegalArgumentException("Amount must be positive");
    }

    // ---- life cycle -------------------------------------------------------------------------

    /** FR4: only a zero balance account can be closed (the "no active loan" rule is checked by the service). */
    public void close() {
        if (Math.abs(balance) >= EPS) {
            throw new IllegalStateException("Cannot close account " + accountNo + ": balance must be zero (current " + balance + ")");
        }
        status = AccountStatus.CLOSED;
    }

    public void freeze() { if (status != AccountStatus.CLOSED) status = AccountStatus.FROZEN; }

    public void unfreeze() { if (status == AccountStatus.FROZEN) status = AccountStatus.ACTIVE; }

    /** Marks an ACTIVE account DORMANT after 12 months without activity. */
    public boolean markDormantIfInactive(LocalDateTime now) {
        if (status == AccountStatus.ACTIVE && ChronoUnit.MONTHS.between(lastActivity, now) >= 12) {
            status = AccountStatus.DORMANT;
            return true;
        }
        return false;
    }

    // ---- queries ----------------------------------------------------------------------------

    public double getBalance() { return balance; }
    public String getAccountNo() { return accountNo; }
    public String getCustomerId() { return customerId; }
    public LocalDate getOpenDate() { return openDate; }
    public AccountStatus getStatus() { return status; }
    public List<Transaction> getHistory() { return List.copyOf(history); }

    /** FR10: the last n transactions, oldest first. */
    public List<Transaction> getMiniStatement(int n) {
        int from = Math.max(0, history.size() - n);
        return List.copyOf(history.subList(from, history.size()));
    }

    /** FR11: all transactions between two dates (inclusive). */
    public List<Transaction> getStatement(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date is after end date");
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : history) {
            LocalDate d = t.getDate().toLocalDate();
            if (!d.isBefore(from) && !d.isAfter(to)) result.add(t);
        }
        return result;
    }
}
