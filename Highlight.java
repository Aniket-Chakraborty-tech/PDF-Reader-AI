package com.pdfreader;

import java.util.List;

/** A saved highlight: which page, what text, and the boxes to paint. */
public record Highlight(int page, String text, List<WordBox> boxes) {
}
