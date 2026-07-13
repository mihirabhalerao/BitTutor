package model;

// Implements MerkleNode structure to store file details i.e. it's hash and textContent
public class BlobNode implements MerkleNode {
    private final String hash;
    private final String textContent;

    public BlobNode(String hash, String textConent) {
        this.hash = hash;
        this.textContent = textConent;
    }

    @Override
    public String getHash() {
        return this.hash;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    public String getTextContent() {
        return this.textContent;
    }
}
