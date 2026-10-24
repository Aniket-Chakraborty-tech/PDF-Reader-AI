package com.pdfreader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves highlights in a small JSON file next to the PDF
 * (e.g. notes.pdf -> notes.pdf.highlights.json), so they're still there next time.
 * The PDF itself is never modified.
 */
public class HighlightStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Highlight>>() { }.getType();

    private final Path jsonFile;

    public HighlightStore(File pdf) {
        this.jsonFile = pdf.toPath().resolveSibling(pdf.getName() + ".highlights.json");
    }

    public List<Highlight> load() {
        if (!Files.exists(jsonFile)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            List<Highlight> loaded = GSON.fromJson(reader, LIST_TYPE);
            return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
        } catch (IOException | JsonParseException e) {
            // a broken highlights file shouldn't stop the PDF from opening
            return new ArrayList<>();
        }
    }

    public void save(List<Highlight> highlights) throws IOException {
        Files.writeString(jsonFile, GSON.toJson(highlights));
    }
}
