package com.bahairesearch.common.model;

/**
 * A single passage result returned to the UI, including attribution fields.
 */
public final class QuoteResult {
    private final String quote;
    private final String author;
    private final String bookTitle;
    private final String paragraphOrPage;
    private final String sourceUrl;

    public QuoteResult(String quote, String author, String bookTitle,
                       String paragraphOrPage, String sourceUrl) {
        this.quote = quote;
        this.author = author;
        this.bookTitle = bookTitle;
        this.paragraphOrPage = paragraphOrPage;
        this.sourceUrl = sourceUrl;
    }

    public String quote()          { return quote; }
    public String author()         { return author; }
    public String bookTitle()      { return bookTitle; }
    public String paragraphOrPage(){ return paragraphOrPage; }
    public String sourceUrl()      { return sourceUrl; }
}