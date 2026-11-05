package service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.*;

/**
 * The single entry point for the UI. Every public operation checks the session (timeout), the role
 * (RBAC), validates input, performs the change and writes an audit entry.
 * All mutators are synchronized, so two simultaneous operations can never corrupt a balance.
 */
public class BankService implements Serializable {
    public static final double DEFAULT_LOAN_RATE = 10.0; // percent per year
    public static final int MINI_STATEMENT_SIZE = 10;

    private final Branch branch;
    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Loan> loans = new LinkedHashMap<>();
    private final AuthService auth = new AuthService();
    private final AuditLog audit = new AuditLog();
    private int accountSeq = 100000, customerSeq, loanSeq, employeeSeq;

    public BankService(String branchId, String location) {
        this.branch = new Branch(branchId, location);
    }

    // ===== authentication and users (FR17-FR19) ================================================

    public Session login(String userId, String password) {
        return login(userId, password, java.time.Clock.systemDefaultZone(), AuthService.DEFAULT_TIMEOUT);
    }

    /** Login with an explicit clock and idle timeout (used by tests to check session expiry). */
    public Session login(String userId, String password, java.time.Clock clock, java.time.Duration timeout) {
        try {
            Session s = auth.login(userId, password, clock, timeout);
            audit.log(s.getUser().getUserId(), "LOGIN");
            return s;
        } catch (InvalidLoginException e) {
            audit.log(String.valueOf(userId), "LOGIN FAILED: " + e.getMessage());
            throw e;
        }
    }

    public void logout(Session s) {
        audit.log(s.getUser().getUserId(), "LOGOUT");
        s.logout();
    }

    public synchronized boolean hasUsers() { return !auth.isEmpty(); }

    /** Creates the very first manager. Only allowed while no user exists. */
    public synchronized void bootstrapAdmin(String userId, String password) {
        if (!auth.isEmpty()) throw new IllegalStateException("Users already exist");
        branch.addEmployee(new Manager(nextEmployeeId(), "System Admin"));
        auth.create(userId, password, Role.MANAGER, null);
        audit.log("system", "BOOTSTRAP ADMIN " + userId);
    }

    /** Manager creates a login for an existing customer. */
    public synchronized void createCustomerLogin(Session s, String userId, String password, String customerId) {
        User actor = requireManager(s);
        findCustomer(customerId);
        auth.create(userId, password, Role.CUSTOMER, customerId);
        audit.log(actor.getUserId(), "CREATE CUSTOMER LOGIN " + userId + " for " + customerId);
    }

    /** Manager creates an employee (teller or manager) together with the login. */
    public synchronized String createEmployee(Session s, String name, String userId, String password, Role role) {
        User actor = requireManager(s);
        if (role == Role.CUSTOMER) throw new IllegalArgumentException("Use createCustomerLogin for customers");
        String empId = nextEmployeeId();
        Employee e = role == Role.MANAGER ? new Manager(empId, name) : new Teller(empId, name);
        auth.create(userId, password, role, null); // fails first if the user ID is taken
        branch.addEmployee(e);
        audit.log(actor.getUserId(), "CREATE EMPLOYEE " + empId + " (" + role + ") login " + userId);
        return empId;
    }

    public synchronized void setUserEnabled(Session s, String userId, boolean enabled) {
        User actor = requireManager(s);
        auth.find(userId).setEnabled(enabled);
        audit.log(actor.getUserId(), (enabled ? "ENABLE USER " : "DISABLE USER ") + userId);
    }

    public synchronized void unlockUser(Session s, String userId) {
        User actor = requireManager(s);
        auth.find(userId).unlock();
        audit.log(actor.getUserId(), "UNLOCK USER " + userId);
    }

    public synchronized List<User> listUsers(Session s) {
        requireManager(s);
        return auth.list();
    }

    public synchronized List<AuditLog.Entry> getAuditLog(Session s) {
        requireManager(s);
        return audit.getEntries();
    }

    // ===== customers and accounts (FR1-FR4) ====================================================

    public synchronized Customer registerCustomer(Session s, String name, String address, String phone, String idProof) {
        User actor = requireStaff(s);
        Customer c = Customer.register(String.format("CUS%04d", ++customerSeq), name, address, phone, idProof);
        customers.put(c.getCustomerId(), c);
        audit.log(actor.getUserId(), "REGISTER CUSTOMER " + c.getCustomerId());
        return c;
    }

    public synchronized void updateProfile(Session s, String customerId, String name, String address, String phone) {
        User actor = requireStaff(s);
        findCustomer(customerId).updateProfile(name, address, phone);
        audit.log(actor.getUserId(), "UPDATE PROFILE " + customerId);
    }

    public synchronized Customer getCustomer(Session s, String customerId) {
        User u = actor(s);
        if (u.getRole() == Role.CUSTOMER && !customerId.equals(u.getCustomerId())) throw deny("You can only view your own profile");
        return findCustomer(customerId);
    }

    /** FR1/FR2: opens a savings or current account with a unique number and records the opening deposit. */
    public synchronized Account openAccount(Session s, String customerId, String type, double openingDeposit) {
        User actor = requireStaff(s);
        findCustomer(customerId);
        if (openingDeposit < 0) throw new IllegalArgumentException("Opening deposit cannot be negative");
        if ("SAVINGS".equalsIgnoreCase(type == null ? "" : type.trim()) && openingDeposit < SavingsAccount.MIN_BALANCE) {
            throw new IllegalArgumentException("A savings account needs an opening deposit of at least " + SavingsAccount.MIN_BALANCE);
        }
        Account a = AccountFactory.create(type, "ACC" + (++accountSeq), customerId);
        if (openingDeposit > 0) a.deposit(openingDeposit);
        branch.addAccount(a);
        audit.log(actor.getUserId(), "OPEN ACCOUNT " + a.getAccountNo() + " (" + a.getType() + ") for " + customerId);
        return a;
    }

    /** FR4: only if the balance is zero and there is no active loan. */
    public synchronized void closeAccount(Session s, String accountNo) {
        User actor = requireStaff(s);
        Account a = branch.findAccount(accountNo);
        for (Loan l : loans.values()) {
            if (l.getAccountNo().equals(accountNo) && l.isActive()) {
                throw new IllegalStateException("Cannot close account " + accountNo + ": loan " + l.getLoanId() + " is still active");
            }
        }
        a.close();
        audit.log(actor.getUserId(), "CLOSE ACCOUNT " + accountNo);
    }

    public synchronized void freezeAccount(Session s, String accountNo) {
        User actor = requireManager(s);
        branch.findAccount(accountNo).freeze();
        audit.log(actor.getUserId(), "FREEZE ACCOUNT " + accountNo);
    }

    public synchronized void unfreezeAccount(Session s, String accountNo) {
        User actor = requireManager(s);
        branch.findAccount(accountNo).unfreeze();
        audit.log(actor.getUserId(), "UNFREEZE ACCOUNT " + accountNo);
    }

    /** Customers see their own accounts, staff see every account. */
    public synchronized List<Account> listAccounts(Session s) {
        User u = actor(s);
        List<Account> result = new ArrayList<>();
        for (Account a : branch.getAccounts()) {
            if (u.getRole().isStaff() || a.getCustomerId().equals(u.getCustomerId())) result.add(a);
        }
        return result;
    }

    // ===== transactions (FR5-FR9) ==============================================================

    public synchronized void deposit(Session s, String accountNo, double amount) {
        User actor = actor(s);
        accessibleAccount(actor, accountNo).deposit(amount);
        audit.log(actor.getUserId(), "DEPOSIT " + amount + " to " + accountNo);
    }

    public synchronized void withdraw(Session s, String accountNo, double amount) throws InsufficientFundsException {
        User actor = actor(s);
        accessibleAccount(actor, accountNo).withdraw(amount);
        audit.log(actor.getUserId(), "WITHDRAW " + amount + " from " + accountNo);
    }

    /**
     * FR7: atomic transfer. Everything that can fail is checked or done first; if the credit still fails,
     * the debit is undone, so neither balance changes and no stray entry is left in either history.
     */
    public synchronized void transfer(Session s, String fromNo, String toNo, double amount) throws InsufficientFundsException {
        User actor = actor(s);
        if (actor.getRole() != Role.CUSTOMER) throw deny("Only customers can transfer funds");
        Account from = accessibleAccount(actor, fromNo);
        Account to = branch.findAccount(toNo);
        if (fromNo.equals(toNo)) throw new IllegalArgumentException("Source and destination must be different");
        to.ensureUsable();                       // destination problems are found before anything changes
        from.transferOut(amount);                // fails first, so nothing has changed yet
        try {
            to.transferIn(amount);
        } catch (RuntimeException e) {
            from.undoLast();                     // roll back the debit completely
            throw e;
        }
        audit.log(actor.getUserId(), "TRANSFER " + amount + " from " + fromNo + " to " + toNo);
    }

    // ===== information and reports (FR10, FR11) ===============================================

    public synchronized double getBalance(Session s, String accountNo) {
        return accessibleAccount(actor(s), accountNo).getBalance();
    }

    public synchronized List<Transaction> getMiniStatement(Session s, String accountNo) {
        return accessibleAccount(actor(s), accountNo).getMiniStatement(MINI_STATEMENT_SIZE);
    }

    public synchronized List<Transaction> getStatement(Session s, String accountNo, LocalDate from, LocalDate to) {
        return accessibleAccount(actor(s), accountNo).getStatement(from, to);
    }

    // ===== loans (FR12-FR15) ===================================================================

    public synchronized Loan applyForLoan(Session s, String accountNo, double amount, int tenureMonths, String purpose) {
        User actor = actor(s);
        if (actor.getRole() != Role.CUSTOMER) throw deny("Only customers can apply for a loan");
        Account a = accessibleAccount(actor, accountNo);
        a.ensureUsable();
        Loan loan = new Loan(String.format("LN%04d", ++loanSeq), a.getCustomerId(), accountNo, amount, DEFAULT_LOAN_RATE, tenureMonths, purpose);
        loans.put(loan.getLoanId(), loan);
        audit.log(actor.getUserId(), "APPLY LOAN " + loan.getLoanId() + " amount " + amount);
        return loan;
    }

    /** Manager approves the loan and the amount is credited to the customer's account (disbursed). */
    public synchronized void approveLoan(Session s, String loanId) {
        User actor = requireManager(s);
        Loan loan = findLoan(loanId);
        Account a = branch.findAccount(loan.getAccountNo());
        a.ensureUsable();
        loan.approve();
        a.receiveLoan(loan.getPrincipal());
        loan.markDisbursed();
        audit.log(actor.getUserId(), "APPROVE LOAN " + loanId);
    }

    public synchronized void rejectLoan(Session s, String loanId) {
        User actor = requireManager(s);
        findLoan(loanId).reject();
        audit.log(actor.getUserId(), "REJECT LOAN " + loanId);
    }

    /** Customer pays towards a disbursed loan from the loan's account. */
    public synchronized void repayLoan(Session s, String loanId, double amount) throws InsufficientFundsException {
        User actor = actor(s);
        Loan loan = findLoan(loanId);
        Account a = accessibleAccount(actor, loan.getAccountNo());
        if (actor.getRole() != Role.CUSTOMER) throw deny("Only customers can repay a loan");
        if (loan.getStatus() != LoanStatus.DISBURSED) throw new IllegalStateException("Loan " + loanId + " is " + loan.getStatus());
        if (amount > loan.getOutstanding() + 0.005) throw new IllegalArgumentException("Amount exceeds outstanding balance " + loan.getOutstanding());
        a.payLoan(amount);
        loan.applyRepayment(amount);
        audit.log(actor.getUserId(), "REPAY LOAN " + loanId + " amount " + amount);
    }

    /** Customers see their own loans, managers see all loans. */
    public synchronized List<Loan> listLoans(Session s) {
        User u = actor(s);
        if (u.getRole() == Role.TELLER) throw deny("Tellers cannot view loans");
        List<Loan> result = new ArrayList<>();
        for (Loan l : loans.values()) {
            if (u.getRole() == Role.MANAGER || l.getCustomerId().equals(u.getCustomerId())) result.add(l);
        }
        return result;
    }

    // ===== interest and housekeeping (FR16) ====================================================

    /** Credits one month of interest to every ACTIVE savings account. Returns how many were credited. */
    public synchronized int creditMonthlyInterest(Session s) {
        User actor = requireManager(s);
        int count = 0;
        for (Account a : branch.getAccounts()) {
            if (a instanceof SavingsAccount && a.getStatus() == AccountStatus.ACTIVE && ((SavingsAccount) a).addInterest() > 0) count++;
        }
        audit.log(actor.getUserId(), "CREDIT MONTHLY INTEREST to " + count + " accounts");
        return count;
    }

    /** Marks accounts with no activity for 12 months as DORMANT. */
    public synchronized int runDormancyCheck(Session s, LocalDateTime now) {
        User actor = requireManager(s);
        int count = 0;
        for (Account a : branch.getAccounts()) if (a.markDormantIfInactive(now)) count++;
        audit.log(actor.getUserId(), "DORMANCY CHECK: " + count + " accounts marked dormant");
        return count;
    }

    // ===== persistence (data is not lost on failure) ===========================================

    /** Writes to a temporary file first, then moves it into place, so a crash never leaves a half-written file. */
    public synchronized void save(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "bank", ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp); ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(this);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static BankService load(Path file) throws IOException, ClassNotFoundException {
        try (InputStream is = Files.newInputStream(file); ObjectInputStream ois = new ObjectInputStream(is)) {
            // only our own classes and a few JDK ones may be deserialized
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "model.*;service.*;java.util.*;java.time.*;java.lang.*;!*"));
            BankService bank = (BankService) ois.readObject();
            int max = 0;
            for (Account a : bank.branch.getAccounts()) for (Transaction t : a.getHistory()) max = Math.max(max, t.getTxnId());
            Transaction.ensureCounterAtLeast(max);
            return bank;
        }
    }

    // ===== helpers =============================================================================

    private User actor(Session s) {
        s.validate();
        return s.getUser();
    }

    private User requireStaff(Session s) {
        User u = actor(s);
        if (!u.getRole().isStaff()) throw deny("Only bank staff can do this");
        return u;
    }

    private User requireManager(Session s) {
        User u = actor(s);
        if (u.getRole() != Role.MANAGER) throw deny("Only a manager can do this");
        return u;
    }

    /** Customers may only touch their own accounts. Staff may touch any account. */
    private Account accessibleAccount(User u, String accountNo) {
        Account a = branch.findAccount(accountNo);
        if (u.getRole() == Role.CUSTOMER && !a.getCustomerId().equals(u.getCustomerId())) {
            throw deny("Account " + accountNo + " does not belong to you");
        }
        return a;
    }

    private Customer findCustomer(String customerId) {
        Customer c = customers.get(customerId);
        if (c == null) throw new IllegalArgumentException("No such customer: " + customerId);
        return c;
    }

    private Loan findLoan(String loanId) {
        Loan l = loans.get(loanId);
        if (l == null) throw new IllegalArgumentException("No such loan: " + loanId);
        return l;
    }

    private String nextEmployeeId() { return String.format("EMP%04d", ++employeeSeq); }

    private static AccessDeniedException deny(String message) { return new AccessDeniedException(message); }
}
