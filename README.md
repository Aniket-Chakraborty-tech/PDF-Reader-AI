# PDF Reader + AI Assistant (Java)

A small desktop PDF reader built with JavaFX and Apache PDFBox.

- Open any text-based PDF and flip through pages, zoom in and out
- Drag the mouse over text to select it, then right there you can:
  - **Highlight** it (saved next to the PDF, so it's there next time you open it)
  - **Search the web** for it (Google, Dictionary, Wikipedia, YouTube)
  - **Ask AI** what it means (the AI also sees the sentence around it)
  - **Copy** it
- AI Assistant panel on the right: type any word or question and get an answer

## Requirements
- JDK 17 or newer
- Maven 3.8+
- An Anthropic API key (only for the AI part): https://console.anthropic.com

## Run it
```bash
# 1. set your API key (never put it in the code)
export ANTHROPIC_API_KEY="your-key-here"        # Mac/Linux
setx ANTHROPIC_API_KEY "your-key-here"          # Windows (then open a NEW terminal)

# 2. start the app
mvn javafx:run

# or open a file straight away
mvn javafx:run -Dexec.args="/path/to/book.pdf"
```

Optional: `CLAUDE_MODEL` env var changes the model (default `claude-sonnet-5-5`).

## Project layout
```
pdf-reader-ai/
├── pom.xml
├── README.md
├── .gitignore
└── src/main/java/com/pdfreader/
    ├── App.java               main window, toolbar, selection menu
    ├── PageView.java          draws a page, highlight layer, mouse drag selection
    ├── PdfDocumentModel.java  PDFBox: render pages + find word positions
    ├── HighlightStore.java    saves/loads highlights as JSON
    ├── WebSearch.java         opens browser searches
    ├── AiService.java         calls the Claude API
    ├── AiPanel.java           the AI side panel
    ├── WordBox.java           a word + its position
    ├── Highlight.java         a saved highlight
    └── Selection.java         what the user just selected
```

## Known limits
- Scanned PDFs (pictures of text) have no text layer, so selection won't work on them (needs OCR).
- Rotated pages may select slightly off.
