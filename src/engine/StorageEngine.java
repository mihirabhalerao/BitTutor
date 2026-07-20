package engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import model.CommitNode;
import model.DirectoryTree;
import model.MerkleNode;

public class StorageEngine {
    // Contains object map of hash -> MerkleNode, map of hash -> CommitNode,
    // branch pointers map of branchName -> commitHash, TrieEngine, DiffEngine, 
    // LRUCache and headPointer string
    private final HashMap<String, MerkleNode> objectDatabase;
    private final HashMap<String, CommitNode> commitDatabase;
    private final HashMap<String, String> branchPointers;
    private final TrieEngine trieEngine;
    private final LRUCache lruCache;
    private final DiffEngine diffEngine;
    private String headPointer;

    public StorageEngine() {
        // Initialize LRUCache with capacity 5, TrieEngine seeded with default file names and commands.
        this.objectDatabase = new HashMap<>();
        this.commitDatabase = new HashMap<>();
        this.branchPointers = new HashMap<>();
        this.trieEngine = new TrieEngine();
        this.lruCache = new LRUCache(5);
        this.diffEngine = new DiffEngine();
        this.headPointer = "main";

        this.trieEngine.insert("commit");
        this.trieEngine.insert("checkout");
        this.trieEngine.insert("branch");
        this.trieEngine.insert("diff");

        // remove these seeds when commit functionality is ready
        this.trieEngine.insert("welcome.txt");
        this.trieEngine.insert("todo.md");

        // Setup initial default branch pointer state
        this.branchPointers.put("main", null);
        this.trieEngine.insert("main");
    }

    // --- Database Operations API Methods ---

    public void saveObject(String hash, MerkleNode node) {
        objectDatabase.put(hash, node);
    }

    public MerkleNode getObject(String hash) {
        return objectDatabase.get(hash);
    }

    public boolean containsObjectHash(String hash) {
        return objectDatabase.containsKey(hash);
    }

    public void saveCommit(String hash, CommitNode commit) {
        commitDatabase.put(hash, commit);
    }

    public CommitNode getCommit(String hash) {
        return commitDatabase.get(hash);
    }

    public void updateBranchPointer(String branchName, String commitHash) {
        branchPointers.put(branchName, commitHash);
    }

    public String getCommitHashFromBranch(String branchName) {
        return branchPointers.get(branchName);
    }

    public boolean branchExists(String branchName) {
        return branchPointers.containsKey(branchName);
    }

    public String getHeadPointer() {
        return headPointer;
    }

    public void setHeadPointer(String headPointer) {
        this.headPointer = headPointer;
    }

    public TrieEngine getTrieEngine() {
        return this.trieEngine;
    }

    public LRUCache getLRUCache() {
        return this.lruCache;
    }

    public DiffEngine getDiffEngine() {
        return this.diffEngine;
    }

    public String findLowestCommonAncestor(String commitHashA, String commitHashB) {
        Set<String> visitedByA = new HashSet<>();
        Queue<String> q = new LinkedList<>();
        q.offer(commitHashA);

        while (!q.isEmpty()) {
            String cHash = q.poll();
            if (cHash == null)
                continue;
            visitedByA.add(cHash);

            for (String parentHash : commitDatabase.get(cHash).getParentHashes()) {
                q.offer(parentHash);
            }
        }

        if (visitedByA.contains(commitHashB)) {
            return commitHashB;
        }

        q.offer(commitHashB);

        while (!q.isEmpty()) {
            String cHash = q.poll();
            if (cHash == null)
                continue;

            for (String parentHash : commitDatabase.get(cHash).getParentHashes()) {
                if (visitedByA.contains(parentHash)) {
                    return parentHash;
                }
                q.offer(parentHash);
            }
        }

        return null;
    }

    public List<CommitNode> getCommitsToReplay(String branchTipHash, String ancestorHash) {
        List<CommitNode> commitsPath = new ArrayList<>();
        if (!commitDatabase.containsKey(branchTipHash)) {
            System.out.println("Commit with hash: '" + branchTipHash + "' doesn't exist.");
            return commitsPath;
        }

        if (!commitDatabase.containsKey(ancestorHash)) {
            System.out.println("Commit with hash: '" + ancestorHash + "' doesn't exist.");
            return commitsPath;
        }

        String current = branchTipHash;

        while (current != null && !current.equals(ancestorHash)) {
            CommitNode node = getCommit(current);
            if (node == null)
                break;

            commitsPath.add(node);

            // Follow the first parent in case of prior merge nodes
            if (!node.getParentHashes().isEmpty()) {
                current = node.getParentHashes().get(0);
            } else {
                current = null;
            }
        }

        Collections.reverse(commitsPath);
        return commitsPath;
    }

    public void printCommitGraphLog() {
        String activeBranch = getHeadPointer();
        String currentCommitHash = getCommitHashFromBranch(activeBranch);

        if (currentCommitHash == null) {
            System.out.println("Notification: Timeline history is clear. No commits recorded yet.");
            return;
        }

        // 1. Collect and Sort Commits Topologically using DFS (Child before Parent)
        List<model.CommitNode> sortedCommits = new java.util.ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        buildTopologicalOrder(currentCommitHash, visited, sortedCommits);
        java.util.Collections.reverse(sortedCommits);

        // 2. Iterate and print commits in a clean linear text format
        for (model.CommitNode commit : sortedCommits) {
            String cHash = commit.getHash();

            StringBuilder branchLabel = new StringBuilder();
            java.util.List<String> pointingBranches = new java.util.ArrayList<>();
            for (Map.Entry<String, String> e : branchPointers.entrySet()) {
                if (e.getValue().equals(cHash)) pointingBranches.add(e.getKey());
            }

            if (!pointingBranches.isEmpty()) {
                branchLabel.append(" \u001B[33m(");
                for (int b = 0; b < pointingBranches.size(); b++) {
                    String br = pointingBranches.get(b);
                    if (br.equals(activeBranch)) {
                        branchLabel.append("\u001B[31mHEAD -> ").append(br).append("\u001B[33m");
                    } else {
                        branchLabel.append(br);
                    }
                    if (b < pointingBranches.size() - 1)
                        branchLabel.append(", ");
                }
                branchLabel.append(")\u001B[0m");
            }

            // --- FORMAT CRYPTOGRAPHIC EPOCH TIMESTAMPS ---
            java.util.Date commitDate = new java.util.Date(commit.getTimestamp());
            // Matches typical layout configuration formatting styles: EEE MMM dd HH:mm:ss
            // yyyy Z
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z");
            String formattedDate = formatter.format(commitDate);

            // --- PRINT THE LINEAR SUMMARY BLOCK ---
            System.out.println("\u001B[33mcommit " + cHash + "\u001B[0m" + branchLabel.toString());
            System.out.println("Date:   " + formattedDate);
            System.out.println();
            System.out.println("    " + commit.getMessage());
            System.out.println();
        }
    }

    /**
     * Helper method executing standard Post-Order DFS to build clean Topological
     * sequencing paths.
     */
    private void buildTopologicalOrder(String currentHash, java.util.Set<String> visited,
            List<model.CommitNode> order) {
        if (currentHash == null || !visited.add(currentHash))
            return;

        model.CommitNode node = getCommit(currentHash);
        if (node != null) {
            for (String parent : node.getParentHashes()) {
                buildTopologicalOrder(parent, visited, order);
            }
            order.add(node);
        }
    }

    public boolean isWorkspaceDirty() {
        String activeBranch = getHeadPointer();
        String lastCommitHash = getCommitHashFromBranch(activeBranch);
        
        if (lastCommitHash == null) {
            return true;
        }


        CommitNode lastCommitNode = commitDatabase.get(lastCommitHash);
        Path playgroundPath = Paths.get("bit-playground");

        String rootTreeHash = lastCommitNode.getRootTreeHash();

        if (isWorkspaceDirty(playgroundPath, rootTreeHash))
            return true;

        return false;
    }

    private boolean isWorkspaceDirty(Path currDirectoryPath, String directoryHash) {
        DirectoryTree directoryTree = (DirectoryTree) getObject(directoryHash);
        Map<String, String> entries = directoryTree.getEntries();

        try (Stream<Path> stream = Files.list(currDirectoryPath)) {
            List<Path> children = stream.toList();
            if (entries.size() != children.size()) return true;

            for (Path child : children) {
                String name = child.getFileName().toString();

                if (!entries.containsKey(name)) return true;

                if (directoryTree.isChildADirectory(name)) {
                    if (isWorkspaceDirty(child, entries.get(name))) 
                        return true;
                } 
                
                else {
                    String historicalHash = entries.get(name);
                    if (historicalHash == null) return true;

                    String currHash = HashingUtility.hashString(Files.readString(child));
                    if (!Objects.equals(currHash, historicalHash))
                        return true;
                }
            }

        } catch (IOException e) {
            return true;
        }

        return false;
    }

    public void printStorageMetrics() {
        System.out.println("--- Bit Storage Engine Metrics ---");
        System.out.println("Total Tracked Objects (Blobs/Trees): " + objectDatabase.size());
        System.out.println("Total Active Commits in Graph:      " + commitDatabase.size());
        System.out.println("Active Branch Pointers:            " + branchPointers.keySet());
        System.out.println("Current HEAD Context Location:      " + headPointer);
        System.out.println("----------------------------------");
    }
}
