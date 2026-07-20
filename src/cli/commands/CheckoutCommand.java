package cli.commands;

import java.util.List;

import cli.BitCommand;
import engine.FileSystemIO;
import engine.MerkleTreeHelper;
import engine.StorageEngine;

public class CheckoutCommand implements BitCommand{
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
        if (tokens.size() != 3) {
            System.out.println("Invalid syntax. Please use: bit checkout <target-name>.");
            return;
        }

        String target = tokens.get(2);
        if (target.length() == 2 && target.charAt(0) == '-') {
            System.out.println("Invalid syntax. No options allowed.");
            return;
        }

        // If workspace is dirty, ask user to commit before checking out. and halt the checkout.
        if (storageEngine.isWorkspaceDirty()) {
            System.out.println("Error: Your local changes to the following files would be overwritten by checkout, please commit your variations before switching branch lanes.");
            return; 
        }

        List<String> matches = storageEngine.getTrieEngine().searchPrefix(target);
        if (matches.isEmpty()) {
            System.out.println("No branch or commit starting with prefix '" + target + "' exists.");
            return;
        }

        String resolvedTarget = matches.get(0);
        String targetCommitHash = null;

        // If target is a branch, set head pointer to target
        if (storageEngine.branchExists(resolvedTarget)) {
            storageEngine.setHeadPointer(resolvedTarget);
            targetCommitHash = storageEngine.getCommitHashFromBranch(resolvedTarget);
            System.out.println("Switched context to branch '" + resolvedTarget + "'");
        } 
        
        // If target is commit, target is the same as targetCommitHash
        else {
            storageEngine.setHeadPointer(resolvedTarget);
            targetCommitHash = resolvedTarget;
            System.out.println("Switched context to explicit commit snapshot: " + targetCommitHash.substring(0, 7));
        }

        if (targetCommitHash == null) {
            System.out.println("Warning: Target branch has no snapshots recorded yet.");
            return;
        }

        // Restore workspace to the commitHash of the commit node or branch to checkout
        MerkleTreeHelper.restoreWorkspaceToCommit(targetCommitHash, storageEngine);
    }
}
