package com.pdfreader;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Main window: toolbar on top, the PDF page in the middle, the AI assistant on the right. */
public class App extends Application {

    private static final double MIN_ZOOM = 0.6;
    private static final double MAX_ZOOM = 4.0;

    private final PageView pageView = new PageView();
    private final Label pageLabel = new Label("No file open");
    private final Label statusLabel = new Label("Open a PDF to get started.");

    private Stage stage;
    private AiPanel aiPanel;
    private WebSearch webSearch;
    private ContextMenu selectionMenu;

    private PdfDocumentModel model;
    private HighlightStore store;
    private List<Highlight> highlights = new ArrayList<>();
    private int currentPage = 0;
    private double zoom = 1.5;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.webSearch = new WebSearch(getHostServices());
        this.aiPanel = new AiPanel(new AiService());
        pageView.setSelectionListener(this::showSelectionMenu);

        StackPane pageHolder = new StackPane(pageView);
        pageHolder.setPadding(new Insets(16));
        pageHolder.setStyle("-fx-background-color: #e0e0e0;");

        ScrollPane scroll = new ScrollPane(pageHolder);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(scroll);
        root.setRight(aiPanel);
        root.setBottom(buildStatusBar());

        stage.setTitle("PDF Reader");
        stage.setScene(new Scene(root, 1200, 800));
        stage.show();

        // lets you run: mvn javafx:run -Dexec.args="C:/path/to/file.pdf"
        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) {
            openPdf(new File(args.get(0)));
        }
    }

    @Override
    public void stop() {
        closeCurrentDocument();
    }

    // ---------- UI pieces ----------

    private ToolBar buildToolbar() {
        Button open = button("Open PDF", this::chooseFile);
        Button prev = button("< Prev", () -> changePage(-1));
        Button next = button("Next >", () -> changePage(1));
        Button zoomOut = button("-", () -> changeZoom(1 / 1.2));
        Button zoomIn = button("+", () -> changeZoom(1.2));
        Button clear = button("Clear highlights on page", this::clearPageHighlights);
        return new ToolBar(open, prev, pageLabel, next, zoomOut, zoomIn, clear);
    }

    private StackPane buildStatusBar() {
        StackPane bar = new StackPane(statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        return bar;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ---------- opening files ----------

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openPdf(file);
        }
    }

    private void openPdf(File file) {
        statusLabel.setText("Opening " + file.getName() + "...");

        Task<PdfDocumentModel> task = new Task<>() {
            @Override
            protected PdfDocumentModel call() throws Exception {
                return PdfDocumentModel.open(file);
            }
        };

        task.setOnSucceeded(e -> {
            closeCurrentDocument();
            model = task.getValue();
            store = new HighlightStore(file);
            highlights = store.load();
            currentPage = 0;
            stage.setTitle(file.getName() + " - PDF Reader");
            refresh();
            statusLabel.setText("Drag your mouse over some text to select it.");
        });
        task.setOnFailed(e -> showError("Couldn't open that PDF", task.getException()));

        Thread worker = new Thread(task, "pdf-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void closeCurrentDocument() {
        if (model != null) {
            model.close();
            model = null;
        }
    }

    // ---------- paging and zoom ----------

    private void changePage(int delta) {
        if (model == null) return;
        int target = currentPage + delta;
        if (target < 0 || target >= model.pageCount()) return;
        currentPage = target;
        refresh();
    }

    private void changeZoom(double factor) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        refresh();
    }

    private void refresh() {
        if (model == null) return;
        try {
            pageView.show(model, currentPage, zoom, highlightsOn(currentPage));
            pageLabel.setText("Page " + (currentPage + 1) + " / " + model.pageCount());
        } catch (IOException ex) {
            showError("Couldn't draw this page", ex);
        }
    }

    // ---------- selection menu ----------

    private void showSelectionMenu(Selection selection, double screenX, double screenY) {
        if (selectionMenu != null) {
            selectionMenu.hide();
        }
        statusLabel.setText("Selected: " + shorten(selection.text(), 80));

        MenuItem highlight = new MenuItem("Highlight");
        highlight.setOnAction(e -> addHighlight(selection));

        MenuItem askAi = new MenuItem("Ask AI about \"" + shorten(selection.text(), 30) + "\"");
        askAi.setOnAction(e -> aiPanel.askAbout(selection.text(), selection.context()));

        Menu searchWeb = new Menu("Search the web");
        for (WebSearch.Engine engine : WebSearch.Engine.values()) {
            MenuItem item = new MenuItem(engine.label());
            item.setOnAction(e -> webSearch.search(engine, selection.text()));
            searchWeb.getItems().add(item);
        }

        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(selection.text());
            Clipboard.getSystemClipboard().setContent(content);
        });

        selectionMenu = new ContextMenu(highlight, askAi, searchWeb, copy);
        selectionMenu.show(stage, screenX, screenY);
    }

    // ---------- highlights ----------

    private void addHighlight(Selection selection) {
        highlights.add(new Highlight(selection.page(), selection.text(), selection.boxes()));
        saveHighlights();
        refresh();
    }

    private void clearPageHighlights() {
        highlights.removeIf(h -> h.page() == currentPage);
        saveHighlights();
        refresh();
    }

    private List<Highlight> highlightsOn(int page) {
        return highlights.stream().filter(h -> h.page() == page).collect(Collectors.toList());
    }

    private void saveHighlights() {
        if (store == null) return;
        try {
            store.save(highlights);
        } catch (IOException ex) {
            statusLabel.setText("Couldn't save highlights: " + ex.getMessage());
        }
    }

    // ---------- small helpers ----------

    private String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private void showError(String header, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(error == null ? "Unknown error" : error.getMessage());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
