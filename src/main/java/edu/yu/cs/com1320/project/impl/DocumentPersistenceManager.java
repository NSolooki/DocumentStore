package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Document;
import edu.yu.cs.com1320.project.PersistenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;

import com.google.gson.*;

/**
 * created by the DocumentStore and given to the BTree via a call to BTree.setPersistenceManager
 */
public class DocumentPersistenceManager implements PersistenceManager<URI, Document> {

    private File dir;

    /**
     * Constructor for DocumentPersistenceManager
     * @param baseDir where to create the Base Directory, if null it will be set to user.dir
     */
    public DocumentPersistenceManager (File baseDir) {
        if (baseDir == null) {
            this.dir = new File(System.getProperty("user.dir"));
        }
        else {
            this.dir = baseDir;
        }
    }

    /**
     * Serializes the Document (uses helper lambda below).
     * @param uri uri of the Document to serialize
     * @param val the Document to serialize
     * @throws IOException if there is an issue reading input
     */
    @Override
    public void serialize (URI uri, Document val) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null.");
        }
        Gson gson = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, serializer).setPrettyPrinting().serializeNulls().create();
        File file = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
        if (!file.exists()) {
            File parent = new File(file.getParent());
            parent.mkdirs();
            file.createNewFile();
        }
        try (FileWriter fw = new FileWriter(file)) {
            gson.toJson(val, fw);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JsonSerializer<DocumentImpl> serializer = (DocumentImpl doc, Type type, JsonSerializationContext context) -> {
        JsonObject json = new JsonObject();
        Gson gson = new Gson();
        json.add("uri", gson.toJsonTree(doc.getKey()));
        json.add("txt", gson.toJsonTree(doc.getDocumentTxt()));
        if (doc.getDocumentTxt() != null) {
            json.add("binaryData", JsonNull.INSTANCE);
        }
        else {
            json.add("binaryData", gson.toJsonTree(doc.getDocumentBinaryData()));
        }
        json.add("wordToCount", gson.toJsonTree(doc.getWordMap()));
        return json;
    };

    /**
     * Deserializes the Document.
     * @param uri uri of the Document to deserialize
     * @return the deserialized Document
     * @throws IOException if there is an issue reading input
     */
    @Override
    public Document deserialize (URI uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null.");
        }
        Gson gson = new Gson();
        File file = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
        DocumentImpl doc;
        try (FileReader json = new FileReader(file)) {
            doc = gson.fromJson(json, DocumentImpl.class);
        }
        catch (FileNotFoundException e) {
            return null;
        }
        this.delete(uri);
        return doc;
    }

    /**
     * delete the file stored on disk that corresponds to the given key
     * @param key
     * @return true or false to indicate if deletion occured or not
     * @throws IOException
     */
    @Override
    public boolean delete (URI uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null.");
        }
        File deletion = new File(dir, (uri.getAuthority() + uri.getPath() + ".json"));
        return deletion.delete();
    }
}