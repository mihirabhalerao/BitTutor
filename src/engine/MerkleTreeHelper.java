package engine;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import model.*;

public class MerkleTreeHelper {

    public static String buildMerkleTreeRecursively(Path currentPath, StorageEngine storageEngine) throws IOException {
        DirectoryTree currentDirTree = new DirectoryTree();
        StringBuilder treeSignatureBuilder = new StringBuilder();

        try (Stream<Path> stream = Files.list(currentPath)) {
            List<Path> children = stream.toList();

            for (Path child : children) {
                String name = child.getFileName().toString();

                if (Files.isDirectory(child)) {
                    // Recursive step down into sub-directories
                    String subTreeHash = buildMerkleTreeRecursively(child, storageEngine);
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

    public static void flattenTreeRecursively(String treeHash, String prefixPath, Map<String, String> flatMap, StorageEngine storageEngine) {
        DirectoryTree currentTree = (DirectoryTree) storageEngine.getObject(treeHash);
        if (currentTree == null) return;

        for (Map.Entry<String, String> entry : currentTree.getEntries().entrySet()) {
            String name = entry.getKey();
            String currentHash = entry.getValue();
            String fullRelativePath = prefixPath.isEmpty() ? name : prefixPath + "/" + name;

            if (currentTree.isChildADirectory(name)) {
                flattenTreeRecursively(currentHash, fullRelativePath, flatMap, storageEngine);
            } else {
                flatMap.put(fullRelativePath, currentHash);
            }
        }
    }

    public static void unpackMerkleTreeRecursively(String treeHash, Path targetDirectoryPath, StorageEngine storageEngine) throws IOException {
        DirectoryTree currentTree = (DirectoryTree) storageEngine.getObject(treeHash);
        if (currentTree == null) return;

        for (Map.Entry<String, String> entry : currentTree.getEntries().entrySet()) {
            String name = entry.getKey();
            String nodeHash = entry.getValue();
            Path childPath = targetDirectoryPath.resolve(name);

            if (currentTree.isChildADirectory(name)) {
                Files.createDirectories(childPath);
                unpackMerkleTreeRecursively(nodeHash, childPath, storageEngine); // Step down into nested structures
            } else {
                BlobNode blob = (BlobNode) storageEngine.getObject(nodeHash);
                Files.writeString(childPath, blob.getTextContent());
            }
        }
    }

    public static void restoreWorkspaceToCommit(String commitHash, StorageEngine storageEngine) {
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
                            p.toFile().delete();
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
            MerkleTreeHelper.unpackMerkleTreeRecursively(targetCommit.getRootTreeHash(), playgroundPath, storageEngine);
            System.out.println("Successfully changed working directory timeline layout state!");
        } catch (IOException e) {
            System.out.println("Fatal Error reconstructing hierarchical file snapshots: " + e.getMessage());
            System.out.println(e.getClass().getName());
            e.printStackTrace();
        }
    }
}