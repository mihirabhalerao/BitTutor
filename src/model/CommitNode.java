package model;

import java.util.ArrayList;
import java.util.List;

public class CommitNode {
    // Stores a commit's hash, message, timestamp, list of parent hashes and
    // the hash of the root tree it belongs to.
    private final String hash;
    private final String message;
    private final long timestamp;
    private final List<String> parentHashes;
    private final String rootTreeHash;

    public CommitNode(String hash, String message, String rootTreeHash, List<String> parentHashes) {
        this.hash = hash;
        this.message = message;
        this.rootTreeHash = rootTreeHash;
        this.parentHashes = parentHashes != null ? parentHashes : new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    public String getHash() {
        return hash;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<String> getParentHashes() {
        return parentHashes;
    }

    public String getRootTreeHash() {
        return rootTreeHash;
    }
}
