import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import model.*;
import service.*;
import ui.ConsoleApp;

/** Dependency-free test runner (no JUnit needed). Exit code 1 if any test fails. */
public class TestRunner {
    interface Body { void run() throws Exception; }

    static int passed, failed;

    static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + "  -> " + t);
        }
    }

    static void eq(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.001) throw new AssertionError("expected " + expected + " but was " + actual);
    }

    static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " but was " + actual);
    }

    static void yes(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    static void throwsA(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    /** A bank with admin, teller, customers alice (S + C accounts) and bob (savings). */
    static class Fixture {
        BankService bank = new BankService("BR001", "Test Branch");
        Session admin, teller, alice, bob;
        Customer aliceC, bobC;
        Account aliceSavings, aliceCurrent, bobSavings;

        Fixture() throws Exception {
            bank.bootstrapAdmin("admin", "admin123");
            admin = bank.login("admin", "admin123");
            bank.createEmployee(admin, "Tara Teller", "teller", "teller123", Role.TELLER);
            teller = bank.login("teller", "teller123");
            aliceC = bank.registerCustomer(teller, "Alice Rao", "Hyderabad", "9876543210", "ID-1");
            bobC = bank.registerCustomer(teller, "Bob Kumar", "Vizag", "9123456780", "ID-2");
            aliceSavings = bank.openAccount(teller, aliceC.getCustomerId(), "SAVINGS", 5000);
            aliceCurrent = bank.openAccount(teller, aliceC.getCustomerId(), "CURRENT", 2000);
            bobSavings = bank.openAccount(teller, bobC.getCustomerId(), "SAVINGS", 5000);
            bank.createCustomerLogin(admin, "alice", "alice123", aliceC.getCustomerId());
            bank.createCustomerLogin(admin, "bob", "bob123", bobC.getCustomerId());
            alice = bank.login("alice", "alice123");
            bob = bank.login("bob", "bob123");
        }
    }

    public static void main(String[] args) {
        // ---------- unit tests on the model (Step 8 test cases TC1-TC4) ----------
        test("TC1 deposit a positive amount (FR5, FR8)", () -> {
            SavingsAccount s = new SavingsAccount("S1", 5000, 4.0);
            s.deposit(500);
            eq(5500, s.getBalance());
            eq(TransactionType.DEPOSIT, s.getHistory().get(0).getType());
            eq(5500, s.getHistory().get(0).getBalanceAfter());
        });
        test("TC2 withdraw within balance (FR6)", () -> {
            SavingsAccount s = new SavingsAccount("S1", 5000, 4.0);
            s.withdraw(1000);
            eq(4000, s.getBalance());
        });
        test("TC3 savings minimum balance is enforced (FR9)", () -> {
            SavingsAccount s = new SavingsAccount("S1", 5000, 4.0);
            throwsA(InsufficientFundsException.class, () -> s.withdraw(4500));
            eq(5000, s.getBalance());
            yes(s.getHistory().isEmpty(), "failed withdrawal must not be recorded");
        });
        test("TC4 current account overdraft (FR6)", () -> {
            CurrentAccount c = new CurrentAccount("C1", 2000, 1000);
            c.withdraw(2500);
            eq(-500, c.getBalance());
            throwsA(InsufficientFundsException.class, () -> c.withdraw(600)); // would pass the -1000 limit
        });
        test("zero and negative amounts are rejected", () -> {
            SavingsAccount s = new SavingsAccount("S1", 5000, 4.0);
            throwsA(IllegalArgumentException.class, () -> s.deposit(0));
            throwsA(IllegalArgumentException.class, () -> s.deposit(-5));
            throwsA(IllegalArgumentException.class, () -> s.withdraw(-5));
            throwsA(IllegalArgumentException.class, () -> s.deposit(Double.NaN));
            eq(5000, s.getBalance());
        });
        test("exception message matches the guide (Insufficient funds in S1 for amount 3000.0)", () -> {
            SavingsAccount s1 = new SavingsAccount("S1", 3500, 4.0);
            try {
                s1.withdraw(3000);
                throw new AssertionError("expected an exception");
            } catch (InsufficientFundsException e) {
                eq("Insufficient funds in S1 for amount 3000.0", e.getMessage());
            }
        });

        // ---------- service tests (TC5-TC9 and more) ----------
        test("TC5 successful transfer records two transactions (FR7)", () -> {
            Fixture f = new Fixture();
            f.bank.transfer(f.alice, f.aliceSavings.getAccountNo(), f.aliceCurrent.getAccountNo(), 1500);
            eq(3500, f.aliceSavings.getBalance());
            eq(3500, f.aliceCurrent.getBalance());
            eq(TransactionType.TRANSFER_OUT, last(f.aliceSavings).getType());
            eq(TransactionType.TRANSFER_IN, last(f.aliceCurrent).getType());
        });
        test("TC6 transfer with insufficient funds changes nothing (FR7)", () -> {
            Fixture f = new Fixture();
            int s = f.aliceSavings.getHistory().size(), c = f.aliceCurrent.getHistory().size();
            throwsA(InsufficientFundsException.class,
                    () -> f.bank.transfer(f.alice, f.aliceSavings.getAccountNo(), f.aliceCurrent.getAccountNo(), 4500));
            eq(5000, f.aliceSavings.getBalance());
            eq(2000, f.aliceCurrent.getBalance());
            eq(s, f.aliceSavings.getHistory().size());
            eq(c, f.aliceCurrent.getHistory().size());
        });
        test("TC7 transfer to unknown account (FR7)", () -> {
            Fixture f = new Fixture();
            throwsA(AccountNotFoundException.class, () -> f.bank.transfer(f.alice, f.aliceSavings.getAccountNo(), "NOPE", 100));
            eq(5000, f.aliceSavings.getBalance());
        });
        test("TC8 close account with a balance is rejected, zero balance closes (FR4)", () -> {
            Fixture f = new Fixture();
            throwsA(IllegalStateException.class, () -> f.bank.closeAccount(f.teller, f.aliceCurrent.getAccountNo()));
            eq(AccountStatus.ACTIVE, f.aliceCurrent.getStatus());
            f.bank.withdraw(f.teller, f.aliceCurrent.getAccountNo(), 2000);
            f.bank.closeAccount(f.teller, f.aliceCurrent.getAccountNo());
            eq(AccountStatus.CLOSED, f.aliceCurrent.getStatus());
            throwsA(IllegalStateException.class, () -> f.bank.deposit(f.teller, f.aliceCurrent.getAccountNo(), 10));
        });
        test("TC9 three wrong passwords lock the user, fourth attempt refused (FR17)", () -> {
            Fixture f = new Fixture();
            for (int i = 0; i < 3; i++) throwsA(InvalidLoginException.class, () -> f.bank.login("alice", "wrong"));
            throwsA(InvalidLoginException.class, () -> f.bank.login("alice", "alice123")); // correct password, still refused
            f.bank.unlockUser(f.admin, "alice");
            f.bank.login("alice", "alice123");
        });
        test("atomic transfer: failing credit rolls back the debit completely", () -> {
            Fixture f = new Fixture();
            f.bank.freezeAccount(f.admin, f.bobSavings.getAccountNo());
            int before = f.aliceSavings.getHistory().size();
            throwsA(IllegalStateException.class,
                    () -> f.bank.transfer(f.alice, f.aliceSavings.getAccountNo(), f.bobSavings.getAccountNo(), 1000));
            eq(5000, f.aliceSavings.getBalance());
            eq(5000, f.bobSavings.getBalance());
            eq(before, f.aliceSavings.getHistory().size());
            f.bank.unfreezeAccount(f.admin, f.bobSavings.getAccountNo());
            f.bank.transfer(f.alice, f.aliceSavings.getAccountNo(), f.bobSavings.getAccountNo(), 1000);
            eq(6000, f.bobSavings.getBalance());
        });
        test("Account.undoLast restores balance for credits and debits", () -> {
            SavingsAccount s = new SavingsAccount("S1", 5000, 4.0);
            s.deposit(100);
            s.undoLast();
            eq(5000, s.getBalance());
            s.withdraw(100);
            s.undoLast();
            eq(5000, s.getBalance());
        });
        test("concurrent transfers keep the total balance (concurrency NFR)", () -> {
            Fixture f = new Fixture();
            String a = f.aliceSavings.getAccountNo(), b = f.aliceCurrent.getAccountNo();
            f.bank.deposit(f.teller, a, 100000);
            f.bank.deposit(f.teller, b, 100000);
            double total = f.aliceSavings.getBalance() + f.aliceCurrent.getBalance();
            Thread t1 = new Thread(() -> repeat(f, a, b));
            Thread t2 = new Thread(() -> repeat(f, b, a));
            t1.start(); t2.start(); t1.join(); t2.join();
            eq(total, f.aliceSavings.getBalance() + f.aliceCurrent.getBalance());
        });

        // ---------- access control ----------
        test("customer cannot see or use someone else's account (FR18)", () -> {
            Fixture f = new Fixture();
            throwsA(AccessDeniedException.class, () -> f.bank.getBalance(f.alice, f.bobSavings.getAccountNo()));
            throwsA(AccessDeniedException.class, () -> f.bank.withdraw(f.alice, f.bobSavings.getAccountNo(), 10));
            throwsA(AccessDeniedException.class,
                    () -> f.bank.transfer(f.alice, f.bobSavings.getAccountNo(), f.aliceSavings.getAccountNo(), 10));
            eq(1, countOwn(f.bank.listAccounts(f.bob), f.bobC.getCustomerId()) > 0 ? 1 : 0);
            eq(2, f.bank.listAccounts(f.alice).size());
            eq(3, f.bank.listAccounts(f.teller).size());
        });
        test("role checks: teller and customer cannot do manager work (FR18, FR19)", () -> {
            Fixture f = new Fixture();
            throwsA(AccessDeniedException.class, () -> f.bank.createEmployee(f.teller, "X", "x1", "secret1", Role.TELLER));
            throwsA(AccessDeniedException.class, () -> f.bank.listUsers(f.alice));
            throwsA(AccessDeniedException.class, () -> f.bank.openAccount(f.alice, f.aliceC.getCustomerId(), "SAVINGS", 2000));
            throwsA(AccessDeniedException.class, () -> f.bank.getAuditLog(f.teller));
            throwsA(AccessDeniedException.class, () -> f.bank.creditMonthlyInterest(f.teller));
            throwsA(AccessDeniedException.class, () -> f.bank.listLoans(f.teller));
        });
        test("manager can disable a user, disabled user cannot log in (FR19)", () -> {
            Fixture f = new Fixture();
            f.bank.setUserEnabled(f.admin, "bob", false);
            throwsA(InvalidLoginException.class, () -> f.bank.login("bob", "bob123"));
            f.bank.setUserEnabled(f.admin, "bob", true);
            f.bank.login("bob", "bob123");
        });
        test("duplicate user ID is rejected and short passwords refused", () -> {
            Fixture f = new Fixture();
            throwsA(IllegalArgumentException.class, () -> f.bank.createEmployee(f.admin, "Dup", "teller", "secret1", Role.TELLER));
            throwsA(IllegalArgumentException.class, () -> f.bank.createEmployee(f.admin, "Short", "short1", "123", Role.TELLER));
        });
        test("passwords are stored hashed, never in plain text", () -> {
            Fixture f = new Fixture();
            for (User u : f.bank.listUsers(f.admin)) {
                yes(!u.getPasswordHash().contains("admin123") && !u.getPasswordHash().contains("alice123"), "plain text found");
                yes(u.getPasswordHash().length() >= 40, "hash too short");
            }
        });
        test("session times out after inactivity (security NFR)", () -> {
            Fixture f = new Fixture();
            MutableClock clock = new MutableClock();
            Session s = f.bank.login("alice", "alice123", clock, Duration.ofMinutes(5));
            f.bank.getBalance(s, f.aliceSavings.getAccountNo());
            clock.advance(Duration.ofMinutes(4));
            f.bank.getBalance(s, f.aliceSavings.getAccountNo());   // activity refreshes the timer
            clock.advance(Duration.ofMinutes(4));
            f.bank.getBalance(s, f.aliceSavings.getAccountNo());
            clock.advance(Duration.ofMinutes(6));
            throwsA(SessionExpiredException.class, () -> f.bank.getBalance(s, f.aliceSavings.getAccountNo()));
            throwsA(SessionExpiredException.class, () -> f.bank.getBalance(s, f.aliceSavings.getAccountNo())); // stays expired
        });
        test("logged-out session cannot be reused", () -> {
            Fixture f = new Fixture();
            f.bank.logout(f.alice);
            throwsA(SessionExpiredException.class, () -> f.bank.getBalance(f.alice, f.aliceSavings.getAccountNo()));
        });

        // ---------- accounts, customers, statements ----------
        test("account numbers are unique (FR2)", () -> {
            Fixture f = new Fixture();
            yes(!f.aliceSavings.getAccountNo().equals(f.aliceCurrent.getAccountNo()), "duplicate");
            yes(!f.aliceSavings.getAccountNo().equals(f.bobSavings.getAccountNo()), "duplicate");
        });
        test("opening rules: savings needs the minimum deposit, bad type rejected (UC-1 alternate flow)", () -> {
            Fixture f = new Fixture();
            throwsA(IllegalArgumentException.class, () -> f.bank.openAccount(f.teller, f.aliceC.getCustomerId(), "SAVINGS", 500));
            throwsA(IllegalArgumentException.class, () -> f.bank.openAccount(f.teller, f.aliceC.getCustomerId(), "GOLD", 5000));
            throwsA(IllegalArgumentException.class, () -> f.bank.openAccount(f.teller, "CUS9999", "CURRENT", 0));
            eq(3, f.bank.listAccounts(f.teller).size());
        });
        test("customer profile can be updated, invalid data is rejected (FR3)", () -> {
            Fixture f = new Fixture();
            f.bank.updateProfile(f.teller, f.aliceC.getCustomerId(), "Alice R. Rao", "", "9000000001");
            eq("Alice R. Rao", f.aliceC.getName());
            eq("Hyderabad", f.aliceC.getAddress());
            throwsA(IllegalArgumentException.class, () -> f.bank.updateProfile(f.teller, f.aliceC.getCustomerId(), "", "", "abc"));
            eq("9000000001", f.aliceC.getPhone());
            throwsA(IllegalArgumentException.class, () -> f.bank.registerCustomer(f.teller, "", "x", "9876543210", "id"));
        });
        test("mini statement returns the last 10 transactions, oldest first (FR10)", () -> {
            Fixture f = new Fixture();
            for (int i = 1; i <= 12; i++) f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), i);
            List<Transaction> mini = f.bank.getMiniStatement(f.alice, f.aliceSavings.getAccountNo());
            eq(10, mini.size());
            eq(12, mini.get(9).getAmount());
            eq(3, mini.get(0).getAmount());
            eq(5000 + 78, f.bank.getBalance(f.alice, f.aliceSavings.getAccountNo()));
        });
        test("statement by date range (FR11)", () -> {
            Fixture f = new Fixture();
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 50);
            LocalDate today = LocalDate.now();
            eq(2, f.bank.getStatement(f.alice, f.aliceSavings.getAccountNo(), today, today).size()); // opening + deposit
            eq(0, f.bank.getStatement(f.alice, f.aliceSavings.getAccountNo(), today.minusDays(10), today.minusDays(5)).size());
            throwsA(IllegalArgumentException.class, () -> f.bank.getStatement(f.alice, f.aliceSavings.getAccountNo(), today, today.minusDays(1)));
        });
        test("every transaction has a unique id, type, amount and resulting balance (FR8)", () -> {
            Fixture f = new Fixture();
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 100);
            f.bank.withdraw(f.alice, f.aliceSavings.getAccountNo(), 200);
            List<Transaction> h = f.aliceSavings.getHistory();
            eq(3, h.size());
            eq(TransactionType.WITHDRAW, h.get(2).getType());
            eq(4900, h.get(2).getBalanceAfter());
            yes(h.get(0).getTxnId() < h.get(1).getTxnId() && h.get(1).getTxnId() < h.get(2).getTxnId(), "ids not increasing");
        });

        // ---------- loans ----------
        test("EMI calculation (FR13)", () -> {
            eq(8884.88, Loan.calculateEMI(100000, 12, 12));
            eq(1000, Loan.calculateEMI(12000, 0, 12));
        });
        test("loan: apply, approve, disburse, repay in full (FR12-FR15)", () -> {
            Fixture f = new Fixture();
            Loan l = f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 12000, 12, "Laptop");
            eq(LoanStatus.APPLIED, l.getStatus());
            double emi = l.calculateEMI();
            f.bank.approveLoan(f.admin, l.getLoanId());
            eq(LoanStatus.DISBURSED, l.getStatus());
            eq(17000, f.aliceSavings.getBalance());
            eq(TransactionType.LOAN_DISBURSEMENT, last(f.aliceSavings).getType());
            double total = l.getOutstanding();
            eq(emi * 12, total);
            f.bank.repayLoan(f.alice, l.getLoanId(), emi);
            eq(total - emi, l.getOutstanding());
            throwsA(IllegalStateException.class, () -> f.bank.closeAccount(f.teller, f.aliceSavings.getAccountNo()));
            f.bank.repayLoan(f.alice, l.getLoanId(), l.getOutstanding());
            eq(LoanStatus.CLOSED, l.getStatus());
            eq(0, l.getOutstanding());
        });
        test("loan: reject keeps balance unchanged, cannot be approved afterwards", () -> {
            Fixture f = new Fixture();
            Loan l = f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 5000, 6, "Fees");
            f.bank.rejectLoan(f.admin, l.getLoanId());
            eq(LoanStatus.REJECTED, l.getStatus());
            eq(5000, f.aliceSavings.getBalance());
            throwsA(IllegalStateException.class, () -> f.bank.approveLoan(f.admin, l.getLoanId()));
            f.bank.withdraw(f.teller, f.aliceSavings.getAccountNo(), 4000); // balance = min balance... 1000 left
            eq(1000, f.aliceSavings.getBalance());
        });
        test("loan rules: only managers decide, only customers apply/repay, invalid input refused", () -> {
            Fixture f = new Fixture();
            throwsA(AccessDeniedException.class, () -> f.bank.applyForLoan(f.teller, f.aliceSavings.getAccountNo(), 1000, 6, "x"));
            throwsA(AccessDeniedException.class, () -> f.bank.applyForLoan(f.alice, f.bobSavings.getAccountNo(), 1000, 6, "x"));
            throwsA(IllegalArgumentException.class, () -> f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), -1, 6, "x"));
            throwsA(IllegalArgumentException.class, () -> f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 1000, 0, "x"));
            throwsA(IllegalArgumentException.class, () -> f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 1000, 6, " "));
            Loan l = f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 1000, 6, "x");
            throwsA(AccessDeniedException.class, () -> f.bank.approveLoan(f.teller, l.getLoanId()));
            throwsA(AccessDeniedException.class, () -> f.bank.approveLoan(f.alice, l.getLoanId()));
            throwsA(IllegalStateException.class, () -> f.bank.repayLoan(f.alice, l.getLoanId(), 100)); // not disbursed yet
            eq(1, f.bank.listLoans(f.alice).size());
            eq(0, f.bank.listLoans(f.bob).size());
            eq(1, f.bank.listLoans(f.admin).size());
        });
        test("loan repayment cannot exceed the outstanding amount or the account balance", () -> {
            Fixture f = new Fixture();
            Loan l = f.bank.applyForLoan(f.alice, f.aliceSavings.getAccountNo(), 12000, 12, "x");
            f.bank.approveLoan(f.admin, l.getLoanId());
            throwsA(IllegalArgumentException.class, () -> f.bank.repayLoan(f.alice, l.getLoanId(), l.getOutstanding() + 1));
            f.bank.withdraw(f.teller, f.aliceSavings.getAccountNo(), 16000); // leave the 1000 minimum
            throwsA(InsufficientFundsException.class, () -> f.bank.repayLoan(f.alice, l.getLoanId(), 500));
            eq(l.calculateEMI() * 12, l.getOutstanding());
        });

        // ---------- interest, dormancy, audit ----------
        test("monthly interest is credited to savings accounts only (FR16)", () -> {
            Fixture f = new Fixture();
            int n = f.bank.creditMonthlyInterest(f.admin);
            eq(2, n); // alice savings, bob savings
            eq(5000 + 16.67, f.aliceSavings.getBalance());
            eq(2000, f.aliceCurrent.getBalance());
            eq(TransactionType.INTEREST, last(f.aliceSavings).getType());
        });
        test("accounts idle for 12 months become dormant, a transaction wakes them", () -> {
            Fixture f = new Fixture();
            int n = f.bank.runDormancyCheck(f.admin, LocalDateTime.now().plusMonths(13));
            eq(3, n);
            eq(AccountStatus.DORMANT, f.aliceSavings.getStatus());
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 10);
            eq(AccountStatus.ACTIVE, f.aliceSavings.getStatus());
            eq(0, f.bank.runDormancyCheck(f.admin, LocalDateTime.now().plusMonths(1)));
        });
        test("frozen accounts refuse transactions until a manager unfreezes them", () -> {
            Fixture f = new Fixture();
            f.bank.freezeAccount(f.admin, f.aliceSavings.getAccountNo());
            throwsA(IllegalStateException.class, () -> f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 10));
            throwsA(AccessDeniedException.class, () -> f.bank.unfreezeAccount(f.teller, f.aliceSavings.getAccountNo()));
            f.bank.unfreezeAccount(f.admin, f.aliceSavings.getAccountNo());
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 10);
        });
        test("critical actions are audited with user and timestamp", () -> {
            Fixture f = new Fixture();
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 10);
            try { f.bank.login("alice", "bad"); } catch (InvalidLoginException ignored) { }
            List<AuditLog.Entry> log = f.bank.getAuditLog(f.admin);
            boolean deposit = false, failed = false;
            for (AuditLog.Entry e : log) {
                yes(e.getTime() != null && e.getUserId() != null, "missing user/time");
                if (e.getUserId().equals("alice") && e.getAction().startsWith("DEPOSIT")) deposit = true;
                if (e.getUserId().equals("alice") && e.getAction().startsWith("LOGIN FAILED")) failed = true;
            }
            yes(deposit && failed, "expected deposit and failed-login entries");
        });

        // ---------- persistence and UI ----------
        test("data survives save and load (including users and txn ids)", () -> {
            Fixture f = new Fixture();
            f.bank.deposit(f.alice, f.aliceSavings.getAccountNo(), 250);
            Path file = Files.createTempFile("bank-test", ".dat");
            try {
                f.bank.save(file);
                BankService loaded = BankService.load(file);
                Session s = loaded.login("alice", "alice123");
                eq(5250, loaded.getBalance(s, f.aliceSavings.getAccountNo()));
                eq(3, loaded.listAccounts(loaded.login("teller", "teller123")).size());
                loaded.deposit(s, f.aliceSavings.getAccountNo(), 1);
                List<Transaction> h = loaded.getMiniStatement(s, f.aliceSavings.getAccountNo());
                yes(h.get(h.size() - 1).getTxnId() > h.get(h.size() - 2).getTxnId(), "txn ids must keep increasing");
                throwsA(InvalidLoginException.class, () -> loaded.login("alice", "wrong"));
            } finally {
                Files.deleteIfExists(file);
            }
        });
        test("console app: customer logs in, views balance and logs out", () -> {
            BankService bank = new BankService("BR001", "Demo");
            DemoData.seed(bank);
            String script = "1\nalice\nalice123\n1\nACC100001\n0\n0\n";
            String output = runConsole(bank, script);
            yes(output.contains("Welcome, alice (CUSTOMER)"), output);
            yes(output.contains("Balance: 25000.00"), output);
            yes(output.contains("Logged out."), output);
        });
        test("console app: errors are shown, not thrown (wrong password, overdraw, bad number)", () -> {
            BankService bank = new BankService("BR001", "Demo");
            DemoData.seed(bank);
            String script = "1\nalice\nnope\n"            // failed login
                    + "1\nalice\nalice123\n5\nACC100001\n24500\n" // breaks the minimum balance
                    + "4\nACC100001\nabc\n"               // not a number
                    + "6\nACC100001\nACC100002\n100\n"    // transfer works
                    + "0\n0\n";
            String output = runConsole(bank, script);
            yes(output.contains("Login failed"), output);
            yes(output.contains("Error: Insufficient funds in ACC100001"), output);
            yes(output.contains("Error: 'abc' is not a number"), output);
            yes(output.contains("Transfer successful."), output);
        });
        test("console app: manager approves a loan end to end", () -> {
            BankService bank = new BankService("BR001", "Demo");
            DemoData.seed(bank);
            String script = "1\nbob\nbob123\n7\nACC100003\n12000\n12\nCar repair\n0\n"
                    + "1\nadmin\nadmin123\n10\nLN0001\nA\n0\n0\n";
            String output = runConsole(bank, script);
            yes(output.contains("Application LN0001 submitted. Status: APPLIED"), output);
            yes(output.contains("Loan approved and disbursed."), output);
        });

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------- helpers ----------

    static Transaction last(Account a) {
        List<Transaction> h = a.getHistory();
        return h.get(h.size() - 1);
    }

    static int countOwn(List<Account> accounts, String customerId) {
        int n = 0;
        for (Account a : accounts) if (a.getCustomerId().equals(customerId)) n++;
        return n;
    }

    static void repeat(Fixture f, String from, String to) {
        try {
            for (int i = 0; i < 500; i++) f.bank.transfer(f.alice, from, to, 10);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String runConsole(BankService bank, String script) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        new ConsoleApp(bank, new ByteArrayInputStream(script.getBytes()), new PrintStream(buf), null).run();
        return buf.toString();
    }

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
