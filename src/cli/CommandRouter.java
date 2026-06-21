package cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import engine.FileSystemIO;
import engine.HashingUtility;
import engine.StorageEngine;
import model.BlobNode;
import model.CommitNode;
import model.DirectoryTree;

public class CommandRouter {
    private final StorageEngine storageEngine;
    private final FileSystemIO fileSystemIO;

    public CommandRouter() {
        this.storageEngine = new StorageEngine();
        this.fileSystemIO = new FileSystemIO();
    }

    public void handleInput(String input) {
        List<String> tokens = CommandParser.tokenize(input);
        String baseCommand = tokens.get(0);
        if (!baseCommand.equalsIgnoreCase("bit")) {
            System.out.println("Error: Command must start with 'bit'. Example: 'bit init'.");
            return;
        }

        if (tokens.size() < 2) {
            System.out.println("Error: Missing sub-command option. Try 'bit init' or 'bit help'.");
            return;
        }

        routeSubCommand(tokens.get(1), tokens);
    }

    private void routeSubCommand(String subCommand, List<String> tokens) {
        switch (subCommand.toLowerCase()) {
            case "init":
                handleInit(tokens);
                break;
            case "edit":
                handleEdit(tokens);
                break;
            case "commit":
                handleCommit(tokens);
                break;
            case "branch":
                handleBranch(tokens);
                break;
            case "checkout":
                handleCheckout(tokens);
                break;
            case "diff":
                handleDiff(tokens);
                break;
            case "merge":
                handleMerge(tokens);
                break;
            case "rebase":
                handleRebase(tokens);
                break;
            case "log":
                handleLog(tokens);
                break;
            case "help":
                printHelpMenu();
                break;
            default:
                System.out.println("Error: Unknown command 'bit " + subCommand + "'. Type 'bit help'.");
        }
    }

    private void handleInit(List<String> tokens) {
        fileSystemIO.initalizePlayground();
    }

    private void handleEdit(List<String> tokens) {
        if (tokens.size() < 3) {
            System.out.println("Error: Specify a file name. Example: bit edit note.txt");
            return;
        }
        String fileName = tokens.get(2);
        List<String> matches = storageEngine.getTrieEngine().searchPrefix(fileName);

        if (matches.isEmpty()) {
            System.out.println("Error: File matching input pattern sequence '" + fileName + "' untracked.");
            return;
        }
        String resolvedFileName = matches.get(0);
        fileSystemIO.openNativeEditor(resolvedFileName);
    }

    private void handleCommit(List<String> tokens) {
        if (tokens.size() < 4 || !tokens.get(2).equals("-m")) {
            System.out.println("Error: Syntax invalid. Use: bit commit -m \"message\"");
            return;
        }
        String commitMessage = tokens.get(3);
        Path playgroundPath = Paths.get("bit-playground");

        if (!Files.exists(playgroundPath)) {
            System.out.println("No root directory initialized. Please try running 'bit init' first.");
            return;
        }

        try {
            StringBuilder treeContentBuilder = new StringBuilder();
            DirectoryTree currentTree = new DirectoryTree();

            try (Stream<Path> paths = Files.list(playgroundPath)) {
                List<Path> fileList = paths.filter(Files::isRegularFile).toList();

                for (Path filePath : fileList) {
                    String content = Files.readString(filePath);
                    if (content.contains("<<<<<<< HEAD") || content.contains("=======")
                            || content.contains(">>>>>>>")) {
                        System.out.println("🛑 Commit Rejected: Raw conflict markers detected.");
                        return;
                    }
                }

                for (Path filePath : fileList) {
                    String fileName = filePath.getFileName().toString();
                    String fileContent = Files.readString(filePath);
                    String blobHash = HashingUtility.hashString(fileContent);

                    if (!storageEngine.containsObjectHash(blobHash)) {
                        storageEngine.saveObject(blobHash, new BlobNode(blobHash, fileContent));
                    }

                    currentTree.addEntry(fileName, blobHash);
                    treeContentBuilder.append(fileName).append(":").append(blobHash).append(";");
                }
            }

            String treeRootHash = HashingUtility.hashString(treeContentBuilder.toString());
            currentTree.setHash(treeRootHash);
            storageEngine.saveObject(treeRootHash, currentTree);

            String activeBranch = storageEngine.getHeadPointer();
            String parentCommitHash = storageEngine.getCommitHashFromBranch(activeBranch);
            List<String> parents = new ArrayList<>();
            if (parentCommitHash != null) {
                parents.add(parentCommitHash);
            }
            String commitContentString = commitMessage + treeRootHash + System.currentTimeMillis() + parents.toString();
            String commitHash = HashingUtility.hashString(commitContentString);

            CommitNode commit = new CommitNode(commitHash, commitMessage, treeRootHash, parents);
            storageEngine.saveCommit(commitHash, commit);

            storageEngine.updateBranchPointer(activeBranch, commitHash);
            storageEngine.getTrieEngine().insert(commitHash);

            // Clean, minimalist output format
            System.out.println(
                    "[" + activeBranch + " " + commitHash.substring(0, 7) + "] Commit Successful: " + commitMessage);
        } catch (IOException e) {
            System.out.println("Fatal Error during commit processing: " + e.getMessage());
        }
    }

    private void handleBranch(List<String> tokens) {
        if (tokens.size() != 3) {
            System.out.println("Invalid syntax. Please use: bit branch <branch-name>.");
            return;
        }

        String newBranchName = tokens.get(2);
        if (newBranchName.length() == 2 && newBranchName.charAt(0) == '-') {
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

    private void handleCheckout(List<String> tokens) {
        if (tokens.size() != 3) {
            System.out.println("Invalid syntax. Please use: bit checkout <target-name>.");
            return;
        }

        String target = tokens.get(2);
        if (target.length() == 2 && target.charAt(0) == '-') {
            System.out.println("Invalid syntax. No options allowed.");
            return;
        }

        // --- ADDED PRE-FLIGHT DIRTY CHECK GATES ---
        if (storageEngine.isWorkspaceDirty()) {
            System.out.println("Error: Your local changes to the following files would be overwritten by checkout:");
            System.out.println("Please commit your variations before switching branch lanes.");
            return; // Halt the checkout completely
        }
        // ------------------------------------------

        List<String> matches = storageEngine.getTrieEngine().searchPrefix(target);
        if (matches.isEmpty()) {
            System.out.println("No branch or commit starting with prefix '" + target + "' exists.");
            return;
        }

        String resolvedTarget = matches.get(0);
        String targetCommitHash = null;

        if (storageEngine.branchExists(resolvedTarget)) {
            storageEngine.setHeadPointer(resolvedTarget);
            targetCommitHash = storageEngine.getCommitHashFromBranch(resolvedTarget);
            System.out.println("Switched context to branch '" + resolvedTarget + "'");
        } else {
            storageEngine.setHeadPointer(resolvedTarget);
            targetCommitHash = resolvedTarget;
            System.out.println("Switched context to explicit commit snapshot: " + targetCommitHash.substring(0, 7));
        }

        if (targetCommitHash == null) {
            System.out.println("Warning: Target branch has no snapshots recorded yet.");
            return;
        }

        restoreWorkspaceToCommit(targetCommitHash);
    }

    private void handleDiff(List<String> tokens) {
        Path playgroundPath = Paths.get("bit-playground");
        if (!Files.exists(playgroundPath)) {
            System.out.println("No root directory found. Please run 'bit init' first.");
            return;
        }

        if (tokens.size() != 2) {
            System.out.println("Syntax error. Correct format is 'bit diff'.");
            return;
        }

        String activeBranch = storageEngine.getHeadPointer();
        String commitHash = storageEngine.getCommitHashFromBranch(activeBranch);

        if (commitHash == null) {
            System.out.println("No commits exist for this branch.");
            return;
        }

        try {
            CommitNode commitNode = storageEngine.getCommit(commitHash);
            String rootTreeHash = commitNode.getRootTreeHash();
            DirectoryTree directoryTree = (DirectoryTree) storageEngine.getObject(rootTreeHash);

            for (Map.Entry<String, String> e : directoryTree.getEntries().entrySet()) {
                String fileName = e.getKey();
                String blobHash = e.getValue();

                BlobNode blobNode = (BlobNode) storageEngine.getObject(blobHash);
                List<String> historyLines = blobNode.getTextContent().lines().toList();

                List<String> liveLines = new ArrayList<>();
                Path liveFilePath = playgroundPath.resolve(fileName);

                if (Files.exists(liveFilePath)) {
                    liveLines = Files.readString(liveFilePath).lines().toList();
                }

                System.out.println("\nDiff tracking for file: " + fileName);
                System.out.println("----------------------------------------");

                String fileCacheKey = fileName + "_" + blobHash + "_" + liveLines.hashCode();
                List<String> coloredDiffReport = storageEngine.getLRUCache().get(fileCacheKey);

                if (coloredDiffReport != null) {
                    System.out.println("[Cache Hit]: Retrieved pre-computed delta from memory.");
                } else {
                    coloredDiffReport = storageEngine.getDiffEngine().computeDiff(historyLines, liveLines);
                    storageEngine.getLRUCache().put(fileCacheKey, coloredDiffReport);
                }

                for (String line : coloredDiffReport) {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Fatal Error parsing workspace streams: " + e.getMessage());
        }
    }

    private void handleMerge(List<String> tokens) {
        if (tokens.size() < 3) {
            System.out.println("Merge command requires the syntax 'bit merge <branch-name>'.");
            return;
        }

        String targetBranch = tokens.get(2);

        if (!storageEngine.branchExists(targetBranch)) {
            System.out.println("No branch named '" + targetBranch + "' exists.");
            return;
        }

        String activeBranch = storageEngine.getHeadPointer();
        String currentCommit = storageEngine.getCommitHashFromBranch(activeBranch);
        String targetCommit = storageEngine.getCommitHashFromBranch(targetBranch);

        if (currentCommit.equals(targetCommit)) {
            System.out.println("Already up-to-date.");
            return;
        }

        String ancestorCommitHash = storageEngine.findLowestCommonAncestor(targetCommit, currentCommit);
        if (ancestorCommitHash.equals(targetCommit)) {
            System.out.println("Already up-to-date.");
            return;
        } else if (ancestorCommitHash.equals(currentCommit)) {
            System.out.println("Fast-forwarding '" + activeBranch + "' to '" + targetBranch + "'...");
            storageEngine.updateBranchPointer(activeBranch, targetCommit);
            restoreWorkspaceToCommit(targetCommit);
        } else {
            System.out.println("Executing 3-way merge snapshot generation...");

            try {
                CommitNode currentCommitNode = storageEngine.getCommit(currentCommit);
                CommitNode targetCommitNode = storageEngine.getCommit(targetCommit);
                CommitNode lcaCommitNode = storageEngine.getCommit(ancestorCommitHash);

                DirectoryTree currTree = (DirectoryTree) storageEngine.getObject(currentCommitNode.getRootTreeHash());
                DirectoryTree targetTree = (DirectoryTree) storageEngine.getObject(targetCommitNode.getRootTreeHash());
                DirectoryTree lcaTree = (DirectoryTree) storageEngine.getObject(lcaCommitNode.getRootTreeHash());

                DirectoryTree mergedTree = new DirectoryTree();
                Path playgroundPath = Paths.get("bit-playground");

                Set<String> combinedFileKeys = new HashSet<>();
                combinedFileKeys.addAll(currTree.getEntries().keySet());
                combinedFileKeys.addAll(targetTree.getEntries().keySet());
                combinedFileKeys.addAll(lcaTree.getEntries().keySet());

                for (String fileName : combinedFileKeys) {
                    String hashLca = lcaTree.getEntries().get(fileName);
                    String hashCurrent = currTree.getEntries().get(fileName);
                    String hashTarget = targetTree.getEntries().get(fileName);

                    if (Objects.equals(hashCurrent, hashTarget)) {
                        if (hashCurrent != null)
                            mergedTree.addEntry(fileName, hashCurrent);
                    } else if (Objects.equals(hashCurrent, hashLca)) {
                        if (hashTarget != null) {
                            BlobNode targetNode = (BlobNode) storageEngine.getObject(hashTarget);
                            Files.writeString(playgroundPath.resolve(fileName), targetNode.getTextContent());
                            mergedTree.addEntry(fileName, hashTarget);
                        } else {
                            Files.deleteIfExists(playgroundPath.resolve(fileName));
                        }
                    } else if (Objects.equals(hashLca, hashTarget)) {
                        if (hashCurrent != null)
                            mergedTree.addEntry(fileName, hashCurrent);
                    } else {
                        System.out.println("🚨 Merge Conflict inside file: " + fileName);

                        String currentText = hashCurrent != null
                                ? ((BlobNode) storageEngine.getObject(hashCurrent)).getTextContent()
                                : "";
                        String targetText = hashTarget != null
                                ? ((BlobNode) storageEngine.getObject(hashTarget)).getTextContent()
                                : "";

                        String markerHeader = "\\ Clean up the conflict markers and this line once resolved";
                        String markerHead = "<<<<<<< HEAD (Current Branch)";
                        String markerDivider = "=======";
                        String markerTail = ">>>>>>> " + targetBranch + " (Incoming Branch)";

                        StringBuilder conflictMarker = new StringBuilder();
                        conflictMarker.append(markerHeader).append("\n")
                                .append(markerHead).append("\n")
                                .append(currentText)
                                .append(markerDivider).append("\n")
                                .append(targetText)
                                .append(markerTail).append("\n");

                        Path filePath = playgroundPath.resolve(fileName);
                        Files.writeString(filePath, conflictMarker.toString());

                        while (true) {
                            fileSystemIO.openNativeEditor(fileName);
                            List<String> lines = Files.readAllLines(filePath);
                            boolean markersStillExist = false;

                            for (String line : lines) {
                                String trimmed = line.trim();
                                if (trimmed.equals(markerHeader) || trimmed.equals(markerHead) ||
                                        trimmed.equals(markerDivider) || trimmed.equals(markerTail)) {
                                    markersStillExist = true;
                                    break;
                                }
                            }

                            if (markersStillExist) {
                                System.out.println("\n❌ Markers present. Re-opening editor...");
                            } else {
                                System.out.println("✅ Clean resolution confirmed: " + fileName);
                                String resolvedText = Files.readString(filePath);
                                String resolvedHash = HashingUtility.hashString(resolvedText);

                                BlobNode resolvedBlob = new BlobNode(resolvedHash, resolvedText);
                                storageEngine.saveObject(resolvedHash, resolvedBlob);
                                mergedTree.addEntry(fileName, resolvedHash);
                                break;
                            }
                        }
                    }
                }

                StringBuilder treeContentBuilder = new StringBuilder();
                for (Map.Entry<String, String> e : mergedTree.getEntries().entrySet()) {
                    treeContentBuilder.append(e.getKey()).append(":").append(e.getValue()).append(";");
                }

                String mergedTreeHash = HashingUtility.hashString(treeContentBuilder.toString());
                mergedTree.setHash(mergedTreeHash);
                storageEngine.saveObject(mergedTreeHash, mergedTree);

                List<String> mergeParents = new ArrayList<>();
                mergeParents.add(currentCommit);
                mergeParents.add(targetCommit);

                String mergeMessage = "Merge branch '" + targetBranch + "' into " + activeBranch;
                String commitContentString = mergeMessage + mergedTreeHash + System.currentTimeMillis()
                        + mergeParents.toString();
                String mergeCommitHash = HashingUtility.hashString(commitContentString);

                CommitNode mergeCommit = new CommitNode(mergeCommitHash, mergeMessage, mergedTreeHash, mergeParents);
                storageEngine.saveCommit(mergeCommitHash, mergeCommit);

                storageEngine.updateBranchPointer(activeBranch, mergeCommitHash);
                storageEngine.getTrieEngine().insert(mergeCommitHash);

                System.out.println("Recorded Merge Commit: " + mergeCommitHash.substring(0, 7));
            } catch (IOException e) {
                System.out.println("Fatal Error during merge: " + e.getMessage());
            }
        }
    }

    private void handleRebase(List<String> tokens) {
        if (tokens.size() < 3) {
            System.out.println("Error: Missing target branch. Syntax: bit rebase <branch-name>");
            return;
        }
        String targetBranch = tokens.get(2);

        if (!storageEngine.branchExists(targetBranch)) {
            System.out.println("Error: Branch '" + targetBranch + "' does not exist.");
            return;
        }
        String currentBranch = storageEngine.getHeadPointer();
        String currentHash = storageEngine.getCommitHashFromBranch(currentBranch);
        String targetHash = storageEngine.getCommitHashFromBranch(targetBranch);

        if (currentHash.equals(targetHash)) {
            System.out.println("Already up-to-date.");
            return;
        }

        String lcaHash = storageEngine.findLowestCommonAncestor(currentHash, targetHash);

        if (lcaHash.equals(targetHash)) {
            System.out.println("Already up-to-date.");
            return;
        }

        if (lcaHash.equals(currentHash)) {
            System.out.println("Fast-forwarding '" + currentBranch + "' straight to '" + targetBranch + "'...");
            storageEngine.updateBranchPointer(currentBranch, targetHash);
            restoreWorkspaceToCommit(targetHash);
            return;
        }

        System.out.println("Rebasing current branch '" + currentBranch + "' onto '" + targetBranch + "'...");
        List<CommitNode> commitsToReplay = storageEngine.getCommitsToReplay(currentHash, lcaHash);

        String currentParentPointer = targetHash;
        Path playgroundPath = Paths.get("bit-playground");

        try {
            for (CommitNode commitToReplay : commitsToReplay) {
                CommitNode currentParentNode = storageEngine.getCommit(currentParentPointer);
                String originalParentHash = commitToReplay.getParentHashes().get(0);
                CommitNode originalParentNode = storageEngine.getCommit(originalParentHash);

                DirectoryTree currentTree = (DirectoryTree) storageEngine
                        .getObject(currentParentNode.getRootTreeHash());
                DirectoryTree targetTree = (DirectoryTree) storageEngine.getObject(commitToReplay.getRootTreeHash());
                DirectoryTree lcaTree = (DirectoryTree) storageEngine.getObject(originalParentNode.getRootTreeHash());

                DirectoryTree mergedTree = new DirectoryTree();

                Set<String> combinedFileKeys = new HashSet<>();
                combinedFileKeys.addAll(currentTree.getEntries().keySet());
                combinedFileKeys.addAll(targetTree.getEntries().keySet());
                combinedFileKeys.addAll(lcaTree.getEntries().keySet());

                for (String fileName : combinedFileKeys) {
                    String hashLca = lcaTree.getEntries().get(fileName);
                    String hashCurrent = currentTree.getEntries().get(fileName);
                    String hashTarget = targetTree.getEntries().get(fileName);

                    if (Objects.equals(hashCurrent, hashTarget)) {
                        if (hashCurrent != null)
                            mergedTree.addEntry(fileName, hashCurrent);
                    } else if (Objects.equals(hashCurrent, hashLca)) {
                        if (hashTarget != null) {
                            BlobNode targetBlob = (BlobNode) storageEngine.getObject(hashTarget);
                            Files.writeString(playgroundPath.resolve(fileName), targetBlob.getTextContent());
                            mergedTree.addEntry(fileName, hashTarget);
                        } else {
                            Files.deleteIfExists(playgroundPath.resolve(fileName));
                        }
                    } else if (Objects.equals(hashTarget, hashLca)) {
                        if (hashCurrent != null)
                            mergedTree.addEntry(fileName, hashCurrent);
                    } else {
                        System.out.println("🚨 Rebase Merge Conflict inside file: " + fileName);

                        String currentText = hashCurrent != null
                                ? ((BlobNode) storageEngine.getObject(hashCurrent)).getTextContent()
                                : "";
                        String targetText = hashTarget != null
                                ? ((BlobNode) storageEngine.getObject(hashTarget)).getTextContent()
                                : "";

                        String markerHeader = "\\ Clean up the conflict markers and this line once resolved";
                        String markerHead = "<<<<<<< CURRENT MOVING BASELINE";
                        String markerDivider = "=======";
                        String markerTail = ">>>>>>> REPLAYING PATCH: " + commitToReplay.getHash().substring(0, 7);

                        StringBuilder conflictMarker = new StringBuilder();
                        conflictMarker.append(markerHeader).append("\n")
                                .append(markerHead).append("\n")
                                .append(currentText)
                                .append(markerDivider).append("\n")
                                .append(targetText)
                                .append(markerTail).append("\n");

                        Path filePath = playgroundPath.resolve(fileName);
                        Files.writeString(filePath, conflictMarker.toString());

                        while (true) {
                            fileSystemIO.openNativeEditor(fileName);
                            List<String> lines = Files.readAllLines(filePath);
                            boolean markersStillExist = false;

                            for (String line : lines) {
                                String trimmed = line.trim();
                                if (trimmed.equals(markerHeader) || trimmed.equals(markerHead) ||
                                        trimmed.equals(markerDivider) || trimmed.equals(markerTail)) {
                                    markersStillExist = true;
                                    break;
                                }
                            }

                            if (markersStillExist) {
                                System.out.println("\n❌ Conflict markers present. Re-opening editor...");
                            } else {
                                System.out.println("✅ Clean resolution confirmed: " + fileName);
                                String resolvedText = Files.readString(filePath);
                                String resolvedHash = HashingUtility.hashString(resolvedText);

                                BlobNode resolvedBlob = new BlobNode(resolvedHash, resolvedText);
                                storageEngine.saveObject(resolvedHash, resolvedBlob);
                                mergedTree.addEntry(fileName, resolvedHash);
                                break;
                            }
                        }
                    }
                }

                StringBuilder treeContentBuilder = new StringBuilder();
                for (Map.Entry<String, String> e : mergedTree.getEntries().entrySet()) {
                    treeContentBuilder.append(e.getKey()).append(":").append(e.getValue()).append(";");
                }
                String mergedTreeHash = HashingUtility.hashString(treeContentBuilder.toString());
                mergedTree.setHash(mergedTreeHash);
                storageEngine.saveObject(mergedTreeHash, mergedTree);

                List<String> rebasedParents = new ArrayList<>();
                rebasedParents.add(currentParentPointer);

                String rebasedMessage = commitToReplay.getMessage() + " (rebased)";
                String identityString = rebasedMessage + mergedTreeHash + System.currentTimeMillis()
                        + rebasedParents.toString();
                String rebasedCommitHash = HashingUtility.hashString(identityString);

                CommitNode rebasedCommitNode = new CommitNode(rebasedCommitHash, rebasedMessage, mergedTreeHash,
                        rebasedParents);
                storageEngine.saveCommit(rebasedCommitHash, rebasedCommitNode);
                storageEngine.getTrieEngine().insert(rebasedCommitHash);

                currentParentPointer = rebasedCommitHash;
            }

            storageEngine.updateBranchPointer(currentBranch, currentParentPointer);
            restoreWorkspaceToCommit(currentParentPointer);
            System.out.println("🎉 Successfully rebased onto branch: " + targetBranch);
        } catch (IOException e) {
            System.out.println("Fatal Error during rebase execution: " + e.getMessage());
        }
    }

    private void handleLog(List<String> tokens) {
        Path playgroundPath = Paths.get("bit-playground");
        if (!Files.exists(playgroundPath)) {
            System.out.println("Error: No repository initialized. Please run 'bit init' first.");
            return;
        }
        storageEngine.printCommitGraphLog();
    }

    private void restoreWorkspaceToCommit(String commitHash) {
        CommitNode targetCommit = storageEngine.getCommit(commitHash);
        String rootTreeHash = targetCommit.getRootTreeHash();
        DirectoryTree rootTree = (DirectoryTree) storageEngine.getObject(rootTreeHash);

        try {
            Path playgroundPath = Paths.get("bit-playground");
            if (Files.exists(playgroundPath)) {
                try (var stream = Files.list(playgroundPath)) {
                    for (Path p : stream.filter(Files::isRegularFile).toList()) {
                        Files.delete(p);
                    }
                }
            }

            Map<String, String> entries = rootTree.getEntries();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                String fileName = e.getKey();
                String blobHash = e.getValue();

                BlobNode blobNode = (BlobNode) storageEngine.getObject(blobHash);
                String previousTextContent = blobNode.getTextContent();

                Path filePath = playgroundPath.resolve(fileName);
                Files.writeString(filePath, previousTextContent);
            }
        } catch (IOException e) {
            System.out.println("Fatal Error reconstructing file snapshots: " + e.getMessage());
        }
    }

    private void printHelpMenu() {
        System.out.println("\nAvailable Commands:");
        System.out.println("  bit init               - Setup a new workspace directory.");
        System.out.println("  bit edit <file>        - Fire external process hook editor.");
        System.out.println("  bit commit -m \"msg\"    - Compute Merkle changes and log commit.");
        System.out.println("  bit branch <name>      - Construct new vertex edge tracking tag.");
        System.out.println("  bit checkout <target>  - Traverse history graph nodes.");
        System.out.println("  bit diff               - Run Myers greedy shortest-path script algorithm.");
        System.out.println("  bit merge <branch>     - Reconcile timelines via 3-way Merkle structures.");
        System.out.println("  bit rebase <branch>    - Replay unique branch patches sequentially.");
        System.out.println("  bit log                - Render the historical commit timeline graph layout.");
    }
}