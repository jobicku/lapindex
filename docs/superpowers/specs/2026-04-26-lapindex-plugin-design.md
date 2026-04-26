# Lapindex Plugin Design

**Date:** 2026-04-26  
**Project:** `ru.jobick.lapindex`  
**Platform:** IntelliJ Platform 2025.3.1 (build 253+), Android Studio

## Overview

Plugin that indexes keys from a Language API JSON file and provides:
- **Go To Declaration** (Cmd+Click) from `remoteString("key")` call to the matching entry in the JSON file
- **Error highlighting** for unknown keys (not found in any configured JSON file)

## Requirements

| # | Requirement |
|---|---|
| 1 | Navigate from `remoteString("key")` string literal to `JsonProperty` in configured JSON file |
| 2 | Red underline on string literal if key not found in any configured JSON file |
| 3 | JSON file paths configured in Settings (list of paths, project-relative or absolute) |
| 4 | JSON format: standard object `{"key": "value", ...}` |
| 5 | Method name `remoteString` is fixed (not configurable) |
| 6 | Multiple JSON files supported (different build flavors with same keys) |
| 7 | Navigation targets the file matching the active Android build variant/source set |
| 8 | Kotlin source files only |

## Architecture

### Components

```
ru.jobick.lapindex/
  settings/
    LapindexSettings              — PersistentStateComponent, stores List<String> of JSON paths
    LapindexSettingsConfigurable  — Settings UI page (Settings → Tools → Lapindex)
  index/
    LapindexJsonIndex             — Project service, in-memory cache of parsed JSON keys
    JsonPropertyLocation          — Data class: VirtualFile + JsonProperty PSI node
  reference/
    RemoteStringReferenceContributor  — PsiReferenceContributor, wires references on Kotlin string literals
    RemoteStringReference             — PsiReferenceBase, resolves key → JsonProperty
  inspection/
    RemoteStringKeyInspection     — LocalInspectionTool, highlights unknown keys
  android/
    ActiveVariantResolver         — Reads active build variant via AndroidFacet
  util/
    RemoteStringUtil              — Shared: isRemoteStringKey(KtStringTemplateExpression): Boolean
```

### Component Responsibilities

**`LapindexSettings`**
- Persists `jsonFilePaths: List<String>` via `@State` / `PersistentStateComponent`
- Project-level (not application-level)

**`LapindexSettingsConfigurable`**
- Settings page under `Settings → Tools → Lapindex`
- UI: editable list with Add / Remove / Browse (file chooser) buttons
- On Apply: calls `LapindexJsonIndex.refresh()`

**`LapindexJsonIndex`**
- Project service registered in `plugin.xml`
- Cache type: `Map<String, List<JsonPropertyLocation>>`
  - key = JSON property key string
  - value = all locations across all configured files (one per flavor/source set)
- Loads on project open via `ProjectActivity`
- Reloads on `LapindexSettings` change
- Invalidates per-file entries via `VirtualFileListener` watching configured files
- Parse errors (file not found, invalid JSON): skip file, log warning, rest of cache unaffected

**`RemoteStringUtil.isRemoteStringKey`**
```kotlin
fun isRemoteStringKey(expr: KtStringTemplateExpression): Boolean {
    val arg = expr.parent as? KtValueArgument ?: return false
    val argList = arg.parent as? KtValueArgumentList ?: return false
    val call = argList.parent as? KtCallExpression ?: return false
    return call.calleeExpression?.text == "remoteString"
        && argList.arguments.firstOrNull() == arg
}
```
Used by both `RemoteStringReferenceContributor` and `RemoteStringKeyInspection`.

**`RemoteStringReference`**
- Extends `PsiReferenceBase<KtStringTemplateExpression>`
- `resolve()`:
  1. Extract key text from element (strip surrounding quotes)
  2. Get module for element via `ModuleUtilCore.findModuleForPsiElement()`
  3. Call `LapindexJsonIndex.find(key, module)` → returns best-matching `JsonPropertyLocation`
  4. Return `JsonPropertyLocation.property` (the `JsonProperty` PSI node)
- `isSoft = true` (no error from reference itself; inspection handles missing keys separately)

**`RemoteStringKeyInspection`**
- Visitor visits `KtStringTemplateExpression`
- If `isRemoteStringKey(expr)` and `reference.resolve() == null`:
  - `holder.registerProblem(expr, "Unknown remote string key: '${key}'")`
  - Severity: `ProblemHighlightType.GENERIC_ERROR`

**`ActiveVariantResolver`**
- `getActiveSourceSetNames(module: Module): List<String>`
  1. `AndroidFacet.getInstance(module) ?: return emptyList()`
  2. Read `selectedVariantName` (e.g. `"googleDebug"`)
  3. Decompose into source sets: `["googleDebug", "google", "debug", "main"]`
  4. Return list

**`LapindexJsonIndex.find(key, module)`**
1. `locations = cache[key] ?: return null`
2. If `locations.size == 1`: return it directly
3. `activeSourceSets = ActiveVariantResolver.getActiveSourceSetNames(module)`
4. For each source set name, find first location whose `file.path` contains `"/$sourceSetName/"` → return it
5. Fallback: return `locations.first()`

## Data Flow

```
User: Cmd+Click on "some.key" in remoteString("some.key")
  → PsiReferenceContributor: detects KtStringTemplateExpression
  → isRemoteStringKey() check passes
  → RemoteStringReference.resolve()
      → LapindexJsonIndex.find("some.key", module)
          → cache lookup → List<JsonPropertyLocation>
          → ActiveVariantResolver.getActiveSourceSetNames(module)
          → filter by path match → JsonPropertyLocation
      → return JsonProperty PSI node
  → IDE: navigate to JsonProperty in JSON file
```

## Settings UI

Page path: `Settings → Tools → Lapindex`

```
JSON Files:
┌─────────────────────────────────────────────────┐
│ app/src/google/assets/strings.json              │
│ app/src/alternative/assets/strings.json         │
│                                                 │
└─────────────────────────────────────────────────┘
[Add...] [Remove]
```

Paths stored as strings. Resolved relative to project base dir at runtime.

## Plugin Configuration (plugin.xml)

```xml
<depends>com.intellij.modules.platform</depends>
<depends>com.intellij.modules.lang</depends>
<depends optional="true" config-file="lapindex-android.xml">org.jetbrains.android</depends>

<extensions defaultExtensionNs="com.intellij">
  <projectService serviceImplementation="ru.jobick.lapindex.index.LapindexJsonIndex"/>
  <projectConfigurable instance="ru.jobick.lapindex.settings.LapindexSettingsConfigurable"
                       displayName="Lapindex" parentId="tools"/>
  <psi.referenceContributor language="kotlin"
                             implementation="ru.jobick.lapindex.reference.RemoteStringReferenceContributor"/>
  <localInspection language="kotlin"
                   implementationClass="ru.jobick.lapindex.inspection.RemoteStringKeyInspection"
                   displayName="Unknown remote string key"
                   groupName="Lapindex"
                   enabledByDefault="true"
                   level="ERROR"/>
  <postStartupActivity implementation="ru.jobick.lapindex.index.LapindexIndexStartupActivity"/>
</extensions>
```

`lapindex-android.xml` — optional descriptor loaded only when Android plugin present:
```xml
<!-- enables ActiveVariantResolver code path -->
```

## Dependencies (build.gradle.kts)

```kotlin
intellijPlatform {
    bundledPlugin("com.intellij.java")          // for Java PSI utilities
    bundledPlugin("org.jetbrains.kotlin")       // for KtStringTemplateExpression etc.
    bundledPlugin("com.intellij.modules.json")  // for JsonFile, JsonProperty PSI
}
```

Android plugin dependency: optional, resolved at runtime via reflection guard in `ActiveVariantResolver`.

## Error Handling

| Scenario | Behavior |
|---|---|
| JSON file path in settings doesn't exist | Log warning, skip, other files unaffected |
| JSON file is not valid JSON | Log warning, skip |
| String literal contains template expression `"${var}"` | Skip (not a plain key) |
| Android plugin not installed | `ActiveVariantResolver` returns empty, fallback to first matching file |
| Module not found for PSI element | Skip variant resolution, use first match |

## Out of Scope

- Autocompletion of keys
- Inline hints showing values
- Find Usages of a key across the codebase
- Rename refactoring for keys
- Multiple method names (only `remoteString`)
