package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Trie;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * @param <Value>
 */
public class TrieImpl<Value> implements Trie<Value> {

    private static final int CHARACTER_SIZE = 36;
    private Node<Value> root;

    private static final class Node<Value> {
        
        private List<Value> values;
        private Node<Value>[] links;

        private Node () {
            this.values = new ArrayList<>();
            this.links = new Node[CHARACTER_SIZE];
        }
    }

    /**
     * constructor for Trie
     */
    public TrieImpl () {
        this.root = new Node<>();
    }

    /**
     * Add the given value at the given key
     * @param key
     * @param val
     */
    @Override
    public void put (String key, Value val) {
        if (key == null) {
            throw new IllegalArgumentException ("Key must not be null.");
        }
        if (val == null) {
            return;
        }
        else {
            this.root = this.put(this.root, key, val, 0);
        }
    }

    /**
     * Add the given value at the given key (private)
     * @param n current node
     * @param key 
     * @param val 
     * @param d current depth
     * @return final node in the key
     */
    private Node<Value> put (Node<Value> n, String key, Value val, int d) {
        if (n == null) {
            n = new Node<>();
        }
        if (d == key.length()) {
            if (!n.values.contains(val)) {
                n.values.add(val);
            }
            return n;
        }
        int index = indexFunction(key.charAt(d));
        n.links[index] = this.put(n.links[index], key, val, d+1);
        return n;
    }

    /**
     * Return node at which key is stored
     * @param n current node
     * @param key key which to get
     * @param d current depth
     * @return node at which key is stored
     */
    private Node<Value> get (Node<Value> n, String key, int d) {
        if (n == null) {
            return null;
        }
        if (d == key.length()) {
            return n;
        }
        int index = indexFunction(key.charAt(d));
        return this.get(n.links[index], key, d+1);
    }

    /**
     * Get all exact matches for the given key, sorted in descending order.
     * Search is CASE INSENSITIVE.
     * @param key
     * @param comparator used to sort values
     * @return a List of matching Values, in descending order
     */
    @Override
    public List<Value> getAllSorted (String key, Comparator<Value> comparator) {
        if (key == null || comparator == null) {
            throw new IllegalArgumentException("Argument must not be null.");
        }
        Node<Value> n = this.get(this.root, key, 0);
        if (n == null || key.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(n.values, comparator);
        return n.values;
    }

    /**
     * Get all matches which contain a String with the given prefix, sorted in descending order.
     * For example, if the key is "Too", you would return any value that contains "Tool", "Too", "Tooth", "Toodle", etc.
     * Search is CASE INSENSITIVE.
     * @param prefix
     * @param comparator used to sort values
     * @return a List of all matching Values containing the given prefix, in descending order
     */
    @Override
    public List<Value> getAllWithPrefixSorted (String prefix, Comparator<Value> comparator) {
        if (prefix == null || comparator == null) {
            throw new IllegalArgumentException("Argument must not be null.");
        }
        Node<Value> n = this.get(this.root, prefix, 0);
        List<Value> prefixValues = new ArrayList<>();
        if (n != null && !prefix.isEmpty()) {
            this.getAllWithPrefixSorted(prefix, n, prefixValues);
        }
        Collections.sort(prefixValues, comparator);
        return prefixValues;
    }

    /**
     * Get all matches which contain a String with the given prefix (private).
     * @param prefix 
     * @param n current node
     * @param prefixValues current list of prefix values
     */
    private void getAllWithPrefixSorted (String prefix, Node<Value> n, List<Value> prefixValues) {
        if (n.values != null && !n.values.isEmpty()) {
            for (Value v : n.values) {
                if (!prefixValues.contains(v)) {
                    prefixValues.add(v);
                }
            }
        }
        for (Node<Value> link : n.links) {
            if (link != null) {
                this.getAllWithPrefixSorted(prefix, link, prefixValues);
            }
        }
    }

    /**
     * Get all matches which contain a String with the given prefix - UNSORTED.
     * @param prefix
     * @return list of prefix values
     */
    private List<Value> getAllWithPrefix (String prefix) {
        Node<Value> n = this.get(this.root, prefix, 0);
        List<Value> prefixValues = new ArrayList<>();
        if (n != null) {
            this.getAllWithPrefix(prefix, n, prefixValues);
        }
        return prefixValues;
    }

    /**
     * Get all matches which contain a String with the given prefix - UNSORTED (private).
     * @param prefix
     * @param n current node
     * @param prefixValues current list of prefix values
     */
    private void getAllWithPrefix (String prefix, Node<Value> n, List<Value> prefixValues) {
        if (n.values != null && !n.values.isEmpty()) {
            for (Value v : n.values) {
                if (!prefixValues.contains(v)) {
                    prefixValues.add(v);
                }
            }
        }
        for (Node<Value> link : n.links) {
            if (link != null) {
                this.getAllWithPrefix(prefix, link, prefixValues);
            }
        }
    }

    /**
     * Remove the given value from the node of the given key (do not remove the value from other nodes in the Trie)
     * @param key
     * @param val
     * @return the value which was deleted. If the key did not contain the given value, return null.
     */
    @Override
    public Value delete (String key, Value val) {
        if (key == null || val == null) {
            throw new IllegalArgumentException ("Argument must not be null.");
        }
        Node<Value> deletionNode = this.get(this.root, key, 0);
        if (deletionNode == null) {
            return null;
        }
        Value deletion = deletionNode.values.contains(val) ? deletionNode.values.get(deletionNode.values.indexOf(val)) : null;
        if (deletion == null) {
            return null;
        }
        this.root = this.delete(this.root, key, val, 0);
        return deletion;
    }

    /**
     * Remove the given value from the node of the given key (private).
     * @param n current node
     * @param key 
     * @param val
     * @param d current depth
     * @return if the node is still relevant - the node; if no longer relevant - null
     */
    private Node<Value> delete (Node<Value> n, String key, Value val, int d) {
        if (n == null) {
            return null;
        }
        if (d == key.length()) {
            n.values.remove(val);
        }
        else {
            int index = indexFunction(key.charAt(d));
            n.links[index] = this.delete(n.links[index], key, val, d+1);       
        }
        if (n.values != null || !n.values.isEmpty()) {
            return n;
        }
        for (Node<Value> link : n.links) {
            if (link != null) {
                return n;
            }
        }
        return null;
    }

    /**
     * Delete all values from the node of the given key (do not remove the values from other nodes in the Trie)
     * @param key
     * @return a Set of all Values that were deleted.
     */
    @Override
    public Set<Value> deleteAll (String key) {
        if (key == null) {
            throw new IllegalArgumentException ("Key must not be null.");
        }
        Set<Value> deletions = new HashSet<>();
        if (key.isEmpty()) {
            return deletions;
        }
        this.root = this.deleteAll(this.root, key, 0, deletions);
        return deletions;
    }

    /**
     * Delete all values from the node of the given key (private).
     * @param n current node
     * @param key 
     * @param d current depth
     * @param deletions current Set of deleted values
     * @return if the node is still relevant - the node; if no longer relevant - null
     */
    private Node<Value> deleteAll (Node<Value> n, String key, int d, Set<Value> deletions) {
        if (n == null) {
            return null;
        }
        if (d == key.length()) {
            deletions.addAll(n.values);
            n.values.clear();
        }
        else {
            int index = indexFunction(key.charAt(d));
            n.links[index] = this.deleteAll(n.links[index], key, d+1, deletions);
        }
        if (n.values != null || !n.values.isEmpty()) {
            return n;
        }
        for (Node<Value> link : n.links) {
            if (link != null) {
                return n;
            }
        }
        return null;
    }

    /**
     * Delete the subtree rooted at the last character of the prefix.
     * Search is CASE INSENSITIVE.
     * @param prefix
     * @return a Set of all Values that were deleted; an empty set if an empty string was passed
     */
    @Override
    public Set<Value> deleteAllWithPrefix (String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException ("Prefix must not be null.");
        }
        Set<Value> deletions = new HashSet<>();
        if (prefix.isEmpty()) {
            return deletions;
        }
        this.root = this.deleteAllWithPrefix(this.root, prefix, 0, deletions);
        return deletions;
    }

    /**
     * Delete the subtree rooted at the last character of the prefix (private).
     * @param n current node
     * @param prefix
     * @param d current depth
     * @param deletions current set of deleted values
     * @return if the node is still relevant - the node; if no longer relevant - null
     */
    private Node<Value> deleteAllWithPrefix (Node<Value> n, String prefix, int d, Set<Value> deletions) {
        if (n == null) {
            return null;
        }
        if (d == prefix.length()) {
            deletions.addAll(getAllWithPrefix(prefix));
            n = null;
        }
        else {
            int index = indexFunction(prefix.charAt(d));
            n.links[index] = this.deleteAllWithPrefix(n.links[index], prefix, d+1, deletions);       
        }
        if (n != null) {
            if (n.values != null || !n.values.isEmpty()) {
                return n;
            }
            for (Node<Value> link : n.links) {
                if (link != null) {
                    return n;
                }
            }
        }
        return null;
    }

    /**
     * Return an index for the array based on given char
     * @param c char for which to get index of
     * @return index
     */
    private int indexFunction (char c) {
        c = Character.toUpperCase(c);
        return Character.isDigit(c) ? c-48 : c-55;
    }
}