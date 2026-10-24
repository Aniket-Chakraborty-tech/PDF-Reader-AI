package com.pdfreader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a PDF opened with PDFBox. It renders pages to images and keeps
 * the position of every word so we know what's under the mouse.
 */
public class PdfDocumentModel implements AutoCloseable {

    private final File file;
    private final PDDocument document;
    private final PDFRenderer renderer;
    private final Map<Integer, List<WordBox>> wordsByPage;

    private PdfDocumentModel(File file, PDDocument document, Map<Integer, List<WordBox>> wordsByPage) {
        this.file = file;
        this.document = document;
        this.renderer = new PDFRenderer(document);
        this.wordsByPage = wordsByPage;
    }

    /** Opens the file and scans every page for words. Can be slow for big PDFs, so call it off the UI thread. */
    public static PdfDocumentModel open(File file) throws IOException {
        PDDocument doc = Loader.loadPDF(file);
        WordCollector collector = new WordCollector();
        collector.getText(doc); // this walks all pages and fills the collector
        return new PdfDocumentModel(file, doc, collector.words);
    }

    public File getFile() {
        return file;
    }

    public int pageCount() {
        return document.getNumberOfPages();
    }

    public BufferedImage renderPage(int pageIndex, float scale) throws IOException {
        return renderer.renderImage(pageIndex, scale);
    }

    public List<WordBox> wordsOn(int pageIndex) {
        return wordsByPage.getOrDefault(pageIndex, Collections.emptyList());
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException ignored) {
            // nothing useful we can do if closing fails
        }
    }

    /** Walks through the PDF text and records one WordBox per word. */
    private static class WordCollector extends PDFTextStripper {

        final Map<Integer, List<WordBox>> words = new HashMap<>();

        WordCollector() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            List<TextPosition> currentWord = new ArrayList<>();
            for (TextPosition tp : positions) {
                String ch = tp.getUnicode();
                if (ch == null || ch.isBlank()) {
                    finishWord(currentWord);
                } else {
                    currentWord.add(tp);
                }
            }
            finishWord(currentWord);
        }

        private void finishWord(List<TextPosition> chars) {
            if (chars.isEmpty()) {
                return;
            }
            StringBuilder text = new StringBuilder();
            float left = Float.MAX_VALUE;
            float top = Float.MAX_VALUE;
            float right = 0;
            float bottom = 0;

            for (TextPosition tp : chars) {
                text.append(tp.getUnicode());
                float baseline = tp.getYDirAdj();
                left = Math.min(left, tp.getXDirAdj());
                right = Math.max(right, tp.getXDirAdj() + tp.getWidthDirAdj());
                top = Math.min(top, baseline - tp.getHeightDir());
                bottom = Math.max(bottom, baseline);
            }

            int pageIndex = getCurrentPageNo() - 1;
            words.computeIfAbsent(pageIndex, k -> new ArrayList<>())
                 .add(new WordBox(text.toString(), left, top, right - left, bottom - top));
            chars.clear();
        }
    }
}
