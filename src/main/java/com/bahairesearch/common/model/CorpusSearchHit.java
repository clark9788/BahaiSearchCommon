package com.bahairesearch.common.model;

/**
 * Raw FTS5 search hit returned from the database before post-processing and ranking.
 */
public final class CorpusSearchHit {
    private final String quote;
    private final String author;
    private final String title;
    private final String locator;
    private final String sourceUrl;
    private final double score;

    public CorpusSearchHit(String quote, String author, String title,
                           String locator, String sourceUrl, double score) {
        this.quote = quote;
        this.author = author;
        this.title = title;
        this.locator = locator;
        this.sourceUrl = sourceUrl;
        this.score = score;
    }

    public String quote()     { return quote; }
    public String author()    { return author; }
    public String title()     { return title; }
    public String locator()   { return locator; }
    public String sourceUrl() { return sourceUrl; }
    public double score()     { return score; }
}