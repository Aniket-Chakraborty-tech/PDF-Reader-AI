package com.pdfreader;

import java.util.List;

/**
 * What the user selected with the mouse.
 * "context" is the selected text plus a few words on each side, which
 * helps the AI work out the meaning of a word in this specific sentence.
 */
public record Selection(int page, String text, String context, List<WordBox> boxes) {
}
