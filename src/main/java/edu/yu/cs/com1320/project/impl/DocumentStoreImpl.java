
package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Undoable;
import edu.yu.cs.com1320.project.GenericCommand;
import edu.yu.cs.com1320.project.CommandSet;
import edu.yu.cs.com1320.project.Document;
import edu.yu.cs.com1320.project.DocumentStore;
import edu.yu.cs.com1320.project.PersistenceManager;

import java.io.InputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.function.Function;

public class DocumentStoreImpl implements DocumentStore {

    private BTreeImpl<URI, DocumentImpl> storage;
    private StackImpl<Undoable> commandStack;
    private TrieImpl<URI> trie;
    private MinHeapImpl<LUT> heap;
    private int documentCount;
    private int documentBytes;
    private Integer maxDocumentCount;
    private Integer maxDocumentBytes;
    private File dir;

    private final class LUT implements Comparable<LUT> {

        private URI uri;

        LUT (URI uri) {
            this.uri = uri;
        }

        @Override
        public boolean equals (Object o) {
            if (!(o instanceof LUT)) {
                return false;
            }
            return this.uri.equals(((LUT)o).getURI());
        }

        @Override
        public int compareTo (LUT other) {
            if (this.getLastUseTime() > other.getLastUseTime()) {
                return 1;
            }
            else if (this.getLastUseTime() < other.getLastUseTime()) {
                return -1;
            }
            else {
                return 0;
            }
        }

        private long getLastUseTime () {
            return storage.get(uri).getLastUseTime();
        }

        private URI getURI () {
            return this.uri;
        }
    }

    /**
     * Constructor for DocumentStore with default baseDir
     */
    public DocumentStoreImpl () {
        this(null);
    }

    /**
     * Constructor for DocumentStore with specific baseDir option
     * @param baseDir specific baseDir for PersistenceManager
     */
    public DocumentStoreImpl (File baseDir) {
        this.storage = new BTreeImpl<>();
        this.storage.setPersistenceManager((PersistenceManager) new DocumentPersistenceManager(baseDir));
        this.commandStack = new StackImpl<>();
        this.trie = new TrieImpl<>();
        this.heap = new MinHeapImpl<>();
        if (baseDir == null) {
            this.dir = new File(System.getProperty("user.dir"));
        }
        else {
            this.dir = baseDir;
        }
        try {
            this.storage.put(new URI(""), null);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * @param input the Document being put
     * @param uri unique identifier for the Document
     * @param format indicates which type of Document format is being passed
     * @return If there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc.
     *         If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException if there is an issue reading input
     * @throws IllegalArgumentException if uri or format are null
     */
    @Override
    public int putDocument (InputStream input, URI uri, DocumentFormat format) throws IOException {
        if (uri == null || uri.toString().isEmpty() || format == null) {
            throw new IllegalArgumentException("URI and Format must not be null/empty.");
        }
        if (input == null) {
            DocumentImpl deletion = this.removeDocument(this.storage.get(uri));
            this.addGenericCommand(uri, null, deletion);
            return (deletion == null ? 0 : deletion.hashCode());
        }
        byte[] content = input.readAllBytes();
        DocumentImpl doc = this.createDocument(content, uri, format);
        DocumentImpl prevDoc = this.storage.put(uri, doc);
        LinkedHashSet<URI> removedUris;
        if (prevDoc != null) {
            for (String word : prevDoc.getWords()) {
                this.trie.delete(word, prevDoc.getKey());
            }
            removedUris = this.updateHeapAndUsage(doc, prevDoc, System.nanoTime());
        }
        else {
            removedUris = this.addToHeapAndUsage(doc, System.nanoTime());
        }
        this.addGenericCommand(uri, doc, prevDoc, removedUris);
        return (prevDoc == null ? 0 : prevDoc.hashCode());
    }

    /**
     * @param uri the unique identifier of the Document to get
     * @return the given Document
     */
    @Override
    public Document getDocument (URI uri) {
        DocumentImpl doc;
        File file = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
        if (file.exists()) {
            doc = this.storage.get(uri);
            this.addToHeapAndUsage(doc, System.nanoTime());
            return doc;
        }
        else if (this.storage.get(uri) == null) {
            return null;
        }
        else {
            doc = this.storage.get(uri);
            doc.setLastUseTime(System.nanoTime());
            this.heap.reHeapify(new LUT(uri));
            return doc;
        }
    }

    /**
     * @param uri the unique identifier of the Document to delete
     * @return true if the Document is deleted, false if no Document exists with that URI
     */
    @Override
    public boolean deleteDocument (URI uri) {
        DocumentImpl doc = this.storage.get(uri);
        if (doc == null) {
            Function<URI, Boolean> function = functionUri -> true;
            Undoable command = new GenericCommand<URI>(uri, function); 
            this.commandStack.push(command);
            return false;
        }
        else {
            DocumentImpl deletion = this.removeDocument(doc);
            this.addGenericCommand(doc.getKey(), null, deletion);
            return true;
        }
    }

    /**
     * Creates a DocumentImpl and adds it to the Trie
     * @param content byte[] with the content to create the document
     * @param uri uri of the document
     * @param format format of the document
     * @return the DocumentImpl that was created
     */
    private DocumentImpl createDocument (byte[] content, URI uri, DocumentFormat format) {
        DocumentImpl doc;
        if (format == DocumentFormat.TXT) {
            String text = new String(content);
            doc = new DocumentImpl(uri, text);
        }
        else {
            doc = new DocumentImpl(uri, content);
        }
        for (String word : doc.getWords()) {
            this.trie.put(word, doc.getKey());
        }
        return doc;
    }

    /**
     * Removes the Document from the Trie, Heap, documentCount/Bytes (usage), and BTree.
     * @param doc
     * @return deleted Document
     */
    private DocumentImpl removeDocument (DocumentImpl doc) {
        for (String word : doc.getWords()) {
            this.trie.delete(word, doc.getKey());
        }
        this.removeFromHeapAndUsage(doc);
        return this.storage.put(doc.getKey(), null);
}

    /**
     * Undoes the last put or delete command
     * @throws IllegalStateException if there are no actions to be undone, i.e. the commandStack is empty
     */
    @Override
    public void undo () throws IllegalStateException {
        if (this.commandStack.peek() == null) {
            throw new IllegalStateException("There is no command in the commandStack to be undone.");
        }
        this.commandStack.pop().undo();
    }

    /**
     * Undoes the last put or delete that was done with the given URI as its key
     * @param uri
     * @throws IllegalStateException if there are no actions on the commandStack for the given URI
     */
    @Override
    public void undo (URI uri) throws IllegalStateException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null.");
        }
        if (this.commandStack.peek() == null) {
            throw new IllegalStateException("There is no command in the commandStack to be undone.");
        }
        StackImpl<Undoable> tempStack = new StackImpl<>();
        Undoable current = null;
        int commandStackSize = this.commandStack.size();
        boolean containsUri = false;
        for (int i = 0; i < commandStackSize; i++) {
            current = this.commandStack.pop();
            if (((current instanceof GenericCommand) && ((GenericCommand<URI>)current).getTarget().equals(uri)) || ((current instanceof CommandSet) && ((CommandSet<URI>)current).containsTarget(uri))) {
                containsUri = true;
                break;
            }
            tempStack.push(current);
        }
        int tempStackSize = tempStack.size();
        for (int i = 0; i < tempStackSize; i++) {
            this.commandStack.push(tempStack.pop());
        }
        if (current == null || !containsUri) {
            throw new IllegalStateException("There is no command with this URI to be undone.");
        }
        this.undo(uri, current);
    }
    
    /**
     * Undoes the command with this uri
     * @param uri
     * @param command
     */
    private void undo (URI uri, Undoable command) {
        if (command instanceof GenericCommand) {
            ((GenericCommand<URI>)command).undo();
        }
        else {
            ((CommandSet<URI>)command).undo(uri);
            if (!((CommandSet<URI>)command).isEmpty()) {
                this.commandStack.push(command);
            }
        }
    }

    /**
     * Retrieve all Documents whose text contains the given keyword.
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE INSENSITIVE.
     * @param keyword
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> search (String keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("Keyword must not be null.");
        }
        List<Document> matches = new ArrayList<>();
        keyword = this.stringFormatter(keyword);
        long currentUseTime = System.nanoTime();
        for (URI uri : trie.getAllSorted(keyword, this.createComparator(keyword))) {
            DocumentImpl doc;
            File file = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
            if (file.exists()) {
                doc = this.storage.get(uri);
                this.addToHeapAndUsage(doc, currentUseTime);
            }
            else {
                doc = this.storage.get(uri);
                doc.setLastUseTime(currentUseTime);
                this.heap.reHeapify(new LUT(uri));
            }
            matches.add(doc);
        }
        return matches;
    }

    /**
     * Retrieve all Documents whose text starts with the given prefix
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE INSENSITIVE.
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefix (String keywordPrefix) {
        if (keywordPrefix == null) {
            throw new IllegalArgumentException("Keyword must not be null.");
        }
        List<Document> matches = new ArrayList<>();
        keywordPrefix = this.stringFormatter(keywordPrefix);
        long currentUseTime = System.nanoTime();
        for (URI uri : trie.getAllWithPrefixSorted(keywordPrefix, this.createPrefixComparator(keywordPrefix))) {
            DocumentImpl doc;
            File file = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
            if (file.exists()) {
                doc = this.storage.get(uri);
                this.addToHeapAndUsage(doc, currentUseTime);
            }
            else {
                doc = this.storage.get(uri);
                doc.setLastUseTime(currentUseTime);
                this.heap.reHeapify(new LUT(uri));
            }
            matches.add(doc);
        }
        return matches;
    }

    /**
     * Completely remove any trace of any Document which contains the given keyword
     * @param keyword
     * @return a Set of URIs of the Documents that were deleted.
     */
    @Override
    public Set<URI> deleteAll (String keyword) {
        if (keyword == null) {
            throw new IllegalArgumentException("Keyword must not be null.");
        }
        keyword = this.stringFormatter(keyword);
        Set<URI> deletions = trie.deleteAll(keyword);
        Set<DocumentImpl> deletedDocs = new HashSet<>();
        for (URI uri : deletions) {
            DocumentImpl doc = this.storage.get(uri);
            deletedDocs.add(doc);
            this.removeFromHeapAndUsage(doc);
            this.storage.put(uri, null);
        }
        this.addCommandSet(deletedDocs);
        return deletions;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix
     * Delete is CASE INSENSITIVE.
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefix (String keywordPrefix) {
        if (keywordPrefix == null) {
            throw new IllegalArgumentException("Keyword must not be null.");
        }
        keywordPrefix = this.stringFormatter(keywordPrefix);
        Set<URI> deletions = trie.deleteAllWithPrefix(keywordPrefix);
        Set<DocumentImpl> deletedDocs = new HashSet<>();
        for (URI uri : deletions) {
            DocumentImpl doc = this.storage.get(uri);
            deletedDocs.add(doc);
            this.removeFromHeapAndUsage(doc);
            this.storage.put(uri, null);
        }
        this.addCommandSet(deletedDocs);
        return deletions;
    }

    /**
     * Creates a command and pushes it onto the commandStack
     * @param uri uri of the document the command was executed on
     * @param doc the document that is now held at that uri
     * @param prevDoc the document that was previously held at that uri
     */
    private void addGenericCommand (URI uri, DocumentImpl doc, DocumentImpl prevDoc) {
        Function<URI, Boolean> function = functionUri -> {
            if (doc != null) {
                for (String word : doc.getWords()) {
                    this.trie.delete(word, doc.getKey());
                }
                this.removeFromHeapAndUsage(doc);
            }
            this.storage.put(functionUri, prevDoc);
            if (prevDoc != null) {
                for (String word : prevDoc.getWords()) {
                    this.trie.put(word, prevDoc.getKey());
                }
                this.addToHeapAndUsage(prevDoc, System.nanoTime());
            }
            return true;
        };
        Undoable command = new GenericCommand<URI>(uri, function); 
        this.commandStack.push(command);
    }

    /**
     * Creates a command and pushes it onto the commandStack
     * @param uri uri of the document the command was executed on
     * @param doc the document that is now held at that uri
     * @param prevDoc the document that was previously held at that uri
     * @param removedUris set of URIs removed due to Usage limits
     */
    private void addGenericCommand (URI uri, DocumentImpl doc, DocumentImpl prevDoc, LinkedHashSet<URI> removedUris) {
        Function<URI, Boolean> function = functionUri -> {
            if (doc != null) {
                for (String word : doc.getWords()) {
                    this.trie.delete(word, doc.getKey());
                }
                this.removeFromHeapAndUsage(doc);
            }
            for (URI u : removedUris) {
                DocumentImpl d = this.storage.get(u);
                this.storage.put(u, d);
                this.addToHeapAndUsage(d, System.nanoTime());
            }
            this.storage.put(functionUri, prevDoc);
            if (prevDoc != null) {
                for (String word : prevDoc.getWords()) {
                    this.trie.put(word, prevDoc.getKey());
                }
                this.addToHeapAndUsage(prevDoc, System.nanoTime());
            }
            return true;
        };
        Undoable command = new GenericCommand<URI>(uri, function); 
        this.commandStack.push(command);
    }

    /**
     * Creates a CommandSet and pushes it onto the commandStack
     * @param docSet documents to be added to the CommandSet
     */
    private void addCommandSet (Set<DocumentImpl> docSet) {
        CommandSet<URI> commandSet = new CommandSet<>();
        for (DocumentImpl doc : docSet) {
            Function<URI, Boolean> function = functionUri -> {
                this.storage.put(functionUri, doc);
                for (String word : doc.getWords()) {
                    this.trie.put(word, doc.getKey());
                }
                this.addToHeapAndUsage(doc, System.nanoTime());
                return true;
            };
            GenericCommand<URI> command = new GenericCommand<>(doc.getKey(), function);
            commandSet.addCommand(command);
        }
        this.commandStack.push(commandSet);
    }

    /**
     * Compares two Documents based on number of occurrences of the given word
     * Results come in *descending* order of appearances
     * @param word
     * @return
     */
    private Comparator<URI> createComparator (String word) {
        Comparator<URI> comparator = (URI uri1, URI uri2) -> {
            if (this.storage.get(uri1).wordCount(word) > this.storage.get(uri2).wordCount(word)) {
                return -1;
            }
            else if (this.storage.get(uri1).wordCount(word) < this.storage.get(uri2).wordCount(word)) {
                return 1;
            }
            else {
                return 0;
            }
        };
        return comparator;
    }

    /**
     * Compares two Documents based on number of occurrences of the given prefix
     * Results come in *descending* order of appearances
     * @param prefix prefix which will already be formatted
     * @return
     */
    private Comparator<URI> createPrefixComparator (String prefix) {
        Comparator<URI> comparator = (URI uri1, URI uri2) -> {
            if (this.prefixCount(this.storage.get(uri1), prefix) > this.prefixCount(this.storage.get(uri2), prefix)) {
                return -1;
            }
            else if (this.prefixCount(this.storage.get(uri1), prefix) < this.prefixCount(this.storage.get(uri2), prefix)) {
                return 1;
            }
            else {
                return 0;
            }
        };
        return comparator;
    }

    /**
     * How many times does a word with the given prefix appear in the Document?
     * @param doc
     * @param prefix prefix which will already be formatted
     * @return the number of times words with the given prefix appears in the document. If it's a binary document, return 0.
     */
    private int prefixCount (Document doc, String prefix) {
        int count = 0;
        for (String word : doc.getWords()) {
            if (word.startsWith(prefix)) {
                count += doc.wordCount(word);
            }
        }
        return count;
    }

    /**
     * Formats given string to the appropriate form (only letters and numbers, all same case)
     * @param string string to format
     * @return formatted string
     */
    private String stringFormatter (String string) {
        string = string.replaceAll("[^A-Za-z0-9 ]", "");
        string = string.toUpperCase();
        return string;
    }

    /**
     * Set maximum number of Documents that may be stored
     * @param limit
     */
    @Override
    public void setMaxDocumentCount (int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must be at least 0.");
        }
        this.maxDocumentCount = limit;
        this.prepareHeap();
    }

    /**
     * Set maximum number of bytes of usage that may be used by all the Documents in usage combined
     * @param limit
     */
    @Override
    public void setMaxDocumentBytes (int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must be at least 0.");
        }
        this.maxDocumentBytes = limit;
        this.prepareHeap();
    }

    /**
     * Gets number of bytes of specified Document
     * @param doc
     * @return int of bytes
     */
    private int getBytes (DocumentImpl doc) {
        if (doc == null) {
            throw new IllegalArgumentException("Document must not be null.");
        }
        if (doc.getDocumentTxt() != null) {
            return doc.getDocumentTxt().getBytes().length;
        }
        else {
            return doc.getDocumentBinaryData().length;
        }
    }

    /**
     * Adds given Document to Heap and increments Usage (documentCount/Bytes)
     * @param doc
     * @param useTime
     * @return Set of URIs that were moved to disk in the process of preparing the Heap
     */
    private LinkedHashSet<URI> addToHeapAndUsage (DocumentImpl doc, long useTime) {
        doc.setLastUseTime(useTime);
        if (((this.maxDocumentCount != null) && (this.maxDocumentCount == 0)) || ((this.maxDocumentBytes != null) && (this.getBytes(doc) > this.maxDocumentBytes))) {
            return this.removeAllFromHeapAndUsage(doc);
        }
        LinkedHashSet<URI> removedUris = new LinkedHashSet<>(this.prepareHeap(doc));
        this.heap.insert(new LUT(doc.getKey()));
        this.documentCount++;
        this.documentBytes += this.getBytes(doc);
        return removedUris;
    }

    /**
     * Removes Document from Heap as well as Usage (documentCount/Bytes)
     * @param doc
     */
    private void removeFromHeapAndUsage (DocumentImpl doc) {
        doc.setLastUseTime(Long.MIN_VALUE);
        this.heap.reHeapify(new LUT(doc.getKey()));
        this.heap.remove(); //code breaks here - removes doc1 and not doc3
        this.documentCount--;
        this.documentBytes -= this.getBytes(doc);
    }

    /**
     * Updates the given Document in Heap and in Usage (documentCount/Bytes)
     * @param doc new Document containing the values to update with
     * @param prevDoc old Document which to update
     * @param useTime 
     * @return Set of URIs that were moved to disk in the process of preparing the Heap for this update
     */
    private LinkedHashSet<URI> updateHeapAndUsage (DocumentImpl doc, DocumentImpl prevDoc, long useTime) {
        this.documentCount--;
        this.documentBytes -= this.getBytes(prevDoc);
        doc.setLastUseTime(useTime);
        LinkedHashSet<URI> removedUris = new LinkedHashSet<>(this.prepareHeap(doc));
        this.heap.reHeapify(new LUT(doc.getKey()));
        this.documentCount++;
        this.documentBytes += this.getBytes(doc);
        return removedUris;
    }
    /**
     * Removes all Documents from Heap as well as Usage (documentCount/Bytes)
     * @param doc Document which will cause this method to be called
     * this method is only called when the maxDocumentCount == 0 or the Document's bytes > maxDocumentBytes
     * @return Set of all URIs that were in memory
     */
    private LinkedHashSet<URI> removeAllFromHeapAndUsage (DocumentImpl doc) {
        doc.setLastUseTime(System.nanoTime());
        this.heap.insert(new LUT(doc.getKey()));
        LinkedHashSet<URI> removedUris = new LinkedHashSet<>();
        this.documentCount++;
        this.documentBytes += this.getBytes(doc);
        while (this.documentCount > 0) {
            removedUris.add(this.moveDocumentToDisk());
        }
        return removedUris;
    }

    /**
     * Prepares Heap by moving necessary amount of Documents from memory to create space for new maxDocumentCount/Bytes (usage).
     * @param doc
     */
    private void prepareHeap () {
        if (this.maxDocumentCount == null && this.maxDocumentBytes == null) {
            return;
        }
        else if (this.maxDocumentCount == null) {
            while (this.documentBytes > this.maxDocumentBytes) {
                this.moveDocumentToDisk();
            }
        }
        else if (this.maxDocumentBytes == null) {
            while (this.documentCount > this.maxDocumentCount) {
                this.moveDocumentToDisk();
            }
        }
        else {
            while ((this.documentCount > this.maxDocumentCount) || (this.documentBytes > this.maxDocumentBytes)) {
                this.moveDocumentToDisk();
            }
        }
    }

    /**
     * Prepares Heap by moving necessary amount of Documents from memory to create space for new Document.
     * @param doc
     * @return Set of the URI of Documents that were moved to disk
     */
    private LinkedHashSet<URI> prepareHeap (DocumentImpl doc) {
        LinkedHashSet<URI> removedUris = new LinkedHashSet<>();
        if (this.maxDocumentCount == null && this.maxDocumentBytes == null) {
            return removedUris;
        }
        else if (this.maxDocumentCount == null) {
            while ((this.documentBytes + this.getBytes(doc)) > this.maxDocumentBytes) {
                removedUris.add(this.moveDocumentToDisk());
            }
            return removedUris;
        }
        else if (this.maxDocumentBytes == null) {
            while ((this.documentCount + 1) > this.maxDocumentCount) {
                removedUris.add(this.moveDocumentToDisk());
            }
            return removedUris;
        }
        else {
            while (((this.documentCount + 1) > this.maxDocumentCount) || ((this.documentBytes + this.getBytes(doc)) > this.maxDocumentBytes)) {
                removedUris.add(this.moveDocumentToDisk());
            }
            return removedUris;
        }
    }

    /**
     * Moves the least recently used Document to disk.
     * This entails deleting it from the Heap and Usage, and setting the URI's value in the BTree to a serialized Document.
     * @return URI of the Document that was moved to disk
     */
    private URI moveDocumentToDisk() {
        URI uri = this.heap.remove().getURI();
        DocumentImpl doc = this.storage.get(uri);
        this.documentCount--;
        this.documentBytes -= this.getBytes(doc);
        try {
            this.storage.moveToDisk(uri);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }
}