package engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrieEngine {
    private static class TrieNode {
        private final Map<String, TrieNode> children;
        private String completeWord;

        public TrieNode() {
            this.children = new HashMap<>();
            this.completeWord = null;
        }
    }

    private final TrieNode root;

    public TrieEngine() {
        this.root = new TrieNode();
    }

    public void insert(String word) {
        TrieNode ptr = root;
        String remainingWord = word;

        while (!remainingWord.isEmpty()) {
            boolean matchedEdge = false;

            List<String> currentEdges = new ArrayList<>(ptr.children.keySet());
            for (String edge : currentEdges) {
                int commonLength = getCommonPrefixLength(edge, remainingWord);

                if (commonLength > 0) {
                    matchedEdge = true;
                    TrieNode child = ptr.children.remove(edge);

                    if (commonLength == edge.length()) {
                        ptr.children.put(edge, child);
                        ptr = child;
                        remainingWord = remainingWord.substring(commonLength);
                        break;
                    }

                    String commonPrefix = edge.substring(0, commonLength);
                    String remainingEdge = edge.substring(commonLength);

                    TrieNode splitNode = new TrieNode();
                    ptr.children.put(commonPrefix, splitNode);
                    splitNode.children.put(remainingEdge, child);

                    String remaining = remainingWord.substring(commonLength);
                    if (remaining.isEmpty()) {
                        splitNode.completeWord = word;
                        return;
                    } else {
                        TrieNode leafNode = new TrieNode();
                        splitNode.children.put(remaining, leafNode);
                        leafNode.completeWord = word;
                        return;
                    }
                }
            }

            if (!matchedEdge) {
                TrieNode newNode = new TrieNode();
                ptr.children.put(remainingWord, newNode);
                newNode.completeWord = word;
                return;
            }
        }
    }

    public List<String> searchPrefix(String prefix) {
        List<String> matches = new ArrayList<>();
        searchFromRoot(prefix, root, matches);
        return matches;
    }

    private void searchFromRoot(String prefix, TrieNode node, List<String> matches) {
        for (Map.Entry<String, TrieNode> e : node.children.entrySet()) {
            int commonLength = getCommonPrefixLength(prefix, e.getKey());
            if (commonLength > 0) {
                if (commonLength == prefix.length()) {
                    collectAllWords(e.getValue(), matches);
                    return;
                } else {
                    searchFromRoot(prefix.substring(commonLength), e.getValue(), matches);
                    return;
                }
            }
        }
    }

    private void collectAllWords(TrieNode root, List<String> words) {
        if (root.completeWord != null) words.add(root.completeWord);
        for (TrieNode child : root.children.values()) {
            collectAllWords(child, words);
        }
    }

    private int getCommonPrefixLength(String a, String b) {
        int i = 0;
        for (i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) != b.charAt(i)) break;
        }
        return i;
    }
}
