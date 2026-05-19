# BahaiSearchCommon

Shared Java library extracted from the duplicate code between:

- **[BahaiResearch](https://github.com/clark9788/baharesearch.git)** — Windows desktop app (JavaFX, Maven)
- **[BahaiResearchA](https://github.com/clark9788/BahaiResearchA.git)** — Android app (Gradle)

## Purpose

Produce a single `.jar` (`bahai-search-common.jar`) that both consuming projects depend on, eliminating ~600 lines of duplicated FTS5 search logic and data model classes.

## Architecture

```
com.bahairesearch.common
├── model/        → Data transfer objects (plain Java classes)
│   ├── CorpusSearchHit     → Raw FTS5 hit from the database
│   ├── QuoteResult         → Final passage presented to the UI
│   └── ResearchReport      → Search result container
└── search/       → Pure-logic search engine (no platform APIs)
    └── SearchCore          → FTS5 query building, tokenization, ranking, filtering
```

### What lives in the shared `.jar`

| Class | Responsibility |
|-------|---------------|
| `CorpusSearchHit` | 6-field data carrier: quote, author, title, locator, sourceUrl, score |
| `QuoteResult` | 5-field final output model: quote, author, bookTitle, paragraphOrPage, sourceUrl |
| `ResearchReport` | Container: summary string + `List<QuoteResult>` |
| `SearchCore` | Static methods: `toFtsQueryNear`, `toFtsQuery`, `toFtsQueryOr`, `extractFtsTokens`, `extractContentTerms`, `rankForDisplay`, `removeBoilerplateAndDuplicates`, `mergeHits`, `filterByRequestedAuthor`, `filterByRequestedBook`, `filterByContentTerms`, etc. |

### What stays per-platform

**Windows (`LocalCorpusSearchService`):**
- SQL execution via `sqlite-jdbc` (`Connection`, `PreparedStatement`)
- Gemini client (`GeminiClient`)
- Intent resolution and concept extraction (`inferEffectiveConceptTerms`)
- Corpus bootstrap, ingest, PDF parsing
- JavaFX UI

**Android (`LocalCorpusSearchService`):**
- SQL execution via `requery` (`SQLiteDatabase`, `Cursor`)
- `DatabaseHelper`, `buildHitsSql()`, `executeHitsQuery()`, `fetchPhraseHits()`, `findAdditionalBookScopedHits()`
- Android UI (`MainActivity`, `ResultsAdapter`)

## Compatibility

- **Target Java version:** 11 (`maven.compiler.release = 11`)
- **Runtime:** Pure Java SE standard library only — no external dependencies
- **Android:** Works on API 24+ (plain classes, not Java records)
- **Windows:** Works on Java 17+ (Java 11 bytecode is forward-compatible)

## Build

```bash
mvn clean package
```

Output: `target/bahai-search-common-1.0.0.jar`

## Consuming the .jar

### Windows (Maven)

In `BahaiResearch/pom.xml`:

```xml
<dependency>
    <groupId>com.bahairesearch</groupId>
    <artifactId>bahai-search-common</artifactId>
    <version>1.0.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/bahai-search-common-1.0.0.jar</systemPath>
</dependency>
```

Or run `mvn install:install-file` to install into the local Maven repository.

### Android (Gradle)

Copy the `.jar` to `BahaiResearchA/app/libs/` and add:

```groovy
dependencies {
    implementation files('libs/bahai-search-common-1.0.0.jar')
    // ... existing dependencies
}
```

## Migration Steps

### Phase 1 — Model classes only

1. Create `CorpusSearchHit`, `QuoteResult`, `ResearchReport` in `com.bahairesearch.common.model`
2. Build `.jar` and verify it compiles
3. Wire into both projects, delete duplicate model classes
4. Verify both projects compile and run

### Phase 2 — Core search logic

1. Extract shared static methods from Android `LocalCorpusSearchService` into `SearchCore`
2. The extracted methods must be **pure** — take inputs, return outputs, no database access
3. Build `.jar` and verify
4. Refactor Windows `LocalCorpusSearchService` to call `SearchCore` methods
5. Refactor Android `LocalCorpusSearchService` to call `SearchCore` methods
6. Delete duplicated method implementations from both projects
7. Verify identical search results on both platforms

## Key Constants

| Constant | Value |
|----------|-------|
| `NEAR_DISTANCE` | 15 tokens |
| `NEAR_BOOST_MULTIPLIER` | 1000.0 |
| `PHRASE_SCORE_THRESHOLD` | -99995.0 |
| `NOISE_TOKENS` | ~33 tokens (by/for/with/and/the/...) |
| `GENERIC_QUERY_TOKENS` | book/books/most/issue/issues |