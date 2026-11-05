# Bank Management System

An object-oriented banking application in Java, built for the **Object-Oriented Modeling (OOM)** course. It follows the 8-step process from requirements to tested code: requirements, use cases, class identification, class diagram, behaviour models, design details, implementation and testing.

Pure Java 17, no external libraries, no build tool needed. Data is saved to a local file.

## Features

| Area | What it does | Requirements |
|---|---|---|
| Accounts | Open savings or current accounts with unique numbers, update customer profiles, close accounts (zero balance and no active loan only) | FR1-FR4 |
| Transactions | Deposit, withdraw, atomic transfer, full transaction history, savings minimum balance, current account overdraft | FR5-FR9 |
| Reports | Balance, mini-statement (last 10), statement for a date range | FR10-FR11 |
| Loans | Apply, EMI calculation, manager approve/reject with disbursement, repayment tracking | FR12-FR15 |
| Interest | Monthly interest credited to savings accounts | FR16 |
| Security | Login, salted PBKDF2 password hashes, lock after 3 wrong passwords, role-based access (Customer, Teller, Manager), manager creates/disables users | FR17-FR19 |
| Non-functional | Session timeout, audit log of critical actions, thread-safe operations, atomic transfers, crash-safe saving | NFRs |

## Quick start

You need **JDK 17 or later** (`java -version`).

```bash
# Linux / macOS / Git Bash
./run.sh --demo        # build and start with sample data
./test.sh              # build and run all tests
```

```bat
:: Windows
run.bat --demo
test.bat
```

Or by hand:

```bash
javac -d out $(find src test -name '*.java')
java -cp out Main --demo
java -cp out TestRunner
```

Demo logins (created by `--demo`; for demonstration only):

| User | Password | Role |
|---|---|---|
| `admin` | `admin123` | Manager |
| `teller` | `teller123` | Teller |
| `alice` | `alice123` | Customer (savings ACC100001, current ACC100002) |
| `bob` | `bob123` | Customer (savings ACC100003) |

Without `--demo` the first run creates only `admin / admin123`. Data is kept in `data/bank.dat` (delete it to start over).

### Suggested demo for the presentation

1. Log in as `alice`, check the balance of `ACC100001`, transfer 1500 to `ACC100002`, try to withdraw too much (rejected by the minimum balance).
2. Apply for a loan as `bob` (option 7). EMI is shown before submitting.
3. Log in as `admin`, approve the loan (option 10), view the audit log (option 13).
4. Enter a wrong password three times for `alice`: she is locked. Unlock her as `admin` (option 11).

## Project layout

```
src/
  Main.java                  entry point
  model/                     domain classes from the class diagram
    Account (abstract), SavingsAccount, CurrentAccount, Transaction, TransactionType, AccountStatus
    Customer, Loan, LoanStatus, User, Role, PasswordHasher
    Employee (abstract), Teller, Manager, Branch
    InsufficientFundsException, AccountNotFoundException, InvalidLoginException,
    AccessDeniedException, SessionExpiredException
  service/                   application logic
    BankService (single entry point), AuthService, Session, AuditLog, AccountFactory, DemoData
  ui/
    ConsoleApp               menu, calls only BankService
test/
  TestRunner.java            40 tests, no JUnit needed
docs/
  DESIGN.md                  use case, class, sequence, state and activity diagrams (Mermaid)
  TESTING.md                 test cases and requirement traceability
```

## Design highlights

- **Encapsulation and polymorphism:** `Account.balance` is protected and changes only through validated methods. `withdraw()` calls the abstract `canWithdraw()`, implemented differently by `SavingsAccount` (minimum balance) and `CurrentAccount` (overdraft).
- **Factory pattern:** `AccountFactory` creates accounts from a type name.
- **Atomic transfers:** the source is debited first (if that fails nothing has changed), then the destination is credited. If the credit fails, the debit is undone. `BankService` methods are `synchronized`, so concurrent operations cannot corrupt a balance.
- **Security:** passwords are salted and hashed with PBKDF2, never stored in plain text. Every service call checks the session timeout and the user's role. Customers can only touch their own accounts.
- **Audit:** every login, failed login and critical action is logged with user and timestamp.

See [docs/DESIGN.md](docs/DESIGN.md) for all diagrams and [docs/TESTING.md](docs/TESTING.md) for test cases.

## Assumptions and limitations

- Single branch, single currency, no external payment gateway: transfers are internal only.
- Money uses `double`, as in the course guide. A production system would use `BigDecimal` or integer cents.
- Storage is Java serialization into one file. A production system would use a database (SQLite or MySQL) through a `dao` package.
- The loan rate is a fixed 10% per year (`BankService.DEFAULT_LOAN_RATE`), and there is no eligibility rule beyond manager review.

## Possible extensions

Database persistence, a GUI (JavaFX or Swing), per-account interest strategies (Strategy pattern), loan eligibility rules, scheduled monthly interest, e-mail/SMS notifications.

## Web version

The project now includes a dependency-free browser interface while keeping the existing Java OOP/service layer.

### Run in GitHub Codespaces / Linux

```bash
chmod +x web.sh
./web.sh
```

The server listens on port **8080**. In Codespaces, open the forwarded port 8080 in the **Ports** panel to launch the banking website.

### Run on Windows

```bat
web.bat
```

Then open `http://localhost:8080`.

### Web demo accounts

- Manager: `admin / admin123`
- Customer: `alice / alice123`
- Customer: `bob / bob123`

The web version supports login, dashboard, accounts, transactions, deposits, withdrawals, transfers, and loans. Data is persisted to `data/bank-web.dat`.
