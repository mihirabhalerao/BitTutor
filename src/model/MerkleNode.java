package model;

// Interface for a MerkleNode, implemented by BlobNode (for files) and DirectoryTree (for directories)
public interface MerkleNode {
    String getHash();
    boolean isDirectory();
}