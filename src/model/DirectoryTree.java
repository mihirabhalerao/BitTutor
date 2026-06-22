package model;

import java.util.HashMap;
import java.util.Map;

public class DirectoryTree implements MerkleNode {
    private String hash;
    private final Map<String, String> entries; // Entry Name -> Object Hash Reference
    private final Map<String, Boolean> typeMap; // Entry Name -> IsDirectory Flag Toggle

    public DirectoryTree() {
        this.entries = new HashMap<>();
        this.typeMap = new HashMap<>();
        this.hash = "";
    }

    @Override
    public String getHash() { return this.hash; }

    @Override
    public boolean isDirectory() { return true; }

    public void setHash(String hash) { this.hash = hash; }

    public void addEntry(String name, String objectHash, boolean isDir) {
        this.entries.put(name, objectHash);
        this.typeMap.put(name, isDir);
    }

    public Map<String, String> getEntries() { return this.entries; }
    public boolean isChildDirectory(String name) { return this.typeMap.getOrDefault(name, false); }
}