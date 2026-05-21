package com.bahairesearch.common.search;

import com.bahairesearch.common.model.CorpusSearchHit;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure-logic FTS5 search engine extracted from LocalCorpusSearchService.
 *
 * <p>All methods are static and operate entirely on in-memory data structures — no database access,
 * no platform APIs, no side effects. This class is shared between the Windows (JavaFX) and Android
 * versions of BahaiResearch to eliminate duplication of query building, tokenization, ranking,
 * filtering, and deduplication logic.</p>
 */
public final class SearchCore {

    /** Maximum distance between tokens for NEAR proximity matching. */
    public static final int NEAR_DISTANCE = 15;

    /** Score multiplier applied to NEAR proximity hits so they rank above AND/OR FTS5 hits. */
    public static final double NEAR_BOOST_MULTIPLIER = 1000.0;

    /** Scores at or below this threshold are treated as phrase-LIKE matches (ranked by length). */
    public static final double PHRASE_SCORE_THRESHOLD = -99995.0;

    /** Tokens deemed too common to be meaningful search terms. */
    public static final Set<String> NOISE_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "by", "for", "with", "and", "the", "from", "about",
            "quotes", "quote", "please", "show", "find",
            "are", "but", "can", "had", "has", "its", "may", "not", "out", "was",
            "all", "any", "she", "who", "why", "yet", "you", "how", "let", "too", "now")));

    /** Tokens that appear too frequently in queries to be useful as content or title filters. */
    public static final Set<String> GENERIC_QUERY_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "book", "books", "most", "issue", "issues")));

    private SearchCore() {}

    // -------------------------------------------------------------------------
    // FTS query building
    // -------------------------------------------------------------------------

    /**
     * Builds a NEAR query for FTS5 when the topic contains at least 2 meaningful tokens.
     * Returns empty string when there are fewer than 2 tokens.
     */
    public static String toFtsQueryNear(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        int tokenCount = tokens.size();
        if (tokenCount < 2 || tokenCount > 3) return "";
        if (tokenCount == 3) {
            // 3rd token placed outside NEAR so it is required (AND) rather than proximity-bound
           // return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + ", " + NEAR_DISTANCE + ")"
            //        + " AND " + tokens.get(2);
            // Changed back to using 3 NEAR tokens
            return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + " " + tokens.get(2) + ", " + NEAR_DISTANCE + ")";
        }
        return "NEAR(" + tokens.get(0) + " " + tokens.get(1) + ", " + NEAR_DISTANCE + ")";
    }

    /**
     * Builds an AND query for FTS5 from the meaningful tokens in the topic.
     */
    public static String toFtsQuery(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        return tokens.isEmpty() ? "" : buildAndQuery(tokens);
    }

    /**
     * Builds an OR query for FTS5 from the meaningful tokens in the topic.
     */
    public static String toFtsQueryOr(String topic, String resolvedAuthor) {
        List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
        if (tokens.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    /**
     * Extracts FTS5 search tokens from the user's topic, applying Unicode normalization
     * (NFD decomposition, accent stripping, lowercase) so that diacritics are handled
     * consistently in both FTS queries and post-retrieval content-term filtering.
     */
    public static List<String> extractFtsTokens(String topic, String resolvedAuthor) {
        if (topic == null) return Collections.emptyList();
        Set<String> authorTokens = buildAuthorTokenSet(resolvedAuthor);
        List<String> tokens = new ArrayList<>();
        for (String token : normalizeForMatch(topic).split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.length() >= 3 && !NOISE_TOKENS.contains(token)
                    && !authorTokens.contains(token)) {
                tokens.add(token + "*");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(tokens));
    }

    /**
     * Builds an FTS5 AND query from the given token list.
     * If 3 or fewer tokens, all are required (AND).
     * If more than 3, the first 3 are required and the rest are grouped as optional (OR).
     */
    static String buildAndQuery(List<String> tokens) {
        if (tokens.size() <= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(tokens.get(i));
            }
            return sb.toString();
        }
        List<String> required = tokens.subList(0, 3);
        List<String> optional = tokens.subList(3, tokens.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < required.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(required.get(i));
        }
        sb.append(" AND (");
        for (int i = 0; i < optional.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(optional.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Builds a set of author name tokens to exclude from query token extraction.
     */
    public static Set<String> buildAuthorTokenSet(String resolvedAuthor) {
        if (isEmpty(resolvedAuthor)) return Collections.emptySet();
        Set<String> tokens = new HashSet<>();
        for (String token : normalizeForMatch(resolvedAuthor).split("\\s+")) {
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Term and concept inference
    // -------------------------------------------------------------------------

    /**
     * Extracts meaningful content terms from the topic (for post-retrieval filtering).
     */
    public static List<String> extractContentTerms(String topic, String requiredAuthor) {
        String normalizedTopic = normalizeForMatch(topic);
        if (normalizedTopic.isEmpty()) return Collections.emptyList();
        Set<String> authorTerms = new HashSet<>();
        if (!isEmpty(requiredAuthor)) {
            for (String token : normalizeForMatch(requiredAuthor).split("\\s+")) {
                if (!token.isEmpty()) authorTerms.add(token);
            }
        }
        List<String> terms = new ArrayList<>();
        for (String token : normalizedTopic.split("\\s+")) {
            if (token.length() < 3) continue;
            if (NOISE_TOKENS.contains(token) || GENERIC_QUERY_TOKENS.contains(token)
                    || authorTerms.contains(token)) continue;
            terms.add(token);
        }
        return terms;
    }

    /**
     * Extracts meaningful tokens from a book title for scoped matching.
     */
    public static List<String> bookTokensFromTitle(String explicitTitle) {
        if (isEmpty(explicitTitle)) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        for (String token : normalizeForMatch(explicitTitle).split("\\s+")) {
            if (token.length() >= 3 && !NOISE_TOKENS.contains(token)
                    && !GENERIC_QUERY_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Post-retrieval filters
    // -------------------------------------------------------------------------

    /**
     * Filters hits to only those matching the requested author.
     */
    public static List<CorpusSearchHit> filterByRequestedAuthor(
            String requiredAuthor, List<CorpusSearchHit> hits) {
        if (isEmpty(requiredAuthor)) return hits;
        String normalized = normalizeForMatch(requiredAuthor);
        return hits.stream()
                .filter(hit -> normalizeForMatch(hit.author()).equals(normalized))
                .collect(Collectors.toList());
    }

    /**
     * Filters hits to only those containing at least one of the given content terms.
     */
    public static List<CorpusSearchHit> filterByContentTerms(
            List<CorpusSearchHit> hits, List<String> contentTerms) {
        if (contentTerms.isEmpty()) return hits;
        return hits.stream()
                .filter(hit -> containsAnyContentTerm(hit.quote(), contentTerms))
                .collect(Collectors.toList());
    }

    /**
     * Filters hits to only those matching the requested book title tokens.
     */
    public static List<CorpusSearchHit> filterByRequestedBook(
            List<CorpusSearchHit> hits, List<String> requestedBookTokens) {
        if (requestedBookTokens.isEmpty()) return hits;
        int requiredMatches = requestedBookTokens.size() <= 2 ? requestedBookTokens.size() : 2;
        return hits.stream()
                .filter(hit -> countBookTokenMatches(hit, requestedBookTokens) >= requiredMatches)
                .collect(Collectors.toList());
    }

    /**
     * Counts how many of the requested book tokens appear in the hit's title or URL.
     */
    public static int countBookTokenMatches(CorpusSearchHit hit, List<String> requestedBookTokens) {
        String normalizedTitle = normalizeForMatch(hit.title());
        String normalizedUrl   = normalizeForMatch(hit.sourceUrl());
        int matches = 0;
        for (String token : requestedBookTokens) {
            if (normalizedTitle.contains(token) || normalizedUrl.contains(token)) matches++;
        }
        return matches;
    }

    /**
     * Checks whether a quote text contains any of the given content terms (word-boundary aware).
     */
    public static boolean containsAnyContentTerm(String quote, List<String> contentTerms) {
        String normalizedQuote = normalizeForMatch(quote);
        List<String> quoteTokens = new ArrayList<>();
        for (String token : normalizedQuote.split("\\s+")) {
            if (!token.isEmpty()) quoteTokens.add(token);
        }
        for (String term : contentTerms) {
            for (String token : quoteTokens) {
                if (token.startsWith(term)) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ranking and boilerplate removal
    // -------------------------------------------------------------------------

    /**
     * Removes boilerplate passages (navigation text, copyright notices, etc.) and duplicates.
     */
    public static List<CorpusSearchHit> removeBoilerplateAndDuplicates(List<CorpusSearchHit> hits) {
        List<CorpusSearchHit> curated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CorpusSearchHit hit : hits) {
            if (boilerplateReason(hit) != null) continue;
            String key = normalizeForMatch(hit.quote());
            if (key.isEmpty() || !seen.add(key)) continue;
            curated.add(hit);
        }
        return curated;
    }

    /**
     * Sorts hits for display: phrase matches first (shorter = more precise), then BM25 score order.
     */
    public static List<CorpusSearchHit> rankForDisplay(List<CorpusSearchHit> hits) {
        return hits.stream()
                .sorted((l, r) -> {
                    // Tier 1: phrase-LIKE matches (sentinel score) — rank by length (shorter = more precise)
                    boolean lPhrase = l.score() <= PHRASE_SCORE_THRESHOLD;
                    boolean rPhrase = r.score() <= PHRASE_SCORE_THRESHOLD;
                    if (lPhrase != rPhrase) return lPhrase ? -1 : 1;
                    if (lPhrase) {
                        int lLen = l.quote() == null ? 0 : l.quote().length();
                        int rLen = r.quote() == null ? 0 : r.quote().length();
                        return Integer.compare(lLen, rLen);
                    }
                    // Tier 2: BM25 (or NEAR-boosted BM25) — more negative = more relevant
                    return Double.compare(l.score(), r.score());
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns a human-readable reason if the hit is boilerplate, or null if it's legitimate content.
     */
    public static String boilerplateReason(CorpusSearchHit hit) {
        if (hit.quote().trim().isEmpty()) return "empty";
        if (hit.quote().length() < 80) return "too-short";
        if (hit.quote().length() > 15_000) return "too-long";
        String q = normalizeForMatch(hit.quote());
        if (q.contains("bahai reference library"))                                return "bahai-ref-lib";
        if (q.startsWith("a collection of") || q.startsWith("a selection of"))    return "collection-header";
        if (q.contains("can be found here"))                                       return "found-here";
        if (q.contains("downloads about downloads")
                || q.contains("all downloads in authoritative writings and guidance")
                || q.contains("copyright and terms of use")
                || q.contains("read online")
                || q.contains("bahai org home")
                || q.contains("search the bahai reference library"))               return "nav-element";
        if (q.contains("see also"))                                                return "see-also";
        return null;
    }

    /**
     * Multiplies each hit's BM25 score so NEAR proximity results rank above AND/OR hits.
     */
    public static List<CorpusSearchHit> applyNearBoost(List<CorpusSearchHit> hits) {
        List<CorpusSearchHit> boosted = new ArrayList<>(hits.size());
        for (CorpusSearchHit hit : hits) {
            boosted.add(new CorpusSearchHit(
                    hit.quote(), hit.author(), hit.title(),
                    hit.locator(), hit.sourceUrl(),
                    hit.score() * NEAR_BOOST_MULTIPLIER));
        }
        return boosted;
    }

    /**
     * Merges two hit lists, deduplicating by (normalized quote + source URL).
     */
    public static List<CorpusSearchHit> mergeHits(
            List<CorpusSearchHit> primary, List<CorpusSearchHit> secondary) {
        List<CorpusSearchHit> merged = new ArrayList<>(primary);
        Set<String> seen = new HashSet<>();
        for (CorpusSearchHit hit : primary) {
            seen.add(normalizeForMatch(hit.quote()) + "|" + normalizeForMatch(hit.sourceUrl()));
        }
        for (CorpusSearchHit hit : secondary) {
            String key = normalizeForMatch(hit.quote()) + "|" + normalizeForMatch(hit.sourceUrl());
            if (seen.add(key)) merged.add(hit);
        }
        return merged;
    }

    // -------------------------------------------------------------------------
    // Normalization utilities
    // -------------------------------------------------------------------------

    /**
     * Normalizes a string for comparison: NFD decomposition, accent stripping, lowercase,
     * non-alphanumeric characters replaced with spaces, trimmed.
     */
    public static String normalizeForMatch(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Returns the value if non-null and non-blank, otherwise returns the fallback.
     */
    public static String blankToFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /**
     * Returns true if the string is null or whitespace-only.
     */
    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}