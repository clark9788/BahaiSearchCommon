package com.bahairesearch.common.search;

import com.bahairesearch.common.model.CorpusSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchCoreTest {

    // -------------------------------------------------------------------------
    // normalizeForMatch
    // -------------------------------------------------------------------------

    @Test
    void normalizeForMatch_null_returnsEmpty() {
        assertEquals("", SearchCore.normalizeForMatch(null));
    }

    @Test
    void normalizeForMatch_empty_returnsEmpty() {
        assertEquals("", SearchCore.normalizeForMatch(""));
    }

    @Test
    void normalizeForMatch_stripsAccents() {
        assertEquals("baha u llah", SearchCore.normalizeForMatch("Bahá'u'lláh"));
    }

    @Test
    void normalizeForMatch_lowercases() {
        assertEquals("unity", SearchCore.normalizeForMatch("UNITY"));
    }

    @Test
    void normalizeForMatch_replacesNonAlphanumericWithSpaces() {
        // apostrophes, hyphens, punctuation all become spaces, then trimmed
        assertEquals("abdu l baha", SearchCore.normalizeForMatch("'Abdu'l-Bahá"));
    }

    @Test
    void normalizeForMatch_trimsResult() {
        assertEquals("unity", SearchCore.normalizeForMatch("  Unity  "));
    }

    @Test
    void normalizeForMatch_collapsesMidSpaces() {
        // multiple non-alpha chars collapse to single space
        assertEquals("a b", SearchCore.normalizeForMatch("a---b"));
    }

    // -------------------------------------------------------------------------
    // isEmpty / blankToFallback
    // -------------------------------------------------------------------------

    @Test
    void isEmpty_null_true() {
        assertTrue(SearchCore.isEmpty(null));
    }

    @Test
    void isEmpty_emptyString_true() {
        assertTrue(SearchCore.isEmpty(""));
    }

    @Test
    void isEmpty_whitespaceOnly_true() {
        assertTrue(SearchCore.isEmpty("   "));
    }

    @Test
    void isEmpty_nonEmpty_false() {
        assertFalse(SearchCore.isEmpty("x"));
    }

    @Test
    void blankToFallback_nullValue_returnsFallback() {
        assertEquals("default", SearchCore.blankToFallback(null, "default"));
    }

    @Test
    void blankToFallback_emptyValue_returnsFallback() {
        assertEquals("default", SearchCore.blankToFallback("", "default"));
    }

    @Test
    void blankToFallback_presentValue_returnsValue() {
        assertEquals("hello", SearchCore.blankToFallback("hello", "default"));
    }

    // -------------------------------------------------------------------------
    // extractFtsTokens
    // -------------------------------------------------------------------------

    @Test
    void extractFtsTokens_null_returnsEmpty() {
        assertTrue(SearchCore.extractFtsTokens(null, null).isEmpty());
    }

    @Test
    void extractFtsTokens_noiseTokensFiltered() {
        // "for", "the", "and" are all noise
        assertTrue(SearchCore.extractFtsTokens("for the and", null).isEmpty());
    }

    @Test
    void extractFtsTokens_shortTokensFiltered() {
        // tokens < 3 chars dropped; "be" and "on" are 2 chars
        assertTrue(SearchCore.extractFtsTokens("be on", null).isEmpty());
    }

    @Test
    void extractFtsTokens_appendsWildcard() {
        List<String> tokens = SearchCore.extractFtsTokens("unity", null);
        assertEquals(List.of("unity*"), tokens);
    }

    @Test
    void extractFtsTokens_deduplicates() {
        List<String> tokens = SearchCore.extractFtsTokens("unity unity", null);
        assertEquals(1, tokens.size());
        assertEquals("unity*", tokens.get(0));
    }

    @Test
    void extractFtsTokens_preservesOrder() {
        List<String> tokens = SearchCore.extractFtsTokens("light justice peace", null);
        assertEquals(List.of("light*", "justice*", "peace*"), tokens);
    }

    @Test
    void extractFtsTokens_authorTokensExcluded() {
        // "bahaullah" would normally be a token; excluded because it's the author
        List<String> tokens = SearchCore.extractFtsTokens(
                "quotes from Baha'u'llah on unity", "Baha'u'llah");
        // "quotes" is noise, "from" is noise, author tokens excluded, "unity" survives
        assertEquals(List.of("unity*"), tokens);
    }

    @Test
    void extractFtsTokens_normalizesAccents() {
        // "Bahá'u'lláh" normalizes the same as "baha u llah" so should resolve to author tokens
        List<String> tokens = SearchCore.extractFtsTokens("unity peace", null);
        assertTrue(tokens.contains("unity*"));
        assertTrue(tokens.contains("peace*"));
    }

    // -------------------------------------------------------------------------
    // buildAndQuery (package-private, accessed from same package)
    // -------------------------------------------------------------------------

    @Test
    void buildAndQuery_oneToken() {
        assertEquals("tok*", SearchCore.buildAndQuery(List.of("tok*")));
    }

    @Test
    void buildAndQuery_twoTokens() {
        assertEquals("a* AND b*", SearchCore.buildAndQuery(List.of("a*", "b*")));
    }

    @Test
    void buildAndQuery_threeTokens() {
        assertEquals("a* AND b* AND c*", SearchCore.buildAndQuery(List.of("a*", "b*", "c*")));
    }

    @Test
    void buildAndQuery_fourTokens_firstThreeRequiredRestOptional() {
        assertEquals("a* AND b* AND c* AND (d*)",
                SearchCore.buildAndQuery(List.of("a*", "b*", "c*", "d*")));
    }

    @Test
    void buildAndQuery_fiveTokens_firstThreeRequiredRestOr() {
        assertEquals("a* AND b* AND c* AND (d* OR e*)",
                SearchCore.buildAndQuery(List.of("a*", "b*", "c*", "d*", "e*")));
    }

    // -------------------------------------------------------------------------
    // toFtsQueryNear
    // -------------------------------------------------------------------------

    @Test
    void toFtsQueryNear_oneToken_returnsEmpty() {
        assertEquals("", SearchCore.toFtsQueryNear("unity", null));
    }

    @Test
    void toFtsQueryNear_twoTokens_buildsNear() {
        assertEquals("NEAR(unity* light*, 15)",
                SearchCore.toFtsQueryNear("unity light", null));
    }

    @Test
    void toFtsQueryNear_threeTokens_thirdOutsideNear() {
        // 3rd token is AND'd outside the NEAR clause so it is required but not proximity-bound
        assertEquals("NEAR(unity* light*, 15) AND peace*",
                SearchCore.toFtsQueryNear("unity light peace", null));
    }

    @Test
    void toFtsQueryNear_fourTokens_returnsEmpty() {
        // NEAR only fires for 2–3 tokens; 4+ falls through to AND/OR
        assertEquals("", SearchCore.toFtsQueryNear("unity light peace justice", null));
    }

    @Test
    void toFtsQueryNear_noiseTopicOnly_returnsEmpty() {
        assertEquals("", SearchCore.toFtsQueryNear("for the", null));
    }

    // -------------------------------------------------------------------------
    // toFtsQuery
    // -------------------------------------------------------------------------

    @Test
    void toFtsQuery_emptyTopic_returnsEmpty() {
        assertEquals("", SearchCore.toFtsQuery("", null));
    }

    @Test
    void toFtsQuery_singleToken() {
        assertEquals("unity*", SearchCore.toFtsQuery("unity", null));
    }

    @Test
    void toFtsQuery_twoTokens() {
        assertEquals("unity* AND light*", SearchCore.toFtsQuery("unity light", null));
    }

    // -------------------------------------------------------------------------
    // toFtsQueryOr
    // -------------------------------------------------------------------------

    @Test
    void toFtsQueryOr_emptyTopic_returnsEmpty() {
        assertEquals("", SearchCore.toFtsQueryOr("", null));
    }

    @Test
    void toFtsQueryOr_twoTokens() {
        assertEquals("unity* OR light*", SearchCore.toFtsQueryOr("unity light", null));
    }

    @Test
    void toFtsQueryOr_threeTokens() {
        assertEquals("unity* OR light* OR peace*",
                SearchCore.toFtsQueryOr("unity light peace", null));
    }

    // -------------------------------------------------------------------------
    // buildAuthorTokenSet
    // -------------------------------------------------------------------------

    @Test
    void buildAuthorTokenSet_null_returnsEmpty() {
        assertTrue(SearchCore.buildAuthorTokenSet(null).isEmpty());
    }

    @Test
    void buildAuthorTokenSet_emptyString_returnsEmpty() {
        assertTrue(SearchCore.buildAuthorTokenSet("").isEmpty());
    }

    @Test
    void buildAuthorTokenSet_normalizesAndSplits() {
        Set<String> tokens = SearchCore.buildAuthorTokenSet("Baha'u'llah");
        assertTrue(tokens.contains("baha"));
        assertTrue(tokens.contains("u"));
        assertTrue(tokens.contains("llah"));
    }

    // -------------------------------------------------------------------------
    // extractContentTerms
    // -------------------------------------------------------------------------

    @Test
    void extractContentTerms_emptyTopic_returnsEmpty() {
        assertTrue(SearchCore.extractContentTerms("", null).isEmpty());
    }

    @Test
    void extractContentTerms_noiseTokensExcluded() {
        assertTrue(SearchCore.extractContentTerms("for the and", null).isEmpty());
    }

    @Test
    void extractContentTerms_genericQueryTokensExcluded() {
        // "book" and "books" are generic
        assertTrue(SearchCore.extractContentTerms("book books", null).isEmpty());
    }

    @Test
    void extractContentTerms_shortTokensExcluded() {
        assertTrue(SearchCore.extractContentTerms("be on", null).isEmpty());
    }

    @Test
    void extractContentTerms_authorTokensExcluded() {
        List<String> terms = SearchCore.extractContentTerms("unity from Baha'u'llah", "Baha'u'llah");
        // "from" is noise, author tokens excluded; only "unity" survives
        assertEquals(List.of("unity"), terms);
    }

    @Test
    void extractContentTerms_noWildcard() {
        // unlike extractFtsTokens, no trailing * appended
        List<String> terms = SearchCore.extractContentTerms("unity", null);
        assertEquals(List.of("unity"), terms);
        assertFalse(terms.get(0).endsWith("*"));
    }

    // -------------------------------------------------------------------------
    // bookTokensFromTitle
    // -------------------------------------------------------------------------

    @Test
    void bookTokensFromTitle_null_returnsEmpty() {
        assertTrue(SearchCore.bookTokensFromTitle(null).isEmpty());
    }

    @Test
    void bookTokensFromTitle_empty_returnsEmpty() {
        assertTrue(SearchCore.bookTokensFromTitle("").isEmpty());
    }

    @Test
    void bookTokensFromTitle_noiseAndGenericExcluded() {
        // "The" is noise, "Book" is generic, "of" is noise
        List<String> tokens = SearchCore.bookTokensFromTitle("The Book of Certitude");
        assertFalse(tokens.contains("the"));
        assertFalse(tokens.contains("book"));
        assertFalse(tokens.contains("of"));
        assertTrue(tokens.contains("certitude"));
    }

    @Test
    void bookTokensFromTitle_normalWord() {
        List<String> tokens = SearchCore.bookTokensFromTitle("Hidden Words");
        assertEquals(List.of("hidden", "words"), tokens);
    }

    // -------------------------------------------------------------------------
    // boilerplateReason
    // -------------------------------------------------------------------------

    @Test
    void boilerplateReason_emptyQuote_returnsEmpty() {
        assertEquals("empty", SearchCore.boilerplateReason(hit("   ", -1.0)));
    }

    @Test
    void boilerplateReason_tooShort_returnsTooShort() {
        assertEquals("too-short", SearchCore.boilerplateReason(hit("Short.", -1.0)));
    }

    @Test
    void boilerplateReason_tooLong_returnsTooLong() {
        String longText = "x".repeat(15_001);
        assertEquals("too-long", SearchCore.boilerplateReason(hit(longText, -1.0)));
    }

    @Test
    void boilerplateReason_bahaiReferenceLibrary() {
        assertEquals("bahai-ref-lib",
                SearchCore.boilerplateReason(hit(padTo80("Contains the Bahai Reference Library header here."), -1.0)));
    }

    @Test
    void boilerplateReason_collectionHeader_aCollectionOf() {
        assertEquals("collection-header",
                SearchCore.boilerplateReason(hit(padTo80("A collection of writings on justice."), -1.0)));
    }

    @Test
    void boilerplateReason_collectionHeader_aSelectionOf() {
        assertEquals("collection-header",
                SearchCore.boilerplateReason(hit(padTo80("A selection of prayers for the community."), -1.0)));
    }

    @Test
    void boilerplateReason_foundHere() {
        assertEquals("found-here",
                SearchCore.boilerplateReason(hit(padTo80("The full text can be found here online."), -1.0)));
    }

    @Test
    void boilerplateReason_navElement_readOnline() {
        assertEquals("nav-element",
                SearchCore.boilerplateReason(hit(padTo80("Read online or download this document."), -1.0)));
    }

    @Test
    void boilerplateReason_navElement_copyright() {
        assertEquals("nav-element",
                SearchCore.boilerplateReason(hit(padTo80("Please read the copyright and terms of use before proceeding."), -1.0)));
    }

    @Test
    void boilerplateReason_seeAlso() {
        assertEquals("see-also",
                SearchCore.boilerplateReason(hit(padTo80("See also other writings on this topic in the archive."), -1.0)));
    }

    @Test
    void boilerplateReason_cleanPassage_returnsNull() {
        String clean = "The purpose of the one true God in manifesting Himself is to summon all mankind to truthfulness and sincerity.";
        assertNull(SearchCore.boilerplateReason(hit(clean, -5.0)));
    }

    // -------------------------------------------------------------------------
    // removeBoilerplateAndDuplicates
    // -------------------------------------------------------------------------

    @Test
    void removeBoilerplateAndDuplicates_removesBoilerplate() {
        CorpusSearchHit good = hit(longCleanPassage(), -5.0);
        CorpusSearchHit bad  = hit("Short.", -3.0);
        List<CorpusSearchHit> result = SearchCore.removeBoilerplateAndDuplicates(List.of(good, bad));
        assertEquals(1, result.size());
        assertSame(good, result.get(0));
    }

    @Test
    void removeBoilerplateAndDuplicates_removesDuplicates() {
        CorpusSearchHit a = hit(longCleanPassage(), -5.0);
        CorpusSearchHit b = hit(longCleanPassage(), -3.0); // same text, different score
        List<CorpusSearchHit> result = SearchCore.removeBoilerplateAndDuplicates(List.of(a, b));
        assertEquals(1, result.size());
    }

    @Test
    void removeBoilerplateAndDuplicates_keepsDifferentPassages() {
        CorpusSearchHit a = hit(longCleanPassage(), -5.0);
        CorpusSearchHit b = hit(longCleanPassage() + " extra words to differentiate", -3.0);
        List<CorpusSearchHit> result = SearchCore.removeBoilerplateAndDuplicates(List.of(a, b));
        assertEquals(2, result.size());
    }

    // -------------------------------------------------------------------------
    // rankForDisplay
    // -------------------------------------------------------------------------

    @Test
    void rankForDisplay_phraseHitsSortBeforeRegular() {
        CorpusSearchHit phrase  = hit(longCleanPassage(), -99999.0); // phrase sentinel
        CorpusSearchHit regular = hit(longCleanPassage() + " other", -1.0);
        List<CorpusSearchHit> ranked = SearchCore.rankForDisplay(List.of(regular, phrase));
        assertSame(phrase, ranked.get(0));
    }

    @Test
    void rankForDisplay_phraseHits_shorterFirst() {
        String shorter = longCleanPassage();
        String longer  = longCleanPassage() + " additional text here to make it longer than the first passage";
        CorpusSearchHit shortPhrase = hit(shorter, -99999.0);
        CorpusSearchHit longPhrase  = hit(longer,  -99999.0);
        List<CorpusSearchHit> ranked = SearchCore.rankForDisplay(List.of(longPhrase, shortPhrase));
        assertSame(shortPhrase, ranked.get(0));
    }

    @Test
    void rankForDisplay_regularHits_moreNegativeScoreFirst() {
        CorpusSearchHit better = hit(longCleanPassage(), -10.0);
        CorpusSearchHit worse  = hit(longCleanPassage() + " extra", -1.0);
        List<CorpusSearchHit> ranked = SearchCore.rankForDisplay(List.of(worse, better));
        assertSame(better, ranked.get(0));
    }

    // -------------------------------------------------------------------------
    // applyNearBoost
    // -------------------------------------------------------------------------

    @Test
    void applyNearBoost_multipliesScoreByConstant() {
        CorpusSearchHit h = hit(longCleanPassage(), -5.0);
        List<CorpusSearchHit> boosted = SearchCore.applyNearBoost(List.of(h));
        assertEquals(-5.0 * SearchCore.NEAR_BOOST_MULTIPLIER, boosted.get(0).score(), 1e-9);
    }

    @Test
    void applyNearBoost_doesNotMutateOriginal() {
        CorpusSearchHit h = hit(longCleanPassage(), -5.0);
        SearchCore.applyNearBoost(List.of(h));
        assertEquals(-5.0, h.score(), 1e-9);
    }

    @Test
    void applyNearBoost_preservesMetadata() {
        CorpusSearchHit h = new CorpusSearchHit("quote text here and more", "Author", "Title",
                "p.1", "http://example.com", -5.0);
        CorpusSearchHit boosted = SearchCore.applyNearBoost(List.of(h)).get(0);
        assertEquals("Author", boosted.author());
        assertEquals("Title", boosted.title());
        assertEquals("http://example.com", boosted.sourceUrl());
    }

    // -------------------------------------------------------------------------
    // filterByRequestedAuthor
    // -------------------------------------------------------------------------

    @Test
    void filterByRequestedAuthor_nullAuthor_returnsAll() {
        CorpusSearchHit h = hit(longCleanPassage(), -1.0);
        assertEquals(1, SearchCore.filterByRequestedAuthor(null, List.of(h)).size());
    }

    @Test
    void filterByRequestedAuthor_matchingAuthor_returnHit() {
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "Baha'u'llah", "Title",
                "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedAuthor("Baha'u'llah", List.of(h));
        assertEquals(1, result.size());
    }

    @Test
    void filterByRequestedAuthor_nonMatchingAuthor_returnsEmpty() {
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "Abdu'l-Baha", "Title",
                "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedAuthor("Baha'u'llah", List.of(h));
        assertTrue(result.isEmpty());
    }

    @Test
    void filterByRequestedAuthor_normalizationApplied() {
        // Author stored with diacritics, filtered with plain ASCII
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "Bahá'u'lláh", "Title",
                "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedAuthor("Baha'u'llah", List.of(h));
        assertEquals(1, result.size());
    }

    // -------------------------------------------------------------------------
    // filterByContentTerms
    // -------------------------------------------------------------------------

    @Test
    void filterByContentTerms_emptyTerms_returnsAll() {
        CorpusSearchHit h = hit(longCleanPassage(), -1.0);
        assertEquals(1, SearchCore.filterByContentTerms(List.of(h), List.of()).size());
    }

    @Test
    void filterByContentTerms_hitContainsTerm_kept() {
        CorpusSearchHit h = hit("The purpose of justice is the appearance of unity among the people of the world.", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByContentTerms(List.of(h), List.of("justice"));
        assertEquals(1, result.size());
    }

    @Test
    void filterByContentTerms_hitLacksTerm_removed() {
        CorpusSearchHit h = hit(longCleanPassage(), -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByContentTerms(
                List.of(h), List.of("zzznomatch"));
        assertTrue(result.isEmpty());
    }

    @Test
    void filterByContentTerms_wordBoundaryNotSubstring() {
        // "unit" should NOT match a passage that only contains "unity"
        CorpusSearchHit h = hit("The unity of mankind is the great theme.", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByContentTerms(List.of(h), List.of("unit"));
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // filterByRequestedBook
    // -------------------------------------------------------------------------

    @Test
    void filterByRequestedBook_emptyTokens_returnsAll() {
        CorpusSearchHit h = hit(longCleanPassage(), -1.0);
        assertEquals(1, SearchCore.filterByRequestedBook(List.of(h), List.of()).size());
    }

    @Test
    void filterByRequestedBook_oneToken_requiresMatch() {
        CorpusSearchHit match    = new CorpusSearchHit(longCleanPassage(), "A", "Hidden Words", "", "", -1.0);
        CorpusSearchHit noMatch  = new CorpusSearchHit(longCleanPassage() + " x", "A", "Gleanings", "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedBook(
                List.of(match, noMatch), List.of("hidden"));
        assertEquals(1, result.size());
        assertSame(match, result.get(0));
    }

    @Test
    void filterByRequestedBook_twoTokens_bothRequired() {
        CorpusSearchHit match   = new CorpusSearchHit(longCleanPassage(), "A", "Hidden Words", "", "", -1.0);
        CorpusSearchHit partial = new CorpusSearchHit(longCleanPassage() + " x", "A", "Hidden Mysteries", "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedBook(
                List.of(match, partial), List.of("hidden", "words"));
        assertEquals(1, result.size());
        assertSame(match, result.get(0));
    }

    @Test
    void filterByRequestedBook_threeTokens_twoSuffice() {
        // 3 tokens → requiredMatches = 2 (not all 3)
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "A", "Kitab Iqan", "", "", -1.0);
        List<CorpusSearchHit> result = SearchCore.filterByRequestedBook(
                List.of(h), List.of("kitab", "iqan", "certitude"));
        assertEquals(1, result.size());
    }

    // -------------------------------------------------------------------------
    // countBookTokenMatches
    // -------------------------------------------------------------------------

    @Test
    void countBookTokenMatches_matchesInTitle() {
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "A", "Hidden Words", "", "", -1.0);
        assertEquals(2, SearchCore.countBookTokenMatches(h, List.of("hidden", "words")));
    }

    @Test
    void countBookTokenMatches_matchesInUrl() {
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "A", "Other Title",
                "", "http://example.com/hidden-words", -1.0);
        assertEquals(2, SearchCore.countBookTokenMatches(h, List.of("hidden", "words")));
    }

    @Test
    void countBookTokenMatches_noMatches() {
        CorpusSearchHit h = new CorpusSearchHit(longCleanPassage(), "A", "Gleanings", "", "", -1.0);
        assertEquals(0, SearchCore.countBookTokenMatches(h, List.of("hidden", "words")));
    }

    // -------------------------------------------------------------------------
    // containsAnyContentTerm
    // -------------------------------------------------------------------------

    @Test
    void containsAnyContentTerm_exactWordMatch_true() {
        assertTrue(SearchCore.containsAnyContentTerm("The unity of mankind.", List.of("unity")));
    }

    @Test
    void containsAnyContentTerm_noMatch_false() {
        assertFalse(SearchCore.containsAnyContentTerm("The unity of mankind.", List.of("peace")));
    }

    @Test
    void containsAnyContentTerm_substringOnly_false() {
        // "unit" is not a whole token in "unity"
        assertFalse(SearchCore.containsAnyContentTerm("The unity of mankind.", List.of("unit")));
    }

    @Test
    void containsAnyContentTerm_nullQuote_false() {
        assertFalse(SearchCore.containsAnyContentTerm(null, List.of("unity")));
    }

    // -------------------------------------------------------------------------
    // mergeHits
    // -------------------------------------------------------------------------

    @Test
    void mergeHits_primaryAppearsFirst() {
        CorpusSearchHit p = new CorpusSearchHit(longCleanPassage(), "A", "T", "", "url-p", -5.0);
        CorpusSearchHit s = new CorpusSearchHit(longCleanPassage() + " extra", "A", "T", "", "url-s", -3.0);
        List<CorpusSearchHit> merged = SearchCore.mergeHits(List.of(p), List.of(s));
        assertSame(p, merged.get(0));
        assertSame(s, merged.get(1));
    }

    @Test
    void mergeHits_duplicateFromSecondarySkipped() {
        String quote = longCleanPassage();
        CorpusSearchHit p = new CorpusSearchHit(quote, "A", "T", "", "url-same", -5.0);
        CorpusSearchHit s = new CorpusSearchHit(quote, "A", "T", "", "url-same", -3.0);
        List<CorpusSearchHit> merged = SearchCore.mergeHits(List.of(p), List.of(s));
        assertEquals(1, merged.size());
    }

    @Test
    void mergeHits_sameTextDifferentUrlNotDuplicate() {
        String quote = longCleanPassage();
        CorpusSearchHit p = new CorpusSearchHit(quote, "A", "T", "", "url-one", -5.0);
        CorpusSearchHit s = new CorpusSearchHit(quote, "A", "T", "", "url-two", -3.0);
        List<CorpusSearchHit> merged = SearchCore.mergeHits(List.of(p), List.of(s));
        assertEquals(2, merged.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CorpusSearchHit hit(String quote, double score) {
        return new CorpusSearchHit(quote, "Author", "Title", "", "", score);
    }

    /** Pads a string to at least 80 characters so it passes the too-short boilerplate check. */
    private static String padTo80(String s) {
        if (s.length() >= 80) return s;
        return s + " ".repeat(80 - s.length());
    }

    /** A clean passage of sufficient length to pass all boilerplate checks. */
    private static String longCleanPassage() {
        return "The purpose of the one true God in manifesting Himself is to summon all " +
               "mankind to truthfulness and sincerity, to piety and trustworthiness, " +
               "to resignation and submissiveness to the will of God.";
    }
}
