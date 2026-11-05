# Testing

Run all tests with `./test.sh` (or `test.bat`). The runner prints `PASS` or `FAIL` per test and exits with code 1 if any test fails. The same command runs automatically on GitHub through `.github/workflows/ci.yml`.

## Test cases from the design guide (Step 8)

| ID | Scenario | Input | Expected result | Req. |
|---|---|---|---|---|
| TC1 | Deposit a positive amount | Deposit 500 into a savings account holding 5000 | Balance 5500, DEPOSIT transaction recorded | FR5, FR8 |
| TC2 | Withdraw within balance | Withdraw 1000 from savings holding 5000 | Balance 4000 | FR6 |
| TC3 | Breach savings minimum | Withdraw 4500 from savings holding 5000 (minimum 1000) | `InsufficientFundsException`, balance stays 5000 | FR9 |
| TC4 | Use overdraft | Withdraw 2500 from current holding 2000, limit 1000 | Allowed, balance -500 | FR6 |
| TC5 | Successful transfer | Transfer 1500 from savings (5000) to current (2000) | 3500 and 3500, two transactions recorded | FR7 |
| TC6 | Transfer with insufficient funds | Transfer 4500 from savings (5000) | Rejected, both balances and histories unchanged | FR7 |
| TC7 | Transfer to unknown account | Transfer 100 to a missing account number | `AccountNotFoundException`, source unchanged | FR7 |
| TC8 | Close account with balance | Close an account holding money | Rejected: balance must be zero. Closes after the balance is zero | FR4 |
| TC9 | Account lock | Three wrong passwords in a row | User locked, fourth attempt refused even with the right password | FR17 |

## Additional tests

| Area | What is checked |
|---|---|
| Validation | zero, negative and NaN amounts rejected, savings opening deposit minimum, unknown account type, invalid phone number |
| Atomicity | a transfer to a frozen account fails and leaves no entry in the source history, `undoLast`, 2 threads doing 1000 transfers keep the total unchanged |
| Access control | customers cannot see, withdraw from or transfer from another customer's account, tellers and customers cannot do manager tasks, disabled users cannot log in, duplicate user IDs and short passwords refused |
| Security | passwords stored hashed, session expires after idle time and stays expired, logged-out session unusable |
| Reports | mini-statement returns the last 10 in order, date range statement, unique increasing transaction ids |
| Loans | EMI formula, apply/approve/disburse/repay to closed, reject, role rules, repayment limits, active loan blocks closing the account |
| Housekeeping | monthly interest (savings only), dormant accounts after 12 months and reactivation, freeze/unfreeze, audit entries |
| Persistence | save and load keeps balances, users, hashes and transaction id sequence |
| Console UI | scripted sessions for login, error messages, transfer and a full loan approval |

## Requirement traceability

| Requirement | Code | Tests |
|---|---|---|
| FR1, FR2 | `BankService.openAccount`, `AccountFactory` | account numbers unique, opening rules |
| FR3 | `Customer.updateProfile`, `BankService.updateProfile` | profile update |
| FR4 | `Account.close`, `BankService.closeAccount` | TC8, loan blocks closing |
| FR5 | `Account.deposit` | TC1 |
| FR6 | `Account.withdraw`, `canWithdraw` | TC2, TC4 |
| FR7 | `BankService.transfer` | TC5-TC7, atomic transfer, concurrency |
| FR8 | `Transaction`, `Account.record` | TC1, transaction fields test |
| FR9 | `SavingsAccount.canWithdraw` | TC3 |
| FR10 | `Account.getMiniStatement` | mini statement |
| FR11 | `Account.getStatement` | date range |
| FR12-FR15 | `Loan`, `BankService.applyForLoan/approveLoan/rejectLoan/repayLoan` | loan tests |
| FR16 | `SavingsAccount.addInterest`, `BankService.creditMonthlyInterest` | interest test |
| FR17 | `User.login`, `AuthService` | TC9 |
| FR18 | `Role`, `BankService` role checks | access control tests |
| FR19 | `BankService.createEmployee/setUserEnabled/unlockUser` | manager tests |
| NFR security | `PasswordHasher`, `Session` | hashed passwords, session timeout |
| NFR audit | `AuditLog` | audit test |
| NFR concurrency | `synchronized` service methods | concurrency test |
