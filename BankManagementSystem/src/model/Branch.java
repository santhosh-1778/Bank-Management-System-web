package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds the accounts and employees of one branch (aggregation: they are separate objects). */
public class Branch implements Serializable {
    private final String branchId;
    private final String location;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Employee> employees = new ArrayList<>();

    public Branch(String branchId, String location) {
        this.branchId = branchId;
        this.location = location;
    }

    public void addAccount(Account a) {
        if (accounts.putIfAbsent(a.getAccountNo(), a) != null) {
            throw new IllegalArgumentException("Duplicate account number " + a.getAccountNo());
        }
    }

    /** @throws AccountNotFoundException if the number does not exist */
    public Account findAccount(String accountNo) {
        Account a = accounts.get(accountNo);
        if (a == null) throw new AccountNotFoundException(accountNo);
        return a;
    }

    public void addEmployee(Employee e) { employees.add(e); }

    public Collection<Account> getAccounts() { return Collections.unmodifiableCollection(accounts.values()); }
    public List<Employee> getEmployees() { return Collections.unmodifiableList(employees); }
    public String getBranchId() { return branchId; }
    public String getLocation() { return location; }
}
