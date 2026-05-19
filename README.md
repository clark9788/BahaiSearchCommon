# BahaiSearchCommon

Shared Java library containing the core FTS5 search logic used by both the
desktop (BahaiResearch) and Android (BahaiResearchA) projects. Keeping this
logic in one place ensures both platforms behave identically — the NEAR
proximity query, BM25 ranking, boilerplate filtering, and phrase scoring are
all defined here and tested here.

## What lives here

- `SearchCore.java` — all pure search logic (query building, ranking, filtering)
- `CorpusSearchHit`, `QuoteResult`, `ResearchReport` — shared model classes
- Unit tests for every public method in SearchCore

## How to propagate a change to both platforms

### 1. Edit and test in BahaiSearchCommon

```
cd D:\AI-Python\BahaiSearchCommon
mvn test
```

Fix anything that breaks. Add a new test for the changed behavior if one does
not already exist.

### 2. Install to the local Maven repository (covers Desktop)

```
mvn install
```

This builds the JAR, runs tests, and installs it into the local Maven cache
(`~/.m2/repository/com/bahairesearch/bahai-search-common/1.0.0/`). The
desktop project resolves the dependency from there automatically on the next
build.

### 3. Android is automatic (covers Android)

BahaiResearchA uses a Gradle composite build — `includeBuild('../BahaiSearchCommon')`
in its `settings.gradle` tells Gradle to build BahaiSearchCommon from source
directly. No JAR copy required. A Gradle sync in Android Studio picks up any
change automatically.

### 4. Test both platforms

- **Desktop:** `mvn javafx:run` in `D:\AI-Python\BahaiResearch`
- **Android:** Build and run via Android Studio

## Notes

- Android has no Gemini AI support — that is the only intentional behavioral
  difference between the two platforms.
- The NEAR query with a third token as an AND (`NEAR(t1 t2, 15) AND t3`) is
  the canonical design defined in SearchCore and used by both platforms.
- BahaiSearchCommon has both a `pom.xml` (used by the desktop Maven workflow)
  and a `build.gradle` (used by the Android composite build). The two coexist
  without conflict.
