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
gradlew.bat test
```

Fix anything that breaks. Add a new test for the changed behavior if one does
not already exist.

### 2. Both platforms are automatic

Both BahaiResearch (desktop) and BahaiResearchA (Android) use Gradle composite
builds. Each declares `includeBuild('../BahaiSearchCommon')` in its
`settings.gradle`, so Gradle builds BahaiSearchCommon from source automatically
on every build. No install step, no JAR copy, no version mismatch risk.

### 3. Test both platforms

- **Desktop:** `gradlew.bat run` in `D:\AI-Python\BahaiResearch`
- **Android:** Build and run via Android Studio

## Gradle command reference

### BahaiSearchCommon

| Task | Command |
|---|---|
| Run tests | `gradlew.bat test` |

### BahaiResearch (Desktop)

| Task | Command |
|---|---|
| Run the app | `gradlew.bat run` |
| Build fat JAR | `gradlew.bat shadowJar` → `build\libs\BahaiResearch-1.3.0-SNAPSHOT-all.jar` |

### BahaiResearchA (Android)

| Task | Command |
|---|---|
| Build and run | Android Studio (Gradle sync picks up BahaiSearchCommon changes automatically) |

## Maven fallback (Desktop)

BahaiResearch still has a `pom.xml`. To fall back to Maven at any time:

```
mvn javafx:run          (run)
mvn -DskipTests package (fat JAR → target\)
```

BahaiSearchCommon also retains its `pom.xml` — run `mvn install` there first
so Maven can resolve it from `~/.m2`.

## Notes

- Android has no Gemini AI support — that is the only intentional behavioral
  difference between the two platforms.
- The NEAR query with a third token as an AND (`NEAR(t1 t2, 15) AND t3`) is
  the canonical design defined in SearchCore and used by both platforms.
- BahaiSearchCommon has both a `pom.xml` (Maven fallback) and a `build.gradle`
  (Gradle composite build). The two coexist without conflict.
