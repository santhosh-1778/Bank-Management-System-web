package model;

/** Current account: no minimum balance, but an overdraft limit. */
public class CurrentAccount extends Account {
    private final double overdraftLimit;

    public CurrentAccount(String accountNo, double opening, double overdraftLimit) {
        this(accountNo, null, opening, overdraftLimit);
    }

    public CurrentAccount(String accountNo, String customerId, double opening, double overdraftLimit) {
        super(accountNo, customerId, opening);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    protected boolean canWithdraw(double amount) {
        return balance + overdraftLimit >= amount; // overdraft allowed
    }

    public boolean checkOverdraft() { return balance < 0; }

    public double getOverdraftLimit() { return overdraftLimit; }

    @Override
    public String getType() { return "CURRENT"; }
}
