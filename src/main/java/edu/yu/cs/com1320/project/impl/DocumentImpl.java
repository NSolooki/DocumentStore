package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Document;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public class DocumentImpl implements Document {
    
    private URI uri;
    private String txt;
    private byte[] binaryData;
    private Map<String, Integer> wordToCount;
    private long lastUseTime;

    /**
     * constructor for txt document
     * @param uri
     * @param txt
     */
    public DocumentImpl (URI uri, String txt) {
        if (uri == null || uri.toString().isEmpty() || txt == null || txt.isEmpty()) {
            throw new IllegalArgumentException("Argument is either null or blank.");
        }
        this.uri = uri;
        this.txt = txt;
        this.wordToCount = new HashMap<>();
        for (String word : this.getWordsList()) {
            int count = this.wordToCount.get(word) == null ? 0 : this.wordToCount.get(word);
            this.wordToCount.put(word, count+1);
        }
        this.lastUseTime = System.nanoTime();
    }

    /**
     * constructor for binary data document
     * @param uri
     * @param binaryData
     */
    public DocumentImpl (URI uri, byte[] binaryData) {
        if (uri == null || uri.toString().isEmpty() || binaryData == null || binaryData.length == 0) {
            throw new IllegalArgumentException("Argument is either null or blank.");
        }
        this.uri = uri;
        this.binaryData = binaryData;
        this.wordToCount = new HashMap<>();
        this.lastUseTime = System.nanoTime();
    }

    /**
     * @return content of text document
     */
    @Override
    public String getDocumentTxt () {
        return this.txt;
    }

    /**
     * @return content of binary data document
     */
    @Override
    public byte[] getDocumentBinaryData () {
        return this.binaryData;
    }

    /**
     * @return URI which uniquely identifies this document
     */
    @Override
    public URI getKey () {
        return this.uri;
    }

    /**
     * how many times does the given word appear in the document?
     * @param word
     * @return the number of times the given word appears in the document. If it's a binary document, return 0.
     */
    @Override
    public int wordCount (String word) {
        if (word == null) {
            throw new IllegalArgumentException("Word must not be null.");
        }
        word = this.stringFormatter(word);
        return (this.wordToCount.get(word) == null ? 0 : this.wordToCount.get(word));
    }

    /**
     * @return all the words that appear in the document
     */
    @Override
    public Set<String> getWords () {
        Set<String> wordSet = new HashSet<>();
        String text = this.getDocumentTxt();
        if (text == null) {
            return wordSet;
        }
        text = this.stringFormatter(text);
        String[] words = text.trim().split("\\s+");
        wordSet.addAll(Arrays.asList(words));
        return wordSet;
    }

    /**
     * (for stage 4 of project)
     * @return the last time this document was used, via put/get or via a search result
     */
    @Override
    public long getLastUseTime () {
        return this.lastUseTime;
    }

    @Override
    public void setLastUseTime (long timeInNanoseconds) {
        this.lastUseTime = timeInNanoseconds;
    }

    /**
     * @return a copy of the wordToCount map so it can be serialized
     */
    @Override
    public Map<String,Integer> getWordMap () {
        return this.wordToCount;
    }

    /**
     * Set the wordToCount Map during deserialization
     * @param wordMap
     */
    @Override
    public void setWordMap (Map<String,Integer> wordMap) {
        this.wordToCount = wordMap;
    }

    /**
     * @return all the words that appear in the document (with repetitions)
     */
    private List<String> getWordsList () {
        List<String> wordList = new ArrayList<>();
        String text = this.getDocumentTxt();
        if (text == null) {
            return wordList;
        }
        text = this.stringFormatter(text);
        String[] words = text.trim().split("\\s+");
        wordList.addAll(Arrays.asList(words));
        return wordList;
    }

    /**
     * formats given string to the appropriate form (only letters and numbers, all same case)
     * @param string string to format
     * @return formatted string
     */
    private String stringFormatter (String string) {
        string = string.replaceAll("[^A-Za-z0-9 ]", "");
        string = string.toUpperCase();
        return string;
    }

    @Override
    public int hashCode () {
        int result = uri.hashCode();
        result = 31 * result + (txt != null ? txt.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
        return result;
    }

    @Override
    public boolean equals (Object o) {
        return this.hashCode() == o.hashCode();
    }

    /**
     * Compares this document to another based on last time used
     * @param other
     * @return 1 if this document was used more recently, -1 if other document was used more recently, 0 if both were used at the same time
     */
    @Override
    public int compareTo (Document other) {
        if (other == null) {
            throw new IllegalArgumentException ("Doc must not be null.");
        }
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
}