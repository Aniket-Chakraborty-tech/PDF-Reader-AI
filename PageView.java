package com.pdfreader;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows one PDF page as an image, with two transparent layers on top:
 * one for yellow highlights and one that catches the mouse drag.
 */
public class PageView extends StackPane {

    public interface SelectionListener {
        void onSelected(Selection selection, double screenX, double screenY);
    }

    private static final int CONTEXT_WORDS = 12;
    private static final Color HIGHLIGHT_COLOR = Color.web("#FFEB3B", 0.45);

    private final ImageView imageView = new ImageView();
    private final Pane highlightLayer = new Pane();
    private final Pane mouseLayer = new Pane();
    private final Rectangle rubberBand = new Rectangle();

    private PdfDocumentModel model;
    private int pageIndex;
    private double scale = 1.0;
    private double dragStartX;
    private double dragStartY;
    private SelectionListener listener;

    public PageView() {
        rubberBand.setFill(Color.web("#2196F3", 0.2));
        rubberBand.setStroke(Color.web("#2196F3"));
        rubberBand.setVisible(false);
        rubberBand.setMouseTransparent(true);

        highlightLayer.setMouseTransparent(true);
        mouseLayer.setCursor(Cursor.TEXT);
        mouseLayer.getChildren().add(rubberBand);

        getChildren().addAll(imageView, highlightLayer, mouseLayer);
        setStyle("-fx-background-color: white; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0, 0, 2);");

        mouseLayer.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            dragStartX = e.getX();
            dragStartY = e.getY();
            rubberBand.setX(dragStartX);
            rubberBand.setY(dragStartY);
            rubberBand.setWidth(0);
            rubberBand.setHeight(0);
            rubberBand.setVisible(true);
        });

        mouseLayer.setOnMouseDragged(e -> {
            if (!rubberBand.isVisible()) return;
            rubberBand.setX(Math.min(dragStartX, e.getX()));
            rubberBand.setY(Math.min(dragStartY, e.getY()));
            rubberBand.setWidth(Math.abs(e.getX() - dragStartX));
            rubberBand.setHeight(Math.abs(e.getY() - dragStartY));
        });

        mouseLayer.setOnMouseReleased(e -> {
            if (!rubberBand.isVisible()) return;
            rubberBand.setVisible(false);

            // a plain click (tiny drag) shouldn't select anything
            if (rubberBand.getWidth() < 4 && rubberBand.getHeight() < 4) return;

            Selection selection = findWordsInside(rubberBand);
            if (selection != null && listener != null) {
                listener.onSelected(selection, e.getScreenX(), e.getScreenY());
            }
        });
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    /** Draws the given page at the given zoom, with its highlights. */
    public void show(PdfDocumentModel model, int pageIndex, double scale, List<Highlight> pageHighlights)
            throws IOException {
        this.model = model;
        this.pageIndex = pageIndex;
        this.scale = scale;

        BufferedImage rendered = model.renderPage(pageIndex, (float) scale);
        Image image = SwingFXUtils.toFXImage(rendered, null);
        imageView.setImage(image);

        setMinSize(image.getWidth(), image.getHeight());
        setPrefSize(image.getWidth(), image.getHeight());
        setMaxSize(image.getWidth(), image.getHeight());

        drawHighlights(pageHighlights);
    }

    private void drawHighlights(List<Highlight> pageHighlights) {
        highlightLayer.getChildren().clear();
        for (Highlight highlight : pageHighlights) {
            for (WordBox box : highlight.boxes()) {
                Rectangle r = new Rectangle(
                        box.x() * scale, box.y() * scale,
                        box.width() * scale, box.height() * scale);
                r.setFill(HIGHLIGHT_COLOR);
                highlightLayer.getChildren().add(r);
            }
        }
    }

    /** Finds every word the drag rectangle touches (rectangle is in screen pixels, so divide by scale). */
    private Selection findWordsInside(Rectangle area) {
        if (model == null) return null;

        double left = area.getX() / scale;
        double top = area.getY() / scale;
        double right = (area.getX() + area.getWidth()) / scale;
        double bottom = (area.getY() + area.getHeight()) / scale;

        List<WordBox> allWords = model.wordsOn(pageIndex);
        List<WordBox> picked = new ArrayList<>();
        int firstIndex = -1;
        int lastIndex = -1;

        for (int i = 0; i < allWords.size(); i++) {
            WordBox word = allWords.get(i);
            if (word.intersects(left, top, right, bottom)) {
                picked.add(word);
                if (firstIndex < 0) firstIndex = i;
                lastIndex = i;
            }
        }
        if (picked.isEmpty()) return null;

        String text = joinWords(picked);
        int from = Math.max(0, firstIndex - CONTEXT_WORDS);
        int to = Math.min(allWords.size(), lastIndex + CONTEXT_WORDS + 1);
        String context = joinWords(allWords.subList(from, to));

        return new Selection(pageIndex, text, context, picked);
    }

    private String joinWords(List<WordBox> words) {
        StringBuilder sb = new StringBuilder();
        for (WordBox w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.text());
        }
        return sb.toString();
    }
}
