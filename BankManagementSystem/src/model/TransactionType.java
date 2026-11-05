package model;

/** Kinds of entries that can appear on an account statement. */
public enum TransactionType {
    DEPOSIT(true), WITHDRAW(false), TRANSFER_IN(true), TRANSFER_OUT(false),
    INTEREST(true), LOAN_DISBURSEMENT(true), LOAN_REPAYMENT(false);

    private final boolean credit;

    TransactionType(boolean credit) { this.credit = credit; }

    /** True if this type increases the account balance. */
    public boolean isCredit() { return credit; }
}
