# Plan: Move 3rd Token Outside NEAR Clause

## Context

The `toFtsQueryNear()` method in the shared `SearchCore` library currently bundles all 3 tokens inside the NEAR proximity clause. This is too restrictive—the 3rd token should be required document-wide but not proximity-constrained.

## Current Behavior

```sql
NEAR(token1 token2 token3, 15)
```

All three tokens must appear within 15 words of each other.

## Desired Behavior

```sql
NEAR(token1 token2, 15) AND token3
```

- First two tokens must be within proximity (15 words)
- Third token must appear anywhere in the document (ANDed outside NEAR)

## File to Change

**`src/main/java/com/bahairesearch/common/search/SearchCore.java`** — Method `toFtsQueryNear()` (currently lines 52–62)

### Before

```java
public static String toFtsQueryNear(String topic, String resolvedAuthor) {
    List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
    if (tokens.size() < 2) return "";
    // Use first 3 tokens for NEAR (matching AND's 3-required-token cap in buildAndQuery)
    List<String> nearTokens = tokens.size() >= 3 ? tokens.subList(0, 3) : tokens;
    return "NEAR(" + String.join(" ", nearTokens) + ", " + NEAR_DISTANCE + ")";
}
```

### After

```java
public static String toFtsQueryNear(String topic, String resolvedAuthor) {
    List<String> tokens = extractFtsTokens(topic, resolvedAuthor);
    if (tokens.size() < 2) return "";
    StringBuilder sb = new StringBuilder();
    sb.append("NEAR(").append(tokens.get(0)).append(" ").append(tokens.get(1))
      .append(", ").append(NEAR_DISTANCE).append(")");
    // 3rd+ token ANDed outside NEAR (document-wide, not proximity-constrained)
    for (int i = 2; i < tokens.size() && i < 3; i++) {
        sb.append(" AND ").append(tokens.get(i));
    }
    return sb.toString();
}
```

## Edge Cases

| Scenario | Output | Notes |
|---|---|---|
| 2 tokens | `NEAR(t1 t2, 15)` | Same as current |
| 3 tokens | `NEAR(t1 t2, 15) AND t3` | **New behavior** |
| < 2 tokens | `""` (empty) | Same as current |

## Impact

- Single-file change in the shared library
- Both Android (`BahaiResearchA`) and desktop (`BahaiResearch`) projects inherit via dependency
- No changes needed in `LocalCorpusSearchService` or any downstream code

---

# Deeper Dive: What Is Common vs. Duplicated Across Projects

## Architecture Overview

There are three codebases:

| Project | Path | Role |
|---|---|---|
| **BahaiSearchCommon** | `d:\AI-Python\BahaiSearchCommon` | Shared Java library (models + pure-logic search engine) |
| **BahaiResearchA** | `d:\AI-Python\BahaiResearchA` | Android app (depends on shared library) |
| **BahaiResearch** | (desktop JavaFX) | Desktop app (depends on shared library) |

## Shared Library (`BahaiSearchCommon`) — 375 Lines in `SearchCore.java`

### Models (`src/main/java/com/bahairesearch/common/model/`)

| File | Purpose |
|---|---|
| `CorpusSearchHit.java` | Raw FTS5 hit: quote, author, title, locator, sourceUrl, score |
| `QuoteResult.java` | UI-ready passage: quote, author, bookTitle, paragraphOrPage, sourceUrl |
| `ResearchReport.java` | Top-level result: summary + list of QuoteResults |

### Constants (`SearchCore.java`)

| Constant | Value | Usage |
|---|---|---|
| `NEAR_DISTANCE` | 15 | Max word distance for NEAR proximity matching |
| `NEAR_BOOST_MULTIPLIER` | 1000.0 | Score boost so NEAR hits rank above AND/OR |
| `PHRASE_SCORE_THRESHOLD` | -99995.0 | Sentinel score identifying phrase-LIKE matches |
| `NOISE_TOKENS` | Set of ~30 words | Filtered during token extraction (by, the, for, etc.) |
| `GENERIC_QUERY_TOKENS` | {book, books, most, issue, issues} | Filtered from content-term extraction |

### FTS Query Building Methods

| Method | Lines | Produces |
|---|---|---|
| `toFtsQueryNear()` | 56–62 | `NEAR(t1 t2 t3, 15)` — **THE METHOD TO CHANGE** |
| `toFtsQuery()` | 67–70 | AND query via `buildAndQuery()` |
| `toFtsQueryOr()` | 75–84 | OR query |
| `extractFtsTokens()` | 91–103 | Token list with `*` suffix, deduped, noise-filtered |
| `buildAndQuery()` | 110–133 | Up to 3 required AND tokens, rest as optional OR group |
| `buildAuthorTokenSet()` | 138–145 | Set of author name tokens to exclude from query |

### Term / Concept Extraction

| Method | Lines | Purpose |
|---|---|---|
| `extractContentTerms()` | 154–171 | Content terms for post-retrieval quote filtering |
| `bookTokensFromTitle()` | 176–186 | Tokenized title for book-scoped matching |

### Post-Retrieval Filters

| Method | Lines | Purpose |
|---|---|---|
| `filterByRequestedAuthor()` | 195–202 | Keep only hits matching a specific author |
| `filterByContentTerms()` | 207–213 | Keep only hits containing content terms |
| `filterByRequestedBook()` | 218–225 | Keep only hits matching book title tokens |
| `countBookTokenMatches()` | 230–238 | Count how many book tokens appear in hit's title/URL |
| `containsAnyContentTerm()` | 243–253 | Word-boundary-aware content term check |

### Ranking, Dedup, Boilerplate

| Method | Lines | Purpose |
|---|---|---|
| `removeBoilerplateAndDuplicates()` | 262–272 | Strip nav/copyright text + duplicate removal |
| `rankForDisplay()` | 277–293 | Phrase matches first (shorter=better), then by BM25 score |
| `boilerplateReason()` | 298–314 | Returns reason if hit is boilerplate, null if legitimate |
| `applyNearBoost()` | 319–328 | Multiply scores so NEAR hits rank above AND/OR |
| `mergeHits()` | 333–345 | Merge two hit lists, deduplicating by (quote + sourceUrl) |

### Utilities

| Method | Lines | Purpose |
|---|---|---|
| `normalizeForMatch()` | 355–360 | NFD decomposition, accent stripping, lowercase, alphanumeric only |
| `blankToFallback()` | 365–367 | Null/blank → fallback value |
| `isEmpty()` | 372–374 | Null or whitespace-only check |

---

## Android Project (`BahaiResearchA`) — What Is Duplicated

### `LocalCorpusSearchService.java` (649 lines)

This file should be a **thin database-access wrapper** around `SearchCore`, but instead it duplicates:

#### ❌ Duplicated Constants (lines 40–54)

```java
private static final int NEAR_DISTANCE = 15;
private static final double NEAR_BOOST_MULTIPLIER = 1000.0;
private static final double PHRASE_SCORE_THRESHOLD = -99995.0;
private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(...));
private static final Set<String> GENERIC_QUERY_TOKENS = new HashSet<>(Arrays.asList(...));
```

These should be removed; use `SearchCore.NEAR_DISTANCE`, etc.

#### ❌ Duplicated FTS Methods (lines 561–619)

| Duplicated Method | Should Use |
|---|---|
| `toFtsQueryNear()` | `SearchCore.toFtsQueryNear()` |
| `toFtsQuery()` | `SearchCore.toFtsQuery()` |
| `toFtsQueryOr()` | `SearchCore.toFtsQueryOr()` |
| `extractFtsTokens()` | `SearchCore.extractFtsTokens()` |
| `buildAndQuery()` | `SearchCore.buildAndQuery()` |
| `buildAuthorTokenSet()` | `SearchCore.buildAuthorTokenSet()` |

#### ❌ Duplicated Utilities (lines 625–642)

```java
private static String normalizeForMatch(String value)  → SearchCore.normalizeForMatch()
private static String blankToFallback(String value, String fallback) → SearchCore.blankToFallback()
private static boolean isEmpty(String value) → SearchCore.isEmpty()
```

Note: `trimToEmpty()` (line 632) only exists in the Android project — it may be used by SQL builders.

#### ❌ Duplicated Model Classes

| Android Package | Common Package |
|---|---|
| `com.bahairesearch.android.model.QuoteResult` | `com.bahairesearch.common.model.QuoteResult` |
| `com.bahairesearch.android.model.ResearchReport` | `com.bahairesearch.common.model.ResearchReport` |
| `CorpusSearchHit` (inner class, line ~500) | `com.bahairesearch.common.model.CorpusSearchHit` |

#### ❌ Partially Duplicated Pipeline Logic

The `search()` method in the Android project (lines 68–148) mirrors the desktop project's pipeline but mixes in Android-specific concerns (SQL builders, Cursor iteration). The shared `SearchCore` already provides all the pure-logic steps: filtering, ranking, dedup, merging.

### Root Cause

The Android project does **not currently declare a Gradle dependency** on `BahaiSearchCommon`. All the duplicated code was copy-pasted before the shared library existed. The shared library was extracted from the desktop project, but the Android project was never updated to use it.

### What's Legitimately Android-Specific (Not Duplicated)

| Code | Why Platform-Specific |
|---|---|
| SQL query builders (`buildHitsSql`, `buildPhraseSql`, `buildBookScopedSql`) | SQL syntax + parameter binding |
| `findHits()` method | FTS5 MATCH queries against `passages_fts` table |
| `fetchPhraseHits()` | LIKE-based phrase search against `passages` table |
| `findAdditionalBookScopedHits()` | Fallback query for book-scoped searches |
| `HitsResult` inner class | Tracks effective query + fallback state |
| Cursor iteration / result extraction | Android `Cursor` API |
| `logCount()` | Android `Log.i()` |
| `trimToEmpty()` | Only used in Android SQL binding |

---

## What Else Could Move to the Shared Library

### 1. The `HitsResult` Concept (Pattern, Not Literal Code)

Both projects have logic to track whether the NEAR query fired successfully vs. falling back to AND/OR. The **decision logic** (which query type won, which fallback was used) is pure logic and could be extracted, leaving only the actual SQL execution platform-specific.

### 2. The `search()` Pipeline Orchestration

The Android `search()` method (lines 68–148) and the desktop equivalent follow the same sequence:
1. Build 3 query variants (NEAR, AND, OR)
2. Execute queries → collect hits
3. Filter by author, book, content terms
4. Fetch phrase hits as fallback
5. Merge, deduplicate, rank
6. Build summary + format results

This orchestration could be a `SearchPipeline` class in the shared library, accepting a `HitFetcher` interface that each platform implements (Android: SQLite Cursor; Desktop: JDBC ResultSet).

### 3. Display Query Formatting (line 138–142)

```java
String displayQuery = hitsResult.effectiveQuery
    .replaceAll("NEAR\\(([^,]+),\\s*\\d+\\)", "$1")
    .replace("*", "")
    .replace(" AND ", " and ")
    .replace(" OR ", " or ");
```

This is pure string formatting. Could be `SearchCore.formatDisplayQuery()`.

### 4. Summary Message Building (lines 143–146)

```java
String summary = "Found " + quotes.size() + " passage(s) — searched: " + displayQuery;
if (hitsResult.usedFallback) {
    summary += "  (Tip: try fewer or more specific keywords)";
}
```

Pure string construction. Could be `SearchCore.buildSummary()`.

---

## Duplication Map (Summary Table)

| Component | Android (LocalCorpusSearchService.java) | Shared (SearchCore.java) | Status |
|---|---|---|---|
| Constants (5) | Lines 40–54 | Lines 27–44 | **Duplicated** |
| `toFtsQueryNear()` | Lines 561–566 | Lines 56–62 | **Duplicated** |
| `toFtsQuery()` | Lines 569–572 | Lines 67–70 | **Duplicated** |
| `toFtsQueryOr()` | Lines 575–586 | Lines 75–84 | **Duplicated** |
| `extractFtsTokens()` | Lines 567–585 (interleaved) | Lines 91–103 | **Duplicated** |
| `buildAndQuery()` | Lines 587–610 | Lines 110–133 | **Duplicated** |
| `buildAuthorTokenSet()` | Lines 612–619 | Lines 138–145 | **Duplicated** |
| `normalizeForMatch()` | Lines 625–630 | Lines 355–360 | **Duplicated** |
| `blankToFallback()` | Lines 636–638 | Lines 365–367 | **Duplicated** |
| `isEmpty()` | Lines 640–642 | Lines 372–374 | **Duplicated** |
| `QuoteResult` model | `bahairesearch.android.model` | `bahairesearch.common.model` | **Duplicated** |
| `ResearchReport` model | `bahairesearch.android.model` | `bahairesearch.common.model` | **Duplicated** |
| `CorpusSearchHit` model | Inner class in service | `bahairesearch.common.model` | **Duplicated** |
| `trimToEmpty()` | Line 632 | N/A | Android-only (keep) |
| SQL builders | Lines 154–200 | N/A | Android-only (keep) |
| Cursor/DB access | Lines 200–560 | N/A | Android-only (keep) |
| `logCount()` | Lines 644–648 | N/A | Android-only (keep) |
| Filter methods | Lines 460–560 (estimated) | Lines 195–253 | **Duplicated** |
| Ranking/boilerplate | Lines 260–450 (estimated) | Lines 262–345 | **Duplicated** |

## Recommended Migration Order

1. **Phase 1 — Quick Win:** Fix `toFtsQueryNear()` in shared library (single-file change, this plan)
2. **Phase 2 — Add Gradle Dependency:** Make Android project depend on `BahaiSearchCommon` via Maven Local or direct module reference
3. **Phase 3 — De-duplicate Models:** Delete Android's `QuoteResult`, `ResearchReport`, `CorpusSearchHit`; import from common
4. **Phase 4 — De-duplicate Methods:** Delete duplicated constants and utility/FTS methods; delegate to `SearchCore`
5. **Phase 5 — Extract Pipeline:** Create `SearchPipeline` interface + `HitFetcher` abstraction to share orchestration