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

public class RebaseCommand implements BitCommand{
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
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
            MerkleTreeHelper.restoreWorkspaceToCommit(tarHash, storageEngine);
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
                MerkleTreeHelper.restoreWorkspaceToCommit(movingBasePointer, storageEngine);

                Map<String, String> flatBase = new HashMap<>(), flatPatch = new HashMap<>(), flatPatchParent = new HashMap<>();
                MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(movingBasePointer).getRootTreeHash(), "", flatBase, storageEngine);
                MerkleTreeHelper.flattenTreeRecursively(patchNode.getRootTreeHash(), "", flatPatch, storageEngine);
                MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(patchNode.getParentHashes().get(0)).getRootTreeHash(), "", flatPatchParent, storageEngine);

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
                String newRoot = MerkleTreeHelper.buildMerkleTreeRecursively(playgroundPath, storageEngine);
                String newMsg = patchNode.getMessage() + " (rebased)";
                
                CommitNode rebasedNode = new CommitNode(HashingUtility.hashString(newMsg + newRoot + System.currentTimeMillis()), newMsg, newRoot, List.of(movingBasePointer));
                storageEngine.saveCommit(rebasedNode.getHash(), rebasedNode);
                movingBasePointer = rebasedNode.getHash();
            }

            storageEngine.updateBranchPointer(currBranch, movingBasePointer);
            MerkleTreeHelper.restoreWorkspaceToCommit(movingBasePointer, storageEngine);
            System.out.println("🎉 Rebase complete! Tip moved to: " + movingBasePointer.substring(0,7));

        } catch (IOException e) { System.out.println("Rebase failed: " + e.getMessage()); }
    }
}
