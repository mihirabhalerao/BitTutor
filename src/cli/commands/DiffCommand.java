package cli.commands;

import cli.BitCommand;
import engine.*;
import model.BlobNode;
import model.CommitNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class DiffCommand implements BitCommand {
    @Override
    public void execute(List<String> tokens, StorageEngine storageEngine, FileSystemIO fileSystemIO) {
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
                MerkleTreeHelper.flattenTreeRecursively(commitNode.getRootTreeHash(), "", flatSnapshot, storageEngine);

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
                MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(mA.get(0)).getRootTreeHash(), "", flatA, storageEngine);
                MerkleTreeHelper.flattenTreeRecursively(storageEngine.getCommit(mB.get(0)).getRootTreeHash(), "", flatB, storageEngine);

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
}