package engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LRUCache {
    private static class CacheNode {
        String combinedKey;
        List<String> diffResult;
        CacheNode next;
        CacheNode prev;

        private CacheNode(String combinedKey, List<String> diffResult) {
            this.combinedKey = combinedKey;
            this.diffResult = diffResult;
        }
    }
    
    private final CacheNode head;
    private final CacheNode tail;
    private final Map<String, CacheNode> map;
    private final int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new CacheNode("head", null);
        this.tail = new CacheNode("tail", null);
        this.head.next = tail;
        this.tail.prev = head;
    }

    /**
     * Generates a unique, order-independent composite token signature.
     * Ensures diff(A, B) and diff(B, A) hit the exact same cache bucket.
     */
    private String makeCombinedKey(String fileAKey, String fileBKey) {
        if (fileAKey == null) fileAKey = "";
        if (fileBKey == null) fileBKey = "";
        return fileAKey.compareTo(fileBKey) <= 0 ? 
               fileAKey + ":::" + fileBKey : 
               fileBKey + ":::" + fileAKey;
    }

    public boolean diffCacheExists(String fileAKey, String fileBKey) {
        String key = makeCombinedKey(fileAKey, fileBKey);
        if (!map.containsKey(key)) return false;
        return true;
    }

    public List<String> get(String fileAKey, String fileBKey) {
        String key = makeCombinedKey(fileAKey, fileBKey);
        CacheNode node = map.get(key);
        
        if (node == null) {
            return null;
        }
        
        // Refresh the node location to the head (Most Recently Used)
        removeNode(node);
        addAtHead(node);
        return node.diffResult;
    }

    public void put(String fileAKey, String fileBKey, List<String> diffResult) {
        String key = makeCombinedKey(fileAKey, fileBKey);
        CacheNode existingNode = map.get(key);

        if (existingNode != null) {
            // Update the results and refresh priority position
            existingNode.diffResult = diffResult;
            removeNode(existingNode);
            addAtHead(existingNode);
        } else {
            // Construct a brand new tracking node entry
            CacheNode newNode = new CacheNode(key, diffResult);
            map.put(key, newNode);
            addAtHead(newNode);

            // Check if capacity bounds have been breached
            if (map.size() > capacity) {
                CacheNode lruNode = tail.prev; // Identify the true oldest element
                removeNode(lruNode);          // Disconnect from structural links
                map.remove(lruNode.combinedKey); // Drop cleanly from hash tracking map
            }
        }
    }

    private void addAtHead(CacheNode node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(CacheNode node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}