import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import service.BankService;
import service.DemoData;
import ui.ConsoleApp;

/**
 * Entry point.
 *   java -cp out Main                 start (data is kept in data/bank.dat)
 *   java -cp out Main --demo          start with sample users and accounts (only on empty data)
 *   java -cp out Main --data file.dat use a different data file
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Path data = Paths.get("data", "bank.dat");
        boolean demo = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--demo": demo = true; break;
                case "--data": data = Paths.get(args[++i]); break;
                default:
                    System.out.println("Usage: java -cp out Main [--demo] [--data <file>]");
                    return;
            }
        }

        BankService bank;
        if (Files.exists(data)) {
            bank = BankService.load(data);
            if (demo) System.out.println("Existing data found in " + data + ": --demo ignored.");
        } else {
            bank = new BankService("BR001", "Main Branch");
            if (demo) {
                DemoData.seed(bank);
                System.out.println("Demo data created. Logins: admin/admin123 (manager), teller/teller123, alice/alice123, bob/bob123");
            } else {
                bank.bootstrapAdmin("admin", "admin123");
                System.out.println("First run: manager login admin / admin123 created. Create real users and stop using this one.");
            }
            save(bank, data);
        }
        new ConsoleApp(bank, System.in, System.out, data).run();
    }

    private static void save(BankService bank, Path data) throws IOException {
        bank.save(data);
    }
}
