package model;

import java.io.Serializable;
import java.time.LocalDate;

/** A loan application and its repayment tracking (FR12-FR15). */
public class Loan implements Serializable {
    private static final double EPS = 0.005;

    private final String loanId;
    private final String customerId;
    private final String accountNo;
    private final double principal;
    private final double rate;          // percent per year
    private final int tenureMonths;
    private final String purpose;
    private final LocalDate appliedOn = LocalDate.now();
    private LoanStatus status = LoanStatus.APPLIED;
    private double outstanding;

    public Loan(String loanId, String customerId, String accountNo, double principal, double rate, int tenureMonths, String purpose) {
        if (!(principal > 0)) throw new IllegalArgumentException("Loan amount must be positive");
        if (tenureMonths < 1 || tenureMonths > 360) throw new IllegalArgumentException("Tenure must be 1 to 360 months");
        if (rate < 0) throw new IllegalArgumentException("Rate cannot be negative");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("Purpose is required");
        this.loanId = loanId;
        this.customerId = customerId;
        this.accountNo = accountNo;
        this.principal = principal;
        this.rate = rate;
        this.tenureMonths = tenureMonths;
        this.purpose = purpose.trim();
    }

    /** FR13: EMI = P*r*(1+r)^n / ((1+r)^n - 1), r = monthly rate. Rounded to 2 decimals. */
    public static double calculateEMI(double principal, double annualRatePercent, int months) {
        double r = annualRatePercent / 12 / 100;
        double emi = (r == 0) ? principal / months
                : principal * r * Math.pow(1 + r, months) / (Math.pow(1 + r, months) - 1);
        return Math.round(emi * 100) / 100.0;
    }

    public double calculateEMI() { return calculateEMI(principal, rate, tenureMonths); }

    /** Manager approves an application. */
    public void approve() { require(LoanStatus.APPLIED); status = LoanStatus.APPROVED; }

    /** Manager rejects an application. */
    public void reject() { require(LoanStatus.APPLIED); status = LoanStatus.REJECTED; }

    /** Called once the amount has been credited to the customer's account. */
    public void markDisbursed() {
        require(LoanStatus.APPROVED);
        status = LoanStatus.DISBURSED;
        outstanding = Math.round(calculateEMI() * tenureMonths * 100) / 100.0; // total payable
    }

    /** FR15: records a repayment; the loan closes when nothing is outstanding. */
    public void applyRepayment(double amount) {
        require(LoanStatus.DISBURSED);
        if (!(amount > 0)) throw new IllegalArgumentException("Amount must be positive");
        if (amount > outstanding + EPS) throw new IllegalArgumentException("Amount exceeds outstanding balance " + outstanding);
        outstanding = Math.round((outstanding - amount) * 100) / 100.0;
        if (outstanding < EPS) {
            outstanding = 0;
            status = LoanStatus.CLOSED;
        }
    }

    /** True while the loan still blocks closing the account. */
    public boolean isActive() { return status != LoanStatus.CLOSED && status != LoanStatus.REJECTED; }

    private void require(LoanStatus expected) {
        if (status != expected) throw new IllegalStateException("Loan " + loanId + " is " + status + ", expected " + expected);
    }

    public String getLoanId() { return loanId; }
    public String getCustomerId() { return customerId; }
    public String getAccountNo() { return accountNo; }
    public double getPrincipal() { return principal; }
    public double getRate() { return rate; }
    public int getTenureMonths() { return tenureMonths; }
    public String getPurpose() { return purpose; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public LoanStatus getStatus() { return status; }
    public double getOutstanding() { return outstanding; }
}
