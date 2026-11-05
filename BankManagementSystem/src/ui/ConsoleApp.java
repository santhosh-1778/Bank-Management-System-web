package ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import model.*;
import service.AuditLog;
import service.BankService;
import service.Session;

/** Console menu. It only calls BankService, never the model classes directly for changes. */
public class ConsoleApp {
    private static class EndOfInput extends RuntimeException { }

    private final BankService bank;
    private final Scanner in;
    private final PrintStream out;
    private final Path dataFile; // null = do not save

    public ConsoleApp(BankService bank, InputStream input, PrintStream out, Path dataFile) {
        this.bank = bank;
        this.in = new Scanner(input);
        this.out = out;
        this.dataFile = dataFile;
    }

    public void run() {
        out.println("==============================");
        out.println("   BANK MANAGEMENT SYSTEM");
        out.println("==============================");
        try {
            while (true) {
                out.println("\n1) Login\n0) Exit");
                String c = ask("Choose: ");
                if (c.equals("0")) break;
                if (c.equals("1")) {
                    try {
                        Session s = bank.login(ask("User ID: "), ask("Password: "));
                        out.println("Welcome, " + s.getUser().getUserId() + " (" + s.getUser().getRole() + ")");
                        sessionLoop(s);
                    } catch (InvalidLoginException e) {
                        out.println("Login failed: " + e.getMessage());
                    }
                }
            }
        } catch (EndOfInput e) {
            // input stream closed: leave quietly
        }
        out.println("Goodbye.");
    }

    private void sessionLoop(Session s) {
        Role role = s.getUser().getRole();
        while (true) {
            printMenu(role);
            String c = ask("Choose: ");
            if (c.equals("0")) {
                bank.logout(s);
                out.println("Logged out.");
                return;
            }
            try {
                if (role == Role.CUSTOMER) customerAction(s, c); else staffAction(s, role, c);
                save();
            } catch (SessionExpiredException e) {
                out.println(e.getMessage());
                return;
            } catch (InsufficientFundsException | RuntimeException e) {
                if (e instanceof EndOfInput) throw (EndOfInput) e;
                out.println("Error: " + e.getMessage());
            }
        }
    }

    private void printMenu(Role role) {
        out.println();
        if (role == Role.CUSTOMER) {
            out.println("1) Balance        2) Mini statement   3) Statement by dates");
            out.println("4) Deposit        5) Withdraw         6) Transfer");
            out.println("7) Apply for loan 8) Repay loan       9) My loans");
        } else {
            out.println("1) Register customer   2) Open account     3) Update profile   4) Close account");
            out.println("5) Deposit             6) Withdraw         7) Balance + mini statement");
            out.println("8) Statement by dates  9) List all accounts");
            if (role == Role.MANAGER) {
                out.println("10) Loan applications  11) Manage users   12) Credit monthly interest");
                out.println("13) Audit log          14) Freeze/unfreeze account");
            }
        }
        out.println("0) Logout");
    }

    // ---- customer menu ------------------------------------------------------------------------

    private void customerAction(Session s, String c) throws InsufficientFundsException {
        switch (c) {
            case "1": listAccounts(s); out.printf("Balance: %.2f%n", bank.getBalance(s, ask("Account no: "))); break;
            case "2": listAccounts(s); printStatement(bank.getMiniStatement(s, ask("Account no: "))); break;
            case "3": statementByDates(s); break;
            case "4": listAccounts(s); bank.deposit(s, ask("Account no: "), number("Amount: ")); out.println("Deposit successful."); break;
            case "5": listAccounts(s); bank.withdraw(s, ask("Account no: "), number("Amount: ")); out.println("Withdrawal successful."); break;
            case "6":
                listAccounts(s);
                bank.transfer(s, ask("From account: "), ask("To account: "), number("Amount: "));
                out.println("Transfer successful.");
                break;
            case "7": applyForLoan(s); break;
            case "8":
                listLoans(s);
                bank.repayLoan(s, ask("Loan ID: "), number("Amount: "));
                out.println("Repayment recorded.");
                break;
            case "9": listLoans(s); break;
            default: out.println("Unknown option.");
        }
    }

    private void applyForLoan(Session s) {
        listAccounts(s);
        String acc = ask("Account to receive the loan: ");
        double amount = number("Loan amount: ");
        int months = (int) number("Tenure (months): ");
        String purpose = ask("Purpose: ");
        out.printf("EMI would be %.2f at %.1f%% per year%n", Loan.calculateEMI(amount, BankService.DEFAULT_LOAN_RATE, months), BankService.DEFAULT_LOAN_RATE);
        Loan l = bank.applyForLoan(s, acc, amount, months, purpose);
        out.println("Application " + l.getLoanId() + " submitted. Status: " + l.getStatus());
    }

    // ---- staff menu ---------------------------------------------------------------------------

    private void staffAction(Session s, Role role, String c) throws InsufficientFundsException {
        switch (c) {
            case "1": {
                Customer cu = bank.registerCustomer(s, ask("Name: "), ask("Address: "), ask("Phone (digits): "), ask("ID proof: "));
                out.println("Registered customer " + cu.getCustomerId());
                break;
            }
            case "2": {
                Account a = bank.openAccount(s, ask("Customer ID: "), ask("Type (SAVINGS/CURRENT): "), number("Opening deposit: "));
                out.println("Opened account " + a.getAccountNo());
                break;
            }
            case "3":
                bank.updateProfile(s, ask("Customer ID: "), ask("New name (blank = keep): "), ask("New address (blank = keep): "), ask("New phone (blank = keep): "));
                out.println("Profile updated.");
                break;
            case "4": bank.closeAccount(s, ask("Account no: ")); out.println("Account closed."); break;
            case "5": bank.deposit(s, ask("Account no: "), number("Amount: ")); out.println("Deposit successful."); break;
            case "6": bank.withdraw(s, ask("Account no: "), number("Amount: ")); out.println("Withdrawal successful."); break;
            case "7": {
                String acc = ask("Account no: ");
                out.printf("Balance: %.2f%n", bank.getBalance(s, acc));
                printStatement(bank.getMiniStatement(s, acc));
                break;
            }
            case "8": statementByDates(s); break;
            case "9": listAccounts(s); break;
            default:
                if (role == Role.MANAGER) managerAction(s, c); else out.println("Unknown option.");
        }
    }

    private void managerAction(Session s, String c) {
        switch (c) {
            case "10": reviewLoans(s); break;
            case "11": manageUsers(s); break;
            case "12": out.println("Interest credited to " + bank.creditMonthlyInterest(s) + " savings accounts."); break;
            case "13": for (AuditLog.Entry e : bank.getAuditLog(s)) out.println(e); break;
            case "14": {
                String acc = ask("Account no: ");
                if (ask("Freeze (F) or unfreeze (U)? ").equalsIgnoreCase("F")) bank.freezeAccount(s, acc); else bank.unfreezeAccount(s, acc);
                out.println("Done.");
                break;
            }
            default: out.println("Unknown option.");
        }
    }

    private void reviewLoans(Session s) {
        listLoans(s);
        String id = ask("Loan ID to review (blank = back): ");
        if (id.isEmpty()) return;
        String d = ask("Approve (A) or reject (R)? ");
        if (d.equalsIgnoreCase("A")) { bank.approveLoan(s, id); out.println("Loan approved and disbursed."); }
        else if (d.equalsIgnoreCase("R")) { bank.rejectLoan(s, id); out.println("Loan rejected."); }
        else out.println("No change.");
    }

    private void manageUsers(Session s) {
        out.println("1) List users  2) Create employee  3) Create customer login  4) Enable/disable  5) Unlock");
        switch (ask("Choose: ")) {
            case "1":
                for (User u : bank.listUsers(s)) {
                    out.printf("%-12s %-9s %s%s%n", u.getUserId(), u.getRole(), u.isEnabled() ? "enabled" : "DISABLED", u.isLocked() ? ", LOCKED" : "");
                }
                break;
            case "2": {
                Role r = ask("Role (TELLER/MANAGER): ").equalsIgnoreCase("MANAGER") ? Role.MANAGER : Role.TELLER;
                String id = bank.createEmployee(s, ask("Name: "), ask("User ID: "), ask("Password (min 6): "), r);
                out.println("Created employee " + id);
                break;
            }
            case "3": bank.createCustomerLogin(s, ask("User ID: "), ask("Password (min 6): "), ask("Customer ID: ")); out.println("Login created."); break;
            case "4": bank.setUserEnabled(s, ask("User ID: "), ask("Enable (E) or disable (D)? ").equalsIgnoreCase("E")); out.println("Done."); break;
            case "5": bank.unlockUser(s, ask("User ID: ")); out.println("Unlocked."); break;
            default: out.println("Unknown option.");
        }
    }

    // ---- shared output ------------------------------------------------------------------------

    private void listAccounts(Session s) {
        out.println("Accounts:");
        for (Account a : bank.listAccounts(s)) {
            out.printf("  %-10s %-8s %-8s %-9s balance %.2f%n", a.getAccountNo(), a.getType(), a.getStatus(), a.getCustomerId(), a.getBalance());
        }
    }

    private void listLoans(Session s) {
        out.println("Loans:");
        for (Loan l : bank.listLoans(s)) {
            out.printf("  %-7s %-10s amount %.2f, %d months, EMI %.2f, %s, outstanding %.2f (%s)%n", l.getLoanId(), l.getAccountNo(),
                    l.getPrincipal(), l.getTenureMonths(), l.calculateEMI(), l.getStatus(), l.getOutstanding(), l.getPurpose());
        }
    }

    private void statementByDates(Session s) {
        String acc = ask("Account no: ");
        LocalDate from = date("From (yyyy-mm-dd): ");
        LocalDate to = date("To (yyyy-mm-dd): ");
        printStatement(bank.getStatement(s, acc, from, to));
    }

    private void printStatement(List<Transaction> txns) {
        out.printf("%-6s %-18s %12s %12s  %s%n", "ID", "TYPE", "AMOUNT", "BALANCE", "DATE");
        for (Transaction t : txns) {
            out.printf("%-6d %-18s %12.2f %12.2f  %s%n", t.getTxnId(), t.getType(), t.getAmount(), t.getBalanceAfter(), t.getDate().withNano(0));
        }
        if (txns.isEmpty()) out.println("(no transactions)");
    }

    // ---- input helpers ------------------------------------------------------------------------

    private String ask(String prompt) {
        out.print(prompt);
        out.flush();
        if (!in.hasNextLine()) throw new EndOfInput();
        return in.nextLine().trim();
    }

    private double number(String prompt) {
        String v = ask(prompt);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + v + "' is not a number");
        }
    }

    private LocalDate date(String prompt) {
        String v = ask(prompt);
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + v + "' is not a date (use yyyy-mm-dd)");
        }
    }

    private void save() {
        if (dataFile == null) return;
        try {
            bank.save(dataFile);
        } catch (IOException e) {
            out.println("Warning: could not save data: " + e.getMessage());
        }
    }
}
