package com.pdfreader;

/**
 * One word on a PDF page. Coordinates are in PDF points (1/72 inch),
 * measured from the top-left corner of the page.
 */
public record WordBox(String text, float x, float y, float width, float height) {

    /** True if this word overlaps the given rectangle (also in PDF points). */
    public boolean intersects(double left, double top, double right, double bottom) {
        return x < right && x + width > left && y < bottom && y + height > top;
    }
}
