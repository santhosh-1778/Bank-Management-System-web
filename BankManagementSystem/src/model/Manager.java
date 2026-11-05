package model;

/** Approves loans and manages users. Inherits everything a Teller can do (see Role). */
public class Manager extends Employee {
    public Manager(String empId, String name) { super(empId, name); }

    @Override
    public String getTitle() { return "Manager"; }

    public String approveLoan(Loan loan) { return processRequest("review loan " + loan.getLoanId()); }

    public String manageUsers() { return processRequest("manage users"); }
}
