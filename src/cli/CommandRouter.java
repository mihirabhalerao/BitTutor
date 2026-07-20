package cli;

import java.util.*;
import cli.commands.*;
import engine.FileSystemIO;
import engine.StorageEngine;

public class CommandRouter {
    private final StorageEngine storageEngine;
    private final FileSystemIO fileSystemIO;
    private final Map<String, BitCommand> commandRegistry;

    public CommandRouter() {
        this.storageEngine = new StorageEngine();
        this.fileSystemIO = new FileSystemIO();
        this.commandRegistry = new HashMap<>();
        
        initializeCommands();
    }

    private void initializeCommands() {
        // Register instantiated classes
        commandRegistry.put("init", new InitCommand());
        commandRegistry.put("edit", new EditCommand());
        commandRegistry.put("commit", new CommitCommand());
        commandRegistry.put("branch", new BranchCommand());
        commandRegistry.put("checkout", new CheckoutCommand());
        commandRegistry.put("merge", new MergeCommand());
        commandRegistry.put("rebase", new RebaseCommand());
        commandRegistry.put("diff", new DiffCommand());

        
        // Use inline Lambdas for simpler menu
        commandRegistry.put("log", (tokens, engine, io) -> {
            if (!java.nio.file.Files.exists(java.nio.file.Paths.get("bit-playground"))) {
                System.out.println("Error: No repository initialized. Please run 'bit init' first.");
                return;
            }
            engine.printCommitGraphLog();
        });

        commandRegistry.put("stats", (tokens, engine, io) -> engine.printStorageMetrics());
        commandRegistry.put("help", (tokens, engine, io) -> printHelpMenu());
    }

    public void handleInput(String input) {
        List<String> tokens = CommandParser.tokenize(input);
        if (tokens.isEmpty()) return;

        String baseCommand = tokens.get(0);
        if (!baseCommand.equalsIgnoreCase("bit")) {
            System.out.println("Error: Command must start with 'bit'. Example: 'bit init'.");
            return;
        }

        if (tokens.size() < 2) {
            System.out.println("Error: Missing sub-command option. Try 'bit init' or 'bit help'.");
            return;
        }

        String subCommand = tokens.get(1).toLowerCase();
        
        // Functional Router Lookup
        BitCommand targetCommand = commandRegistry.get(subCommand);
        if (targetCommand != null) {
            targetCommand.execute(tokens, storageEngine, fileSystemIO);
        } else {
            System.out.println("Error: Unknown command 'bit " + tokens.get(1) + "'. Type 'bit help'.");
        }
    }

    private void printHelpMenu() {
        System.out.println("\nAvailable Commands:");
        System.out.println("  bit init                            - Setup a new workspace directory.");
        System.out.println("  bit edit <file/dir>                 - Open an existing file/directory or confirm creation.");
        System.out.println("  bit edit -n <file/dir>              - Force create a new file/directory structural node by default.");
        System.out.println("  bit commit -m \"msg\"               - Recursively snapshot workspace into a hierarchical Merkle Tree.");
        System.out.println("  bit branch <name>                   - Construct a new tracking tag reference on the current commit vertex.");
        System.out.println("  bit checkout <target>               - Safely switch branch contexts (blocked if workspace has uncommitted changes).");
        System.out.println("  bit diff                            - Run Myers script algorithm comparing live workspace against HEAD.");
        System.out.println("  bit diff <commitA> <commitB>        - Run Myers script algorithm comparing two explicit historical commits.");
        System.out.println("  bit merge <branch>                  - Reconcile divergent branch paths via a 3-Way Merkle staging reconciliation.");
        System.out.println("  bit rebase <branch>                 - Replay historical branch patches sequentially onto a moving baseline target.");
        System.out.println("  bit log                             - Render a linear chronological list of commits matching production standard.");
        System.out.println("  bit stats                           - Render storage statistics and branch details.");
    }
}