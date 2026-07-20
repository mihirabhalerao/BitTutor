package cli.commands;

import cli.BitCommand;
import engine.*;
import model.CommitNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class CommitCommand implements BitCommand {
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
        if (tokens.size() < 4 || !tokens.get(2).equals("-m")) {
            System.out.println("Error: Syntax invalid. Use: bit commit -m \"message\"");
            return;
        }
        String commitMessage = tokens.get(3);
        Path playgroundPath = Paths.get("bit-playground");

        if (!Files.exists(playgroundPath)) {
            System.out.println("No root directory initialized. Please run 'bit init' first.");
            return;
        }

        try {
            // Pre-commit validation step: look for any unhandled conflict boundaries
            try (Stream<Path> walk = Files.walk(playgroundPath)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();
                for (Path p : files) {
                    if (Files.readString(p).contains("<<<<<<< HEAD")) {
                        System.out.println("Commit Rejected: Raw conflict markers detected.");
                        return;
                    }
                }
            }

            // Build the Hierarchical Nested Merkle Tree recursively
            String treeRootHash = MerkleTreeHelper.buildMerkleTreeRecursively(playgroundPath, storageEngine);

            // Get latest commit on branch
            String activeBranch = storageEngine.getHeadPointer();
            String parentCommitHash = storageEngine.getCommitHashFromBranch(activeBranch);
            List<String> parents = new ArrayList<>();
            if (parentCommitHash != null)
                parents.add(parentCommitHash);

            // Create a CommitNode, save it to StorageEngine, and update the branch pointer.
            String commitContentString = commitMessage + treeRootHash + System.currentTimeMillis() + parents.toString();
            String commitHash = HashingUtility.hashString(commitContentString);

            CommitNode commit = new CommitNode(commitHash, commitMessage, treeRootHash, parents);
            storageEngine.saveCommit(commitHash, commit);

            storageEngine.updateBranchPointer(activeBranch, commitHash);
            storageEngine.getTrieEngine().insert(commitHash);

            System.out.println(
                    "[" + activeBranch + " " + commitHash.substring(0, 7) + "] Commit Successful: " + commitMessage);
        } catch (IOException e) {
            System.out.println("Fatal Error during recursive commit generation sequence: " + e.getMessage());
        }
    }
}