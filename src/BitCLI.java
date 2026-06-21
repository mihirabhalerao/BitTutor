import java.util.Scanner;

import cli.CommandRouter;
import engine.FileSystemIO;

public class BitCLI {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        CommandRouter router = new CommandRouter();
        FileSystemIO fileSystemIO = new FileSystemIO();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            fileSystemIO.purgePlayground();
        }));

        System.out.println("=========================================");
        System.out.println("  Welcome to bit - Version Control Engine ");
        System.out.println("  Type 'exit' to terminate the program.   ");
        System.out.println("=========================================\n");

        while (true) {
            System.out.print("\nbit-vcs > ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Terminating bit CLI session. Goodbye!");
                break;
            }

            // Let the router handle parsing and execution
            router.handleInput(input);
        }
        scanner.close();
    }
}
