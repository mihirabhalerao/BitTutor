package cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cli.BitCommand;
import engine.FileSystemIO;
import engine.HashingUtility;
import engine.MerkleTreeHelper;
import engine.StorageEngine;
import model.BlobNode;
import model.CommitNode;

public class MergeCommand implements BitCommand {
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
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
            MerkleTreeHelper.restoreWorkspaceToCommit(tarHash, storageEngine);
            return;
        }

        System.out.println("Executing 3-Way Merkle Reconciliation...");
        Path playgroundPath = Paths.get("bit-playground");

        try {
            // Flatten all three hierarchical snapshots to safely compare blob data
            Map<String, String> flatCurr = new HashMap<>(), flatTar = new HashMap<>(), flatLca = new HashMap<>();
            MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(currHash).getRootTreeHash(), "", flatCurr, storageEngine);
            MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(tarHash).getRootTreeHash(), "", flatTar, storageEngine);
            MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(lcaHash).getRootTreeHash(), "", flatLca, storageEngine);

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
            String newRootHash = MerkleTreeHelper.buildMerkleTreeRecursively(playgroundPath, storageEngine);
            List<String> parents = List.of(currHash, tarHash);
            String msg = "Merge branch '" + targetBranch + "' into " + activeBranch;

            CommitNode mergeCommit = new CommitNode(HashingUtility.hashString(msg + newRootHash + System.currentTimeMillis() + parents), msg, newRootHash, parents);
            storageEngine.saveCommit(mergeCommit.getHash(), mergeCommit);
            storageEngine.updateBranchPointer(activeBranch, mergeCommit.getHash());
            storageEngine.getTrieEngine().insert(mergeCommit.getHash());

            System.out.println("Merge successful. New tip: " + mergeCommit.getHash().substring(0,7));
        } catch (IOException e) { System.out.println("Merge crash: " + e.getMessage()); }
    }
}
