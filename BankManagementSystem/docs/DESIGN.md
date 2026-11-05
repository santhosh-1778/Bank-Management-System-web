# Design documents

All diagrams are [Mermaid](https://mermaid.js.org/) and render directly on GitHub. They match the code in `src/`.

## Use case diagram

```mermaid
flowchart LR
    Customer([Customer])
    Teller([Teller])
    Manager([Manager / Admin])
    Manager -- "inherits from" --> Teller

    subgraph BMS [Bank Management System]
        Login(Login)
        Open(Open Account)
        Update(Update Customer Profile)
        Close(Close Account)
        Deposit(Deposit)
        Withdraw(Withdraw)
        Transfer(Transfer Funds)
        Balance(View Balance / Mini-statement)
        Statement(Generate Statement)
        Apply(Apply for Loan)
        EMI(Calculate EMI)
        Approve(Approve / Reject Loan)
        Repay(Repay Loan)
        Users(Manage Users)
        Record(Record Transaction)
    end

    Customer --- Login & Deposit & Withdraw & Transfer & Balance & Statement & Apply & Repay
    Teller --- Login & Open & Update & Close & Deposit & Withdraw & Statement
    Manager --- Approve & Users
    Apply -. include .-> EMI
    Deposit -. include .-> Record
    Withdraw -. include .-> Record
    Transfer -. include .-> Record
```

## Class diagram

```mermaid
classDiagram
    class Branch {
        -String branchId
        -String location
        +addAccount(Account a)
        +findAccount(String no) Account
        +addEmployee(Employee e)
    }
    class Employee {
        <<abstract>>
        -String empId
        -String name
        +processRequest(String) String
    }
    class Teller { +openAccount() +closeAccount() }
    class Manager { +approveLoan(Loan) +manageUsers() }
    class Customer {
        -String customerId
        -String name
        -String address
        -String phone
        +register()$ Customer
        +updateProfile(name, address, phone)
    }
    class User {
        -String userId
        -String passwordHash
        -Role role
        -int failedAttempts
        +login(String pwd) boolean
        +lock()
    }
    class Account {
        <<abstract>>
        #String accountNo
        #double balance
        #LocalDate openDate
        +deposit(double)
        +withdraw(double)
        +transferIn(double)
        +transferOut(double)
        +getBalance() double
        #canWithdraw(double) boolean
    }
    class SavingsAccount {
        -double interestRate
        +MIN_BALANCE = 1000
        +addInterest() double
    }
    class CurrentAccount {
        -double overdraftLimit
        +checkOverdraft() boolean
    }
    class Transaction {
        -int txnId
        -TransactionType type
        -double amount
        -LocalDateTime date
        -double balanceAfter
        +getDetails() String
    }
    class Loan {
        -String loanId
        -double principal
        -double rate
        -int tenureMonths
        -LoanStatus status
        +calculateEMI() double
        +approve()
        +reject()
        +applyRepayment(double)
    }

    Employee <|-- Teller
    Employee <|-- Manager
    Account <|-- SavingsAccount
    Account <|-- CurrentAccount
    Customer "1" --> "1..*" Account : owns
    Customer "1" --> "0..*" Loan : applies for
    Manager "1" --> "0..*" Loan : approves
    Account "1" *-- "0..*" Transaction : records
    Branch o-- Account
    Branch o-- Employee
    User "1" --> "0..1" Customer : logs in as
    Teller ..> Account : opens / closes
```

`BankService` (package `service`) is the single entry point used by the UI. `AccountFactory` creates accounts by type name, `AuthService` handles login and lock-out, `Session` handles the idle timeout and `AuditLog` records critical actions.

## Sequence: Withdraw (UC-2)

```mermaid
sequenceDiagram
    actor Customer
    participant UI as ConsoleApp
    participant BS as BankService
    participant A as Account
    participant T as Transaction
    Customer->>UI: account no. and amount
    UI->>BS: withdraw(session, accountNo, amount)
    BS->>BS: validate session, check ownership
    BS->>A: withdraw(amount)
    A->>A: canWithdraw(amount)
    alt funds available
        A->>A: balance = balance - amount
        A->>T: new Transaction(WITHDRAW, amount, balance)
        A-->>BS: done
        BS-->>UI: success
        UI-->>Customer: confirmation
    else insufficient funds
        A-->>BS: InsufficientFundsException
        BS-->>UI: error
        UI-->>Customer: error message
    end
```

## Sequence: Fund transfer (UC-3)

```mermaid
sequenceDiagram
    actor Customer
    participant UI as ConsoleApp
    participant BS as BankService
    participant S as Source Account
    participant D as Destination Account
    Customer->>UI: source, destination, amount
    UI->>BS: transfer(session, from, to, amount)
    BS->>D: ensureUsable()
    BS->>S: transferOut(amount)
    alt source has enough funds
        S-->>BS: ok (TRANSFER_OUT recorded)
        BS->>D: transferIn(amount)
        D-->>BS: ok (TRANSFER_IN recorded)
        BS-->>UI: success
        UI-->>Customer: confirmation
    else insufficient funds or error
        S-->>BS: exception
        Note over BS,S: if the credit fails after the debit,<br/>BS calls S.undoLast() so neither side changes
        BS-->>UI: failure
        UI-->>Customer: error message
    end
```

## State diagrams

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: account opened
    ACTIVE --> DORMANT: no activity for 12 months
    DORMANT --> ACTIVE: customer transaction
    ACTIVE --> FROZEN: manager freezes
    FROZEN --> ACTIVE: manager unfreezes
    ACTIVE --> CLOSED: close request (balance zero, no active loan)
    DORMANT --> CLOSED: close request
    CLOSED --> [*]
```

```mermaid
stateDiagram-v2
    [*] --> APPLIED: customer applies
    APPLIED --> APPROVED: manager approves
    APPLIED --> REJECTED: manager rejects
    APPROVED --> DISBURSED: amount credited
    DISBURSED --> CLOSED: fully repaid
    REJECTED --> [*]
    CLOSED --> [*]
```

## Activity diagram: loan approval

```mermaid
flowchart TD
    A([Start]) --> B[Customer submits application: amount, tenure, purpose]
    B --> C[System calculates EMI]
    C --> D[System saves application as APPLIED]
    D --> E[Manager reviews application]
    E --> F{Eligible?}
    F -- yes --> G[Approve loan]
    F -- no --> H[Reject loan]
    G --> I[Status APPROVED]
    I --> J[Disburse amount to account]
    J --> K[Record transaction, status DISBURSED]
    H --> L[Status REJECTED]
    K --> M[Customer sees the result]
    L --> M
    M --> N([End])
```

## Principles and patterns

| Concept | Where |
|---|---|
| Encapsulation | `Account.balance` is protected. It changes only through `deposit`, `withdraw`, `transferIn`, `transferOut`, which validate every change |
| Abstraction | `Account` and `Employee` are abstract. The service works with "an account" without knowing the type |
| Inheritance | `SavingsAccount`, `CurrentAccount` extend `Account`. `Teller`, `Manager` extend `Employee` |
| Polymorphism | `withdraw()` calls the abstract `canWithdraw()`, which each account type implements differently |
| Factory | `AccountFactory.create("SAVINGS", ...)` so the UI never calls constructors |
| Template method | `Account.debit()` fixes the steps and calls the `canWithdraw()` hook |
| Exceptions | `InsufficientFundsException` (checked), `AccountNotFoundException`, `InvalidLoginException`, `AccessDeniedException`, `SessionExpiredException`, `IllegalArgumentException` for bad amounts |
