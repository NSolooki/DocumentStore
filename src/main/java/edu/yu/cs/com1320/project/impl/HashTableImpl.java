package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.HashTable;

/**
 * Instances of HashTable should be constructed with two type parameters, one for the type of the keys in the table and one for the type of the values
 * @param <Key>
 * @param <Value>
 */
public class HashTableImpl<Key, Value> implements HashTable<Key,Value> {

    private static final class Entry<Key, Value> {
        
        private Key key;
        private Value value;
        private Entry<Key, Value> next;

        private Entry (Key k, Value v) {
            if (k == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            this.key = k;
            this.value = v;
            this.next = null;
        }
    }

    private Entry<Key, Value>[] table;
    private int count;
    private static final double THRESHOLD = 0.75;
    
    /**
     * constructor for HashTable
     */
    public HashTableImpl () {
        this.table = new Entry[5];
        this.count = 0;
    }
    
    /**
     * @param k the key whose value should be returned
     * @return the value that is stored in the HashTable for k, or null if there is no such key in the table
     */
    @Override
    public Value get (Key k) {
        int index = this.hashFunction(k);
        Entry<Key, Value> current = this.table[index];
        if(current == null) {
            return null;
        }
        while(current.next != null && !current.key.equals(k)) {
            current = current.next;
        }
        return (current.key.equals(k) ? current.value : null);
    }

    /**
     * @param k the key at which to store the value
     * @param v the value to store.
     * To delete an entry, put a null value.
     * @return if the key was already present in the HashTable, return the previous value stored for the key. If the key was not already present, return null.
     */
    @Override
    public Value put (Key k, Value v) {
        if (v == null) {
            return this.delete(k);
        }
        if (((double) (this.count+1)/this.table.length) >= THRESHOLD) {
            this.rehash();
        }
        int index = this.hashFunction(k);
        Entry<Key, Value> finder = this.table[index];
        Entry<Key, Value> putEntry = new Entry<>(k, v);
        if (finder == null) {
            this.table[index] = putEntry;
            this.count++;
            return null;
        }
        if (finder.key.equals(k)) {
            Value old = finder.value;
            this.table[index].value = v;
            return old;
        }
        while (finder.next != null && !finder.next.key.equals(k)) {
            finder = finder.next;
        }
        if (finder.next == null) {
            finder.next = putEntry;
            return null;
        }
        else {
            Value old = finder.next.value;
            finder.next.value = v;
            return old;
        }
    }

    /**
     * @param k the key which to delete
     * @return value previously stored at the key, null if there was none
     */
    private Value delete (Key k) {
        int index = this.hashFunction(k);
        Entry<Key, Value> finder = this.table[index];
        if (finder == null) {
            return null;
        }
        if (finder.key.equals(k)) {
            this.table[index] = finder.next == null ? null : finder.next;
            if (finder.next == null) {
                this.count--;
            }
            return finder.value;
        }
        while (!finder.next.key.equals(k) && finder.next != null) {
            finder = finder.next;
        }
        if (finder.next == null) {
            return null;
        }
        if (finder.next.next == null) {
            Entry<Key, Value> old = finder.next;
            finder.next = null;
            return old.value;
        }
        else {
            Entry<Key, Value> old = finder.next;
            finder.next = finder.next.next;
            return old.value;
        }
    }

    /**
     * @param key the key to get the hashFunction for
     * @return hashFunction of that key
     */
    private int hashFunction (Key key) {
        return (key.hashCode() & 0x7fffffff) % this.table.length;
    }

    /**
     * doubles and rehashes the hashTable
     */
    private void rehash () {
        Entry<Key, Value>[] oldTable = this.table;
        this.table = new Entry[this.table.length*2];
        this.count = 0;
        for (Entry<Key, Value> current : oldTable) {
            while (current != null) {
                this.put(current.key, current.value);
                current = current.next;
            }
        }
    }
}