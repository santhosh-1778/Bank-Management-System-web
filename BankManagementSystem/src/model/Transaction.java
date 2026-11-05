package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** One immutable ledger entry (FR8): id, type, amount, date/time and the resulting balance. */
public class Transaction implements Serializable {
    private static final AtomicInteger counter = new AtomicInteger();

    private final int txnId = counter.incrementAndGet();
    private final TransactionType type;
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime date = LocalDateTime.now();

    public Transaction(TransactionType type, double amount, double balanceAfter) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    /** Keeps ids unique after data is loaded from disk. */
    public static void ensureCounterAtLeast(int value) {
        counter.accumulateAndGet(value, Math::max);
    }

    public int getTxnId() { return txnId; }
    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public LocalDateTime getDate() { return date; }

    public String getDetails() {
        return txnId + " | " + type + " | " + amount + " | " + balanceAfter + " | " + date;
    }

    @Override
    public String toString() { return getDetails(); }
}
