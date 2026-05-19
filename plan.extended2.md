# BahaiSearchCommon — Consolidation Effort Evaluation

*Assessment date: 2026-05-18*

---

## Executive Summary

The foundational work is done and solid: `SearchCore.java` (375 lines) correctly captures
all shared pure logic, and the three model classes exist in the right package. The remaining
work—wiring both consumers to actually *use* Common instead of their own copies—is the harder
half of the job. It is achievable but not trivial. Estimated effort: **3–6 focused days**
depending on how much regression testing you build along the way.

---

## What Is Already Done

| Artifact | Status |
|---|---|
| `model/CorpusSearchHit.java` | ✅ in Common |
| `model/QuoteResult.java` | ✅ in Common |
| `model/ResearchReport.java` | ✅ in Common |
| `search/SearchCore.java` (375 lines) | ✅ in Common |
| BahaiResearch `pom.xml` declares `bahai-search-common 1.0.0` | ✅ already present |
| Java 11 target for portability across both consumers | ✅ set in Common's POM |

SearchCore already covers: all FTS query builders (NEAR / AND / OR), token extraction,
noise filtering, content-term extraction, book-token extraction, all post-retrieval filters,
boilerplate detection, deduplication, ranking, NEAR boost, mergeHits, and normalization
utilities. That is the right scope — all of it is pure Java SE with no platform touch.

---

## What Remains — by Project

### BahaiResearch (Windows / JavaFX, 966-line LocalCorpusSearchService)

**Duplicate model classes that still exist locally:**
- `model/QuoteResult.java` — identical to Common; safe to delete and re-import
- `model/ResearchReport.java` — identical to Common; safe to delete and re-import
- Note: `CorpusSearchHit` is *not* in BahaiResearch's model package; it lives inside
  `LocalCorpusSearchService` as an internal concern and maps cleanly to `common.model.CorpusSearchHit`.

**LocalCorpusSearchService.java is 966 lines** — significantly larger than the Android
counterpart (649 lines). The extra ~317 lines are *desktop-only features* that must
**not** go into Common:

| Desktop-only feature | Lines (approx.) |
|---|---|
| `inferRequiredAuthor()` — detects author from free text | ~30 |
| `inferRequestedBookTokens()` — "from X" / "in book X" pattern | ~40 |
| `inferEffectiveConceptTerms()` — pulls AI-extracted concepts | ~25 |
| Semantic AI fallback (OR query with Gemini concepts) | ~40 |
| AI reranking via `GeminiClient.rerankLocalCandidates()` | ~35 |
| `findAdditionalBookScopedHits()` — supplement for book scope | ~55 |
| Debug intent logging (`[IntentDebug]`, `[PipelineCount]`, `[BoilerplateFilter]`) | ~50 |
| SQLite retry logic (3× with 200 ms exponential back-off) | ~20 |

**Two ranking items previously flagged as divergences are actually dead/abandoned code
and should be deleted from the desktop service, not migrated:**
- `sourcePriority()` / UHJ source priority — an earlier experiment in categorizing UHJ
  text by source type; Android (the later, cleaner build) never had it. It is no longer
  needed and should be removed.
- `qualityBand()` — defined in the desktop service but never called anywhere; pure dead
  code. Delete it.

The Android `rankForDisplay` (phrase hits → BM25) is the canonical ranking design.
The desktop should be brought in line with it as part of this consolidation, not the
other way around.

The migration task here is to:
1. Delete the two local model classes and update all imports.
2. Delete `sourcePriority()` and `qualityBand()` from `LocalCorpusSearchService`.
3. Replace the inline `rankForDisplay` with a call to `SearchCore.rankForDisplay`.
4. Replace all other inline shared methods with calls to `SearchCore.*`.
5. Leave the AI-specific features (inference, semantic fallback, reranking, debug
   logging, retry logic) untouched in `LocalCorpusSearchService`.

**Effort estimate: 1–2 days** (mostly mechanical + integration smoke test). Risk
drops to **low–medium** now that the ranking divergence turns out to be dead code.

---

### BahaiResearchA (Android, 649-line LocalCorpusSearchService)

This project has **not been wired to Common at all yet**. Every model class is a local copy:

| File | Action needed |
|---|---|
| `corpus/CorpusSearchHit.java` | Delete; use `com.bahairesearch.common.model.CorpusSearchHit` |
| `model/QuoteResult.java` | Delete; use Common |
| `model/ResearchReport.java` | Delete; use Common |
| `corpus/LocalCorpusSearchService.java` (649 lines) | Refactor to delegate to SearchCore |

Two build changes are also required:
1. Add `bahai-search-common-1.0.0.jar` as a Gradle dependency (local AAR or Maven local repo).
2. Because Android's minimum Java level is 11 (already set in `build.gradle`), no
   desugaring issues are expected.

The Android `LocalCorpusSearchService` is simpler than the desktop version — no AI
integration, simpler ranking (phrase hits → BM25 only). It maps almost 1:1 to what
SearchCore already provides, making this the **lower-risk** migration.

**Effort estimate: 1–2 days** including the Gradle wiring, import updates, and a
test run on the emulator.

---

## One Gap in SearchCore That Needs Closing

`CorpusSearchHit` sits in `com.bahairesearch.common.model` in Common, but the Android
project currently stores it in `com.bahairesearch.android.corpus`. When BahaiResearchA
is wired up, all internal references to `CorpusSearchHit` in its `LocalCorpusSearchService`
must be updated to the Common package. This is mechanical but needs a careful pass —
there is no functional difference between the two classes.

---

## Structural Difference That Will NOT Be Consolidated

The DB access layer is and should remain split:

| Concern | BahaiResearch | BahaiResearchA |
|---|---|---|
| JDBC driver | SQLite JDBC 3.46 (Maven) | Requery sqlite-android 3.49 |
| Connection factory | `CorpusConnectionFactory` (JDBC `Connection`) | `DatabaseHelper` (Android `SQLiteDatabase`) |
| Query execution | `PreparedStatement` / `ResultSet` | `Cursor` |

These are irreconcilable platform types. The right boundary is already drawn: SearchCore
is pure logic; DB access stays in each project. Do not attempt to abstract the DB layer
into Common — it would require an interface + two implementations and add complexity for
no user-visible benefit.

---

## Recommended Migration Order

1. **Write unit tests for SearchCore.java first** (~half a day).
   There are currently no tests. Before either consumer migrates, you need a baseline
   that proves SearchCore's behavior matches what both projects expect. Focus on
   `toFtsQueryNear`, `buildAndQuery`, `extractFtsTokens`, `normalizeForMatch`, and the
   boilerplate filter. This is the highest-leverage investment in the whole project.

2. **Migrate BahaiResearchA** (lower risk, simpler code).
   - Add Gradle dependency on Common.
   - Delete three local model classes, fix imports.
   - Replace inline implementations in `LocalCorpusSearchService` with `SearchCore.*` calls.
   - Smoke test on emulator.

3. **Migrate BahaiResearch** (higher risk, more features to preserve).
   - Delete two local model classes, fix imports.
   - Replace inline implementations in `LocalCorpusSearchService` with `SearchCore.*` calls.
   - Leave all desktop-only features untouched.
   - Run integration test against local corpus.

4. **Reconcile any behavioral deltas discovered during migration** (buffer: ~half a day).
   The two implementations have evolved independently. Expect at least one subtle
   difference in boilerplate thresholds, noise token lists, or ranking tie-breaking
   that needs to be resolved in SearchCore before both consumers can use it cleanly.

---

## Effort Summary

| Task | Estimate | Risk |
|---|---|---|
| Unit tests for SearchCore.java | 0.5 day | Low |
| Migrate BahaiResearchA | 1–2 days | Low–Medium |
| Migrate BahaiResearch | 1–2 days | Medium |
| Reconcile divergence + regression buffer | 0.5–1 day | Medium |
| **Total** | **3–5.5 days** | |

---

## Honest Assessment

The architecture is correct and the hard design decisions were already made well. The
remaining work is largely mechanical — delete duplicates, update imports, replace inline
methods with SearchCore calls — but "mechanical" does not mean "fast." The 966-line
desktop service has subtle ranking behavior (quality bands, UHJ priority) that SearchCore
does not yet encode. You will hit at least one place where Common's implementation
diverges from what the desktop service currently does and you will have to decide which
behavior is canonical.

The biggest risk is **not building tests before migrating**. Without them, you cannot
tell whether a query that used to return "Unity of God passages" still does after the
refactor. Invest the half-day in unit tests first; it will pay back immediately during
the BahaiResearchA migration and again during the BahaiResearch migration.

The overall picture is cleaner than initially assessed. The two ranking features flagged
as divergences (`sourcePriority`, `qualityBand`) are not real divergences — one is dead
code and one is an abandoned experiment. The Android app, built later, reflects the
intended design. The only genuine feature gap between the two projects is Gemini AI
support, which is correctly desktop-only and stays out of Common.

The consolidation is worth doing. Once complete, any future improvement to query
building, noise filtering, boilerplate detection, or ranking logic lands in one place
and both platforms benefit automatically.
