package model;

/** Opens and closes accounts, handles counter transactions and customer profiles. */
public class Teller extends Employee {
    public Teller(String empId, String name) { super(empId, name); }

    @Override
    public String getTitle() { return "Teller"; }

    public String openAccount() { return processRequest("open account"); }

    public String closeAccount() { return processRequest("close account"); }
}
