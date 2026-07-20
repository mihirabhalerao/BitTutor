package cli.commands;

import java.util.List;

import cli.BitCommand;
import engine.FileSystemIO;
import engine.StorageEngine;

public class BranchCommand implements BitCommand {
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
        if (tokens.size() == 2) {
            System.out.println("Current branch name: " + storageEngine.getHeadPointer());
            return;
        }

        String newBranchName = tokens.get(2);
        if (newBranchName.charAt(0) == '-') {
            System.out.println("Invalid syntax. No options allowed.");
            return;
        }

        if (storageEngine.branchExists(newBranchName)) {
            System.out.println("Branch with name '" + newBranchName + "' already exists.");
            return;
        }

        String activeBranch = storageEngine.getHeadPointer();
        String activeCommitHash = storageEngine.getCommitHashFromBranch(activeBranch);

        if (activeCommitHash == null) {
            System.out.println("Cannot create a branch in an empty repository. Make a commit first.");
            return;
        }

        storageEngine.updateBranchPointer(newBranchName, activeCommitHash);
        storageEngine.getTrieEngine().insert(newBranchName);
        System.out.println(
                "Created branch '" + newBranchName + "' pointing to commit: " + activeCommitHash.substring(0, 7));
    }
}
