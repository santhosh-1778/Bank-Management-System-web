package model;

/** Savings account: pays interest and enforces a minimum balance (FR9, FR16). */
public class SavingsAccount extends Account {
    public static final double MIN_BALANCE = 1000;

    private final double interestRate; // percent per year

    public SavingsAccount(String accountNo, double opening, double interestRate) {
        this(accountNo, null, opening, interestRate);
    }

    public SavingsAccount(String accountNo, String customerId, double opening, double interestRate) {
        super(accountNo, customerId, opening);
        this.interestRate = interestRate;
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return balance - amount >= MIN_BALANCE; // keep the minimum balance
    }

    /** Credits one month of interest. Call once a month. Returns the amount credited. */
    public double addInterest() {
        double interest = Math.round(balance * interestRate / 100 / 12 * 100) / 100.0;
        if (interest > 0) credit(interest, TransactionType.INTEREST);
        return interest;
    }

    public double getInterestRate() { return interestRate; }

    @Override
    public String getType() { return "SAVINGS"; }
}
