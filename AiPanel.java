package com.pdfreader;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Side panel: type a word (or a question) and the AI answers. Also used by "Ask AI" on a selection. */
public class AiPanel extends VBox {

    private final AiService ai;
    private final TextField input = new TextField();
    private final Button askButton = new Button("Ask");
    private final TextArea output = new TextArea();
    private final Label status = new Label();

    public AiPanel(AiService ai) {
        this.ai = ai;

        Label title = new Label("AI Assistant");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        input.setPromptText("Type a word or ask a question...");
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox row = new HBox(6, input, askButton);

        output.setEditable(false);
        output.setWrapText(true);
        VBox.setVgrow(output, Priority.ALWAYS);

        setSpacing(8);
        setPadding(new Insets(10));
        setPrefWidth(340);
        getChildren().addAll(title, row, output, status);

        askButton.setDefaultButton(false);
        askButton.setOnAction(e -> submit());
        input.setOnAction(e -> submit());

        if (!ai.isConfigured()) {
            output.setText("AI is turned off because ANTHROPIC_API_KEY isn't set.\n\n"
                    + "Set it in your terminal, then start the app again from the same terminal.");
        }
    }

    /** Called when the user picks "Ask AI" on highlighted text. */
    public void askAbout(String text, String context) {
        input.setText(text);
        run(text, context);
    }

    private void submit() {
        String text = input.getText().trim();
        if (!text.isEmpty()) {
            run(text, null);
        }
    }

    private void run(String text, String context) {
        if (!ai.isConfigured()) {
            status.setText("No API key found.");
            return;
        }

        askButton.setDisable(true);
        status.setText("Thinking...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // one to three words = look it up as a word; anything longer = treat as a question
                return looksLikeWord(text) ? ai.explain(text, context) : ai.ask(text);
            }
        };

        task.setOnSucceeded(e -> {
            output.setText(task.getValue());
            status.setText("");
            askButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            output.setText("Something went wrong:\n" + task.getException().getMessage());
            status.setText("");
            askButton.setDisable(false);
        });

        Thread worker = new Thread(task, "ai-request");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean looksLikeWord(String text) {
        return text.split("\\s+").length <= 3 && !text.endsWith("?");
    }
}
