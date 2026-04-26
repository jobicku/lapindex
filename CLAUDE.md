# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

IntelliJ/Android Studio plugin (Lapindex). Lets developers Cmd+Click on `remoteString("some.key")` call arguments in Kotlin code to navigate to the matching JSON entry. Also highlights unknown keys as errors.

The JSON file is a flat object: `{ "prefix.screen.key": "Display value", ... }`. Paths can be absolute or project-relative.

## Commands

```bash
# Build plugin
./gradlew buildPlugin

# Run IDE sandbox with plugin loaded (use the "Run IDE with Plugin" run config in IDEA, or:)
./gradlew runIde

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "ru.jobick.lapindex.SomeTest"

# Verify plugin (checks compatibility)
./gradlew verifyPlugin
```

The local IntelliJ SDK is hardcoded in `build.gradle.kts` as a `local()` path — update it if the cache path changes.

## Architecture

Data flow: JSON files → `LapindexJsonIndex` (in-memory cache) → `RemoteStringReference` (navigation) + `RemoteStringKeyInspection` (error highlighting).

### Key classes

| Class | Role |
|---|---|
| `LapindexJsonIndex` | Project-level service. Parses configured JSON files into `ConcurrentHashMap<key, List<JsonPropertyLocation>>`. Subscribes to VFS changes to auto-refresh when a watched file changes. |
| `LapindexIndexStartupActivity` | Triggers initial index build on project open. |
| `JsonPropertyLocation` | Holds `VirtualFile` + `JsonProperty` PSI element for a resolved key. |
| `RemoteStringUtil` | PSI pattern matcher. `isRemoteStringKey()` checks that a `KtStringTemplateExpression` is the first argument of a `remoteString(...)` call. |
| `RemoteStringReferenceContributor` | Registers `RemoteStringReference` for all `KtStringTemplateExpression` nodes that pass `RemoteStringUtil.isRemoteStringKey()`. |
| `RemoteStringReference` | `PsiReferenceBase` — `resolve()` looks up the key in `LapindexJsonIndex` and returns the `JsonProperty` PSI node. |
| `RemoteStringKeyInspection` | `LocalInspectionTool` — walks Kotlin PSI, flags keys absent from index as `GENERIC_ERROR`. |
| `LapindexSettings` | `PersistentStateComponent` stored in `.idea/lapindex.xml`. Holds `jsonFilePaths: MutableList<String>`. |
| `LapindexSettingsConfigurable` | Settings UI under **Tools → Lapindex**. Paths are stored relative to project root when possible. |
| `ActiveVariantResolver` | Resolves the active Android build variant via reflection (no hard dependency on Android plugin). Used when multiple JSON files contain the same key — picks the file whose path contains the active source set name. |

### Multi-file / variant resolution

`LapindexJsonIndex.find(key, module)` supports multiple JSON files mapping the same key. When there are duplicates and a `Module` is available, `ActiveVariantResolver` returns candidate source set names (e.g. `["debug", "main"]`) and the first file path containing one of those names wins. Falls back to the first registered entry.

### Plugin compatibility

- Target: IntelliJ 2025.3+ (build 253+), K2 Kotlin plugin mode declared in `plugin.xml`.
- Depends on bundled `org.jetbrains.kotlin` and `com.intellij.modules.json`.
- `AndroidFacet` / `GradleAndroidModel` are accessed via reflection so the plugin loads in non-Android IDEs too.
