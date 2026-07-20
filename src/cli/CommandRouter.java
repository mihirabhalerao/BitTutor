package cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    // Has it's own storage engine and IO file system.
    private final StorageEngine storageEngine;
    private final FileSystemIO fileSystemIO;

    public CommandRouter() {
        this.storageEngine = new StorageEngine();
        this.fileSystemIO = new FileSystemIO(); 
    }

    // Tokenizes the input using CommandParser class
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

        boolean forceCreate = tokens.get(2).equals("-n");
        Path playgroundPath = Paths.get("bit-playground");

        if (forceCreate && tokens.size() < 4) System.out.println("Error: Missing filename after '-n'. Example: bit edit -n src/app.js");
        // if (!forceCreate && !Files.exists(playgroundPath.resolve(tokens.get(2)))) {
        //     System.out.println("Error: Incorrect syntax to create a new file. Example: bit edit -n src/app.js");
        // }
            

        // Force all OS paths to standard Merkle forward-slashes
        String targetPathString = (forceCreate ? tokens.get(3) : tokens.get(2)).replace("\\", "/");
        
        Path targetPath = playgroundPath.resolve(targetPathString);

        if (!Files.exists(playgroundPath)) {
            System.out.println("No root directory initialized. Please run 'bit init' first.");
            return;
        }

        // If file exists and no forced create option mentioned, open native editor
        List<String> matches = storageEngine.getTrieEngine().searchPrefix(targetPathString);
        if (!matches.isEmpty() && !forceCreate) {
                fileSystemIO.openNativeEditor(matches.get(0));
            return;
        }

        // If file doesn't exist and no forced create option mentioned, ask if user wants to create a new file
        if (!forceCreate) {
            System.out.print("Target path '" + targetPathString + "' is untracked. Create new entry? (y/n): ");
            try {
                char choice = (char) System.in.read();
                while (System.in.available() > 0) System.in.read(); 
                if (choice != 'y' && choice != 'Y') {
                    System.out.println("Aborted.");
                    return;
                }
            } catch (IOException e) { return; }
        }

        // Create new file, and open editor
        try {
            // If no actual file in path, just directories -> create directories
            if (!targetPathString.contains(".")) {
                Files.createDirectories(targetPath);
                System.out.println("Created directory: " + targetPathString);
            } else {
                // Create non-existent parent directories and the new file
                if (targetPath.getParent() != null) Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, "");
                
                // Insert file name in trie and open native editor
                storageEngine.getTrieEngine().insert(targetPathString);
                fileSystemIO.openNativeEditor(targetPathString);
            }
        } catch (IOException e) {
            System.out.println("FileSystem Error: " + e.getMessage());
        }
    }
    
    private void handleCommit(List<String> tokens) {
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
            String treeRootHash = buildMerkleTreeRecursively(playgroundPath);

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

    private String buildMerkleTreeRecursively(Path currentPath) throws IOException {
        DirectoryTree currentDirTree = new DirectoryTree();
        StringBuilder treeSignatureBuilder = new StringBuilder();

        try (Stream<Path> stream = Files.list(currentPath)) {
            List<Path> children = stream
                    //.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();

            for (Path child : children) {
                String name = child.getFileName().toString();

                if (Files.isDirectory(child)) {
                    // Recursive step down into sub-directories
                    String subTreeHash = buildMerkleTreeRecursively(child);
                    currentDirTree.addEntry(name, subTreeHash, true);
                    treeSignatureBuilder.append("tree:").append(name).append(":").append(subTreeHash).append(";");
                } else {
                    //Ingest files into standard content blobs
                    String content = Files.readString(child);
                    String blobHash = HashingUtility.hashString(content);

                    if (!storageEngine.containsObjectHash(blobHash)) {
                        storageEngine.saveObject(blobHash, new BlobNode(blobHash, content));
                    }
                    currentDirTree.addEntry(name, blobHash, false);
                    treeSignatureBuilder.append("blob:").append(name).append(":").append(blobHash).append(";");
                }
            }
        }

        String calculatedDirHash = HashingUtility.hashString(treeSignatureBuilder.toString());
        currentDirTree.setHash(calculatedDirHash);
        storageEngine.saveObject(calculatedDirHash, currentDirTree);

        return calculatedDirHash;
    }

    private void handleBranch(List<String> tokens) {
        if (tokens.size() == 2) {
            System.out.println("Current branch name: " + storageEngine.getHeadPointer());
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
        restoreWorkspaceToCommit(targetCommitHash);
    }

    private void handleDiff(List<String> tokens) {
        Path playgroundPath = Paths.get("bit-playground");
        if (!Files.exists(playgroundPath)) {
            System.out.println("No repository initialized.");
            return;
        }

        if (tokens.size() != 2 && tokens.size() != 4) {
            System.out.println("Syntax Error. Use 'bit diff' or 'bit diff <hashA> <hashB>'");
            return;
        }

        try {
            if (tokens.size() == 2) {
                String activeBranch = storageEngine.getHeadPointer();
                String commitHash = storageEngine.getCommitHashFromBranch(activeBranch);
                if (commitHash == null) {
                    System.out.println("No commits exist yet.");
                    return;
                }

                CommitNode commitNode = storageEngine.getCommit(commitHash);
                Map<String, String> flatSnapshot = new HashMap<>();
                flattenTreeRecursively(commitNode.getRootTreeHash(), "", flatSnapshot);

                // FIXED BLINDSPOT: Capture both tracked snapshot keys AND live disk files
                Set<String> allPaths = new HashSet<>(flatSnapshot.keySet());
                try (Stream<Path> walk = Files.walk(playgroundPath)) {
                    walk.filter(Files::isRegularFile).forEach(p -> 
                        allPaths.add(playgroundPath.relativize(p).toString().replace("\\", "/"))
                    );
                }

                for (String pathKey : allPaths) {
                    String blobHash = flatSnapshot.get(pathKey);
                    Path livePath = playgroundPath.resolve(pathKey);

                    List<String> historyLines = blobHash != null ? 
                        ((BlobNode) storageEngine.getObject(blobHash)).getTextContent().lines().toList() : new ArrayList<>();
                    
                    List<String> liveLines = Files.exists(livePath) ? 
                        Files.readString(livePath).lines().toList() : new ArrayList<>();

                    if (historyLines.equals(liveLines)) continue; // Skip unchanged files

                    System.out.println("\nDiff for: " + pathKey);
                    System.out.println("----------------------------------------");
                    List<String> report = storageEngine.getDiffEngine().computeDiff(historyLines, liveLines);
                    for (String line : report) System.out.println(line);
                }
            } else {
                // Explicit 2-Commit Diff
                String hashA = tokens.get(2);
                String hashB = tokens.get(3);

                List<String> mA = storageEngine.getTrieEngine().searchPrefix(hashA);
                List<String> mB = storageEngine.getTrieEngine().searchPrefix(hashB);
                
                if (mA.isEmpty() || mB.isEmpty()) {
                    System.out.println("Invalid commit hashes.");
                    return;
                }

                Map<String, String> flatA = new HashMap<>(), flatB = new HashMap<>();
                flattenTreeRecursively(storageEngine.getCommit(mA.get(0)).getRootTreeHash(), "", flatA);
                flattenTreeRecursively(storageEngine.getCommit(mB.get(0)).getRootTreeHash(), "", flatB);

                Set<String> keys = new HashSet<>(flatA.keySet());
                keys.addAll(flatB.keySet());

                for (String file : keys) {
                    BlobNode blobA = flatA.containsKey(file) ? (BlobNode) storageEngine.getObject(flatA.get(file)) : null;
                    BlobNode blobB = flatB.containsKey(file) ? (BlobNode) storageEngine.getObject(flatB.get(file)) : null;

                    List<String> lA = (blobA != null) ? blobA.getTextContent().lines().toList() : new ArrayList<>();
                    List<String> lB = (blobB != null) ? blobB.getTextContent().lines().toList() : new ArrayList<>();
                    
                    if (lA.equals(lB)) continue;

                    String fileHashA = (blobA == null) ? "EMPTY_FILE_INITIAL" : blobA.getHash();
                    String fileHashB = (blobB == null) ? "EMPTY_FILE_FINAL" : blobB.getHash();

                    System.out.println("\nDiff for: " + file);

                    // Check if diff result exists in cache
                    List<String> cachedDiff = storageEngine.getLRUCache().get(fileHashA, fileHashB);

                    if (cachedDiff != null) {
                        System.out.println("Cache hit, diff already exists: ");
                        for (String line : cachedDiff) System.out.println(line);
                    } else {
                        List<String> diffResult = storageEngine.getDiffEngine().computeDiff(lA, lB);
                        for (String line : diffResult) System.out.println(line);
                        
                        // Populate the LRU cache with new diff
                        storageEngine.getLRUCache().put(fileHashA, fileHashB, diffResult);
                    }
                }
            }
        } catch (IOException e) { System.out.println("Diff Error: " + e.getMessage()); }
    }

    private void handleMerge(List<String> tokens) {
        if (tokens.size() < 3) {
            System.out.println("Syntax: bit merge <branch>");
            return;
        }
        String targetBranch = tokens.get(2);
        if (!storageEngine.branchExists(targetBranch)) {
            System.out.println("Branch '" + targetBranch + "' does not exist.");
            return;
        }

        String activeBranch = storageEngine.getHeadPointer();
        String currHash = storageEngine.getCommitHashFromBranch(activeBranch);
        String tarHash = storageEngine.getCommitHashFromBranch(targetBranch);

        if (currHash.equals(tarHash)) { System.out.println("Already up to date."); return; }

        String lcaHash = storageEngine.findLowestCommonAncestor(tarHash, currHash);
        if (lcaHash.equals(tarHash)) { System.out.println("Already up to date."); return; }
        
        if (lcaHash.equals(currHash)) {
            System.out.println("Fast-forwarding to " + targetBranch);
            storageEngine.updateBranchPointer(activeBranch, tarHash);
            restoreWorkspaceToCommit(tarHash);
            return;
        }

        System.out.println("Executing 3-Way Merkle Reconciliation...");
        Path playgroundPath = Paths.get("bit-playground");

        try {
            // Flatten all three hierarchical snapshots to safely compare blob data
            Map<String, String> flatCurr = new HashMap<>(), flatTar = new HashMap<>(), flatLca = new HashMap<>();
            flattenTreeRecursively(storageEngine.getCommit(currHash).getRootTreeHash(), "", flatCurr);
            flattenTreeRecursively(storageEngine.getCommit(tarHash).getRootTreeHash(), "", flatTar);
            flattenTreeRecursively(storageEngine.getCommit(lcaHash).getRootTreeHash(), "", flatLca);

            Set<String> allFiles = new HashSet<>(flatCurr.keySet());
            allFiles.addAll(flatTar.keySet());
            allFiles.addAll(flatLca.keySet());

            for (String relativePath : allFiles) {
                String hCurr = flatCurr.get(relativePath);
                String hTar = flatTar.get(relativePath);
                String hLca = flatLca.get(relativePath);
                Path filePath = playgroundPath.resolve(relativePath);

                if (Objects.equals(hCurr, hTar)) continue; // Both agreed

                if (Objects.equals(hCurr, hLca)) {
                    // Current didn't touch it, Target did. Take Target's version.
                    if (hTar != null) {
                        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, ((BlobNode) storageEngine.getObject(hTar)).getTextContent());
                    } else {
                        Files.deleteIfExists(filePath);
                    }
                } else if (!Objects.equals(hLca, hTar)) {
                    // BOTH touched it differently -> CONFLICT
                    System.out.println("🚨 Merge Conflict in: " + relativePath);
                    String txtCurr = hCurr != null ? ((BlobNode) storageEngine.getObject(hCurr)).getTextContent() : "";
                    String txtTar = hTar != null ? ((BlobNode) storageEngine.getObject(hTar)).getTextContent() : "";

                    String conflictMarker = "<<<<<<< HEAD\n" + txtCurr + "\n\n=======\n" + txtTar + "\n>>>>>>> " + targetBranch + "\n";
                    if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
                    Files.writeString(filePath, conflictMarker);

                    while (true) {
                        fileSystemIO.openNativeEditor(relativePath);
                        String resolved = Files.readString(filePath);
                        if (resolved.contains("<<<<<<< HEAD") || resolved.contains("=======") || resolved.contains(">>>>>>> " + targetBranch)) {
                            System.out.println("Markers still detected. Re-opening editor...");
                        } else {
                            System.out.println("Resolved: " + relativePath);
                            break;
                        }
                    }
                }
            }

            // SNAP THE NEW HIERARCHICAL MERKLE ROOT FROM THE RESOLVED DISK
            String newRootHash = buildMerkleTreeRecursively(playgroundPath);
            List<String> parents = List.of(currHash, tarHash);
            String msg = "Merge branch '" + targetBranch + "' into " + activeBranch;

            CommitNode mergeCommit = new CommitNode(HashingUtility.hashString(msg + newRootHash + System.currentTimeMillis() + parents), msg, newRootHash, parents);
            storageEngine.saveCommit(mergeCommit.getHash(), mergeCommit);
            storageEngine.updateBranchPointer(activeBranch, mergeCommit.getHash());
            storageEngine.getTrieEngine().insert(mergeCommit.getHash());

            System.out.println("Merge successful. New tip: " + mergeCommit.getHash().substring(0,7));
        } catch (IOException e) { System.out.println("Merge crash: " + e.getMessage()); }
    }

    private void handleRebase(List<String> tokens) {
        if (tokens.size() < 3) { System.out.println("Syntax: bit rebase <branch>"); return; }
        String targetBranch = tokens.get(2);
        if (!storageEngine.branchExists(targetBranch)) { System.out.println("Branch not found."); return; }

        String currBranch = storageEngine.getHeadPointer();
        String currHash = storageEngine.getCommitHashFromBranch(currBranch);
        String tarHash = storageEngine.getCommitHashFromBranch(targetBranch);

        String lcaHash = storageEngine.findLowestCommonAncestor(currHash, tarHash);
        if (lcaHash.equals(currHash)) {
            System.out.println("Fast-forwarding to " + targetBranch);
            storageEngine.updateBranchPointer(currBranch, tarHash);
            restoreWorkspaceToCommit(tarHash);
            return;
        }

        else if (lcaHash.equals(tarHash)) {
            System.out.println("Current branch is already up to date with '" + targetBranch + "'.");
            return;
        }

        List<CommitNode> patches = storageEngine.getCommitsToReplay(currHash, lcaHash);
        String movingBasePointer = tarHash;
        Path playgroundPath = Paths.get("bit-playground");

        try {
            for (CommitNode patchNode : patches) {
                // 1. Check out the baseline to disk first
                restoreWorkspaceToCommit(movingBasePointer);

                Map<String, String> flatBase = new HashMap<>(), flatPatch = new HashMap<>(), flatPatchParent = new HashMap<>();
                flattenTreeRecursively(storageEngine.getCommit(movingBasePointer).getRootTreeHash(), "", flatBase);
                flattenTreeRecursively(patchNode.getRootTreeHash(), "", flatPatch);
                flattenTreeRecursively(storageEngine.getCommit(patchNode.getParentHashes().get(0)).getRootTreeHash(), "", flatPatchParent);

                Set<String> allFiles = new HashSet<>(flatBase.keySet());
                allFiles.addAll(flatPatch.keySet());
                allFiles.addAll(flatPatchParent.keySet());

                for (String file : allFiles) {
                    String hBase = flatBase.get(file), hPatch = flatPatch.get(file), hParent = flatPatchParent.get(file);
                    Path filePath = playgroundPath.resolve(file);

                    if (Objects.equals(hBase, hPatch)) continue;

                    if (Objects.equals(hBase, hParent)) {
                        if (hPatch != null) {
                            if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
                            Files.writeString(filePath, ((BlobNode) storageEngine.getObject(hPatch)).getTextContent());
                        } else Files.deleteIfExists(filePath);
                    } else if (!Objects.equals(hBase, hPatch)) {
                        System.out.println("🚨 Rebase Conflict in: " + file);
                        String tBase = hBase != null ? ((BlobNode)storageEngine.getObject(hBase)).getTextContent() : "";
                        String tPatch = hPatch != null ? ((BlobNode)storageEngine.getObject(hPatch)).getTextContent() : "";

                        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, "<<<<<<< BASELINE\n" + tBase + "\n\n=======\n" + tPatch + "\n>>>>>>> PATCH\n");

                        while (true) {
                            fileSystemIO.openNativeEditor(file);
                            if (!Files.readString(filePath).contains("<<<<<<< BASELINE")) break;
                            System.out.println("Please resolve conflict markers.");
                        }
                    }
                }

                // 2. RE-SNAP TRUE NESTED MERKLE ROOT FROM THE DISK
                String newRoot = buildMerkleTreeRecursively(playgroundPath);
                String newMsg = patchNode.getMessage() + " (rebased)";
                
                CommitNode rebasedNode = new CommitNode(HashingUtility.hashString(newMsg + newRoot + System.currentTimeMillis()), newMsg, newRoot, List.of(movingBasePointer));
                storageEngine.saveCommit(rebasedNode.getHash(), rebasedNode);
                movingBasePointer = rebasedNode.getHash();
            }

            storageEngine.updateBranchPointer(currBranch, movingBasePointer);
            restoreWorkspaceToCommit(movingBasePointer);
            System.out.println("🎉 Rebase complete! Tip moved to: " + movingBasePointer.substring(0,7));

        } catch (IOException e) { System.out.println("Rebase failed: " + e.getMessage()); }
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
        System.out.println("Restoring physical files inside './bit-playground/'...");

        try {
            Path playgroundPath = Paths.get("bit-playground");
            List<Path> elements = new ArrayList<>();
            // Clear out the existing directory structure
            if (Files.exists(playgroundPath)) {
                try (Stream<Path> walk = Files.walk(playgroundPath)) {
                    elements = walk.sorted(Comparator.reverseOrder()).toList();
                }

                try {
                    for (Path p : elements) {
                        if (!p.equals(playgroundPath)) {
                            System.out.println("Deleting: " + p);
                            Thread.sleep(500);
                            // if (Files.isDirectory(p)) {
                            //     try (Stream<Path> s = Files.list(p)) {
                            //         System.out.println(p + " contains:");
                            //         s.forEach(System.out::println);
                            //     }
                            // }
                            System.out.println("Exists before delete: " + Files.exists(p));

                            boolean deleted = p.toFile().delete();

                            System.out.println("Deleted? " + deleted);

                            if (!deleted) {
                                System.out.println("Absolute path: " + p.toAbsolutePath());
                            }

                            // Files.delete(p);
                            System.out.println("Deleted : " + p);
                        }
                    }
                }

                catch (Exception e) {
                    System.out.println("Error: ");
                    e.printStackTrace();
                }
            } else {
                Files.createDirectories(playgroundPath);
            }

            // Reconstruct workspace from hierarchical records
            unpackMerkleTreeRecursively(targetCommit.getRootTreeHash(), playgroundPath);
            System.out.println("Successfully changed working directory timeline layout state!");
        } catch (IOException e) {
            System.out.println("Fatal Error reconstructing hierarchical file snapshots: " + e.getMessage());
            System.out.println(e.getClass().getName());
            e.printStackTrace();
        }
    }

    private void unpackMerkleTreeRecursively(String treeHash, Path targetDirectoryPath) throws IOException {
        DirectoryTree currentTree = (DirectoryTree) storageEngine.getObject(treeHash);
        if (currentTree == null)
            return;

        for (Map.Entry<String, String> entry : currentTree.getEntries().entrySet()) {
            String name = entry.getKey();
            String nodeHash = entry.getValue();
            Path childPath = targetDirectoryPath.resolve(name);

            if (currentTree.isChildADirectory(name)) {
                Files.createDirectories(childPath);
                unpackMerkleTreeRecursively(nodeHash, childPath); // Step down into nested structures
            } else {
                BlobNode blob = (BlobNode) storageEngine.getObject(nodeHash);
                Files.writeString(childPath, blob.getTextContent());
            }
        }
    }

    private void flattenTreeRecursively(String treeHash, String prefixPath, Map<String, String> flatMap) {
        DirectoryTree currentTree = (DirectoryTree) storageEngine.getObject(treeHash);
        if (currentTree == null) return;

        for (Map.Entry<String, String> entry : currentTree.getEntries().entrySet()) {
            String name = entry.getKey();
            String currentHash = entry.getValue();
            String fullRelativePath = prefixPath.isEmpty() ? name : prefixPath + "/" + name;

            if (currentTree.isChildADirectory(name)) {
                flattenTreeRecursively(currentHash, fullRelativePath, flatMap);
            } else {
                flatMap.put(fullRelativePath, currentHash);
            }
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
    }
}