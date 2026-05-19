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

### 3. Copy the JAR to Android (covers Android)

```
copy target\bahai-search-common-1.0.0.jar D:\AI-Python\BahaiResearchA\app\libs\bahai-search-common-1.0.0.jar
```

Android Studio picks it up on the next Gradle sync — the `fileTree` dependency
in `app/build.gradle` handles this automatically. If this copy is skipped,
Android will silently run the old logic.

### 4. Test both platforms

- **Desktop:** `mvn javafx:run` in `D:\AI-Python\BahaiResearch`
- **Android:** Build and run via Android Studio

## Potential improvement: Gradle multi-project build

The main friction in the current workflow is the manual JAR copy to Android
after every change. Migrating to a Gradle multi-project (or composite) build
would eliminate this entirely.

With a single root `settings.gradle` that includes all three projects as
subprojects, each consumer declares BahaiSearchCommon as a project dependency:

```groovy
// BahaiResearchA/app/build.gradle
implementation project(':BahaiSearchCommon')
```

Gradle then resolves the common library automatically at build time — no
`mvn install`, no JAR copy. A change in SearchCore.java is picked up on the
next build of either consumer.

**Effort estimate:**

| Task | Effort |
|---|---|
| Convert BahaiSearchCommon from Maven to Gradle | Low — simple pure-Java library |
| Convert BahaiResearch (Desktop) from Maven to Gradle | Moderate — JavaFX Gradle plugin exists but the POM has several moving parts |
| Wire all three under one root `settings.gradle` | Low once the above are done |

BahaiResearchA is already Gradle so Android needs no conversion.

The lowest-risk first move would be converting BahaiSearchCommon to Gradle and
wiring it into BahaiResearchA as a project dependency, leaving the desktop
Maven workflow untouched. That alone removes the JAR copy step for Android.

This migration is worth doing if BahaiSearchCommon changes frequently. If
changes are infrequent the current three-step manual process is simple enough
to leave as-is.

## Notes

- Android has no Gemini AI support — that is the only intentional behavioral
  difference between the two platforms.
- The NEAR query with a third token as an AND (`NEAR(t1 t2, 15) AND t3`) is
  the canonical design defined in SearchCore and used by both platforms.
