package com.bahairesearch.common.model;

import java.util.List;

/**
 * The complete result of a corpus search: a summary message and the ranked passage list.
 */
public final class ResearchReport {
    private final String summary;
    private final List<QuoteResult> quotes;

    public ResearchReport(String summary, List<QuoteResult> quotes) {
        this.summary = summary;
        this.quotes = quotes;
    }

    public String summary()          { return summary; }
    public List<QuoteResult> quotes(){ return quotes; }
}