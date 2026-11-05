package service;

import model.Customer;
import model.Role;

/** Sample data so the project can be demonstrated immediately. Passwords here are for demos only. */
public final class DemoData {
    private DemoData() { }

    public static void seed(BankService bank) {
        bank.bootstrapAdmin("admin", "admin123");
        Session admin = bank.login("admin", "admin123");
        bank.createEmployee(admin, "Tara Teller", "teller", "teller123", Role.TELLER);

        Session teller = bank.login("teller", "teller123");
        Customer alice = bank.registerCustomer(teller, "Alice Rao", "12 MG Road, Hyderabad", "9876543210", "AADHAAR-1111");
        Customer bob = bank.registerCustomer(teller, "Bob Kumar", "7 Lake View, Vizag", "9123456780", "AADHAAR-2222");
        bank.openAccount(teller, alice.getCustomerId(), "SAVINGS", 25000);
        bank.openAccount(teller, alice.getCustomerId(), "CURRENT", 10000);
        bank.openAccount(teller, bob.getCustomerId(), "SAVINGS", 8000);

        bank.createCustomerLogin(admin, "alice", "alice123", alice.getCustomerId());
        bank.createCustomerLogin(admin, "bob", "bob123", bob.getCustomerId());
        bank.logout(teller);
        bank.logout(admin);
    }
}
