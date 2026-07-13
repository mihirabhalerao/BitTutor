package model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class DirectoryTree implements MerkleNode {
    // Tree has a hash, a map of entries (files and sub-directories) -> object hash, 
    // and a map of filename -> isDirectory bool.
    private String hash;
    private final Map<String, String> entries; 
    private final Map<String, Boolean> typeMap;

    public DirectoryTree() {
        this.entries = new TreeMap<>();
        this.typeMap = new HashMap<>();
        this.hash = "";
    }

    @Override
    public String getHash() { 
        return this.hash; 
    }

    @Override
    public boolean isDirectory() {
        return true; 
    }

    public void setHash(String hash) { 
        this.hash = hash; 
    }

    public void addEntry(String name, String objectHash, boolean isDir) {
        this.entries.put(name, objectHash);
        this.typeMap.put(name, isDir);
    }

    public Map<String, String> getEntries() { 
        return this.entries; 
    }
    
    public boolean isChildADirectory(String name) { 
        return this.typeMap.getOrDefault(name, false); 
    }
}