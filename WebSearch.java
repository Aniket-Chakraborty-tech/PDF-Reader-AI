package com.pdfreader;

import javafx.application.HostServices;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Opens a web search for the selected text in the user's normal browser. */
public class WebSearch {

    public enum Engine {
        GOOGLE("Google", "https://www.google.com/search?q="),
        DICTIONARY("Dictionary (define)", "https://www.google.com/search?q=define+"),
        WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/w/index.php?search="),
        YOUTUBE("YouTube", "https://www.youtube.com/results?search_query=");

        final String label;
        final String baseUrl;

        Engine(String label, String baseUrl) {
            this.label = label;
            this.baseUrl = baseUrl;
        }

        public String label() {
            return label;
        }
    }

    private final HostServices hostServices;

    public WebSearch(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void search(Engine engine, String text) {
        String query = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8);
        hostServices.showDocument(engine.baseUrl + query);
    }
}
