package engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LRUCache {
    private static class CacheNode {
        String fileKey;
        List<String> diffResult;
        CacheNode next;
        CacheNode prev;

        private CacheNode(String fileKey, List<String> diffResult) {
            this.fileKey = fileKey;
            this.diffResult = diffResult;
            this.next = null;
            this.prev = null;
        }
    }
    
    private CacheNode head;
    private CacheNode tail;
    private Map<String, CacheNode> map;
    private final int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new CacheNode("head", null);
        this.tail = new CacheNode("tail", null);
        this.head.next = tail;
        this.tail.prev = head;
    }

    public List<String> get(String fileKey) {
        if (map.containsKey(fileKey)) {
            CacheNode node = map.get(fileKey);
            removeNode(node);
            addAtHead(node);
            return node.diffResult;
        }
        
        return null; 
    }

    public void put(String fileKey, List<String> diffResult) {
        CacheNode node = map.get(fileKey);
        if (node != null) {
            node.diffResult = diffResult;
            removeNode(node);
            addAtHead(node);
        } else {
            CacheNode newNode = new CacheNode(fileKey, diffResult);
            map.put(fileKey, newNode);
            addAtHead(newNode);

            if (map.size() > capacity) {
                removeNode(tail.prev);
                map.remove(tail.prev.fileKey);
            }
        }
    }

    private void addAtHead(CacheNode node) {
        node.prev = head;
        node.next = head.next;
        node.prev.next = node;
        node.next.prev = node;
    }

    private void removeNode(CacheNode node) {
        if (!map.containsKey(node.fileKey)) {
            return;
        }
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}
