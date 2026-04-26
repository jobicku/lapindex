# Lapindex Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an IntelliJ/Android Studio plugin that provides Go-To-Declaration navigation and error highlighting for `remoteString("key")` calls against configurable JSON language files.

**Architecture:** In-memory cache (`LapindexJsonIndex`) parses configured JSON files via IntelliJ JSON PSI on project open. A `PsiReferenceContributor` wires references on `KtStringTemplateExpression` inside `remoteString(...)` to `JsonProperty` PSI nodes. A `LocalInspectionTool` highlights keys absent from the cache. Android build variant detection (reflection-guarded) selects the right file when multiple source-set copies exist.

**Tech Stack:** Kotlin, IntelliJ Platform 2025.3.1 (build 253+), IntelliJ Platform Gradle Plugin v2, bundled plugins: `org.jetbrains.kotlin` (Kotlin PSI), `com.intellij.json` (JSON PSI).

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `build.gradle.kts` | Modify | Add bundled plugin dependencies |
| `src/main/resources/META-INF/plugin.xml` | Modify | Register all extensions |
| `src/main/kotlin/MyToolWindowFactory.kt` | Delete | Remove template |
| `src/main/kotlin/MyMessageBundle.kt` | Delete | Remove template |
| `src/main/resources/messages/MyMessageBundle.properties` | Delete | Remove template |
| `src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettings.kt` | Create | Persistent state: list of JSON file paths |
| `src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettingsConfigurable.kt` | Create | Settings UI: Settings → Tools → Lapindex |
| `src/main/kotlin/ru/jobick/lapindex/index/JsonPropertyLocation.kt` | Create | Data class: VirtualFile + JsonProperty PSI node |
| `src/main/kotlin/ru/jobick/lapindex/index/LapindexJsonIndex.kt` | Create | Project service: in-memory cache, parse, find, invalidate |
| `src/main/kotlin/ru/jobick/lapindex/index/LapindexIndexStartupActivity.kt` | Create | Trigger cache load on project open |
| `src/main/kotlin/ru/jobick/lapindex/util/RemoteStringUtil.kt` | Create | PSI helpers: isRemoteStringKey, getKeyText |
| `src/main/kotlin/ru/jobick/lapindex/reference/RemoteStringReference.kt` | Create | PsiReferenceBase: key string → JsonProperty |
| `src/main/kotlin/ru/jobick/lapindex/reference/RemoteStringReferenceContributor.kt` | Create | Registers reference provider for KtStringTemplateExpression |
| `src/main/kotlin/ru/jobick/lapindex/inspection/RemoteStringKeyInspection.kt` | Create | Highlights unknown keys with ERROR severity |
| `src/main/kotlin/ru/jobick/lapindex/android/ActiveVariantResolver.kt` | Create | Reads active build variant via reflection on AndroidFacet |
| `src/test/kotlin/ru/jobick/lapindex/util/RemoteStringUtilTest.kt` | Create | Unit tests for PSI helpers |
| `src/test/kotlin/ru/jobick/lapindex/reference/RemoteStringReferenceTest.kt` | Create | Integration tests for navigation |
| `src/test/kotlin/ru/jobick/lapindex/inspection/RemoteStringKeyInspectionTest.kt` | Create | Tests for error highlighting |

---

### Task 1: Project Setup

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Delete: `src/main/kotlin/MyToolWindowFactory.kt`
- Delete: `src/main/kotlin/MyMessageBundle.kt`
- Delete: `src/main/resources/messages/MyMessageBundle.properties`

- [ ] **Step 1: Update `build.gradle.kts` — add bundled plugins**

Replace the `dependencies` block:

```kotlin
dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.json")
    }
}
```

- [ ] **Step 2: Replace `plugin.xml` with clean version**

Full file content:

```xml
<idea-plugin>
    <id>ru.jobick.lapindex</id>
    <name>Lapindex</name>
    <vendor url="https://github.com/jobick">jobick</vendor>
    <description><![CDATA[
        Navigation and validation for remote string keys.<br/>
        Cmd+Click on a <code>remoteString("key")</code> argument navigates to the JSON definition.
        Unknown keys are highlighted as errors.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.kotlin</depends>

    <extensions defaultExtensionNs="com.intellij">
    </extensions>
</idea-plugin>
```

- [ ] **Step 3: Delete template files**

```bash
rm src/main/kotlin/MyToolWindowFactory.kt
rm src/main/kotlin/MyMessageBundle.kt
rm src/main/resources/messages/MyMessageBundle.properties
```

- [ ] **Step 4: Create package directories**

```bash
mkdir -p src/main/kotlin/ru/jobick/lapindex/{settings,index,util,reference,inspection,android}
mkdir -p src/test/kotlin/ru/jobick/lapindex/{util,reference,inspection}
```

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts src/main/resources/META-INF/plugin.xml
git commit -m "chore: add bundled plugin deps, replace plugin.xml, remove template code"
```

---

### Task 2: Settings Model

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettings.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create `LapindexSettings.kt`**

```kotlin
package ru.jobick.lapindex.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@State(name = "LapindexSettings", storages = [Storage("lapindex.xml")])
@Service(Service.Level.PROJECT)
class LapindexSettings : PersistentStateComponent<LapindexSettings.State> {

    data class State(var jsonFilePaths: MutableList<String> = mutableListOf())

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    var jsonFilePaths: MutableList<String>
        get() = myState.jsonFilePaths
        set(value) { myState.jsonFilePaths = value }

    companion object {
        fun getInstance(project: Project): LapindexSettings = project.service()
    }
}
```

- [ ] **Step 2: Register in `plugin.xml`**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<projectService serviceImplementation="ru.jobick.lapindex.settings.LapindexSettings"/>
```

- [ ] **Step 3: Build**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettings.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add LapindexSettings persistent project service"
```

---

### Task 3: RemoteStringUtil + Tests

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/util/RemoteStringUtil.kt`
- Create: `src/test/kotlin/ru/jobick/lapindex/util/RemoteStringUtilTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/ru/jobick/lapindex/util/RemoteStringUtilTest.kt`:

```kotlin
package ru.jobick.lapindex.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class RemoteStringUtilTest : BasePlatformTestCase() {

    private fun stringAt(code: String, marker: String): KtStringTemplateExpression {
        val file = myFixture.configureByText("Test.kt", code)
        val offset = code.indexOf(marker) + 1
        return file.findElementAt(offset)?.parent as KtStringTemplateExpression
    }

    fun `test isRemoteStringKey true for first arg`() {
        val expr = stringAt("""fun f() { remoteString("a.b") }""", "\"a.b\"")
        assertTrue(RemoteStringUtil.isRemoteStringKey(expr))
    }

    fun `test isRemoteStringKey false for non-remoteString call`() {
        val expr = stringAt("""fun f() { other("a.b") }""", "\"a.b\"")
        assertFalse(RemoteStringUtil.isRemoteStringKey(expr))
    }

    fun `test isRemoteStringKey false for second argument`() {
        val expr = stringAt("""fun f() { remoteString("k", "second") }""", "\"second\"")
        assertFalse(RemoteStringUtil.isRemoteStringKey(expr))
    }

    fun `test isRemoteStringKey false for string with interpolation`() {
        val x = "\$x"
        val expr = stringAt("""fun f() { val x=1; remoteString("k-$x") }""", "\"k-")
        assertFalse(RemoteStringUtil.isRemoteStringKey(expr))
    }

    fun `test getKeyText returns text for plain string`() {
        val expr = stringAt("""fun f() { remoteString("prefix.screen.key") }""", "\"prefix")
        assertEquals("prefix.screen.key", RemoteStringUtil.getKeyText(expr))
    }

    fun `test getKeyText returns null for interpolated string`() {
        val x = "\$x"
        val expr = stringAt("""fun f() { val x=1; remoteString("k-$x") }""", "\"k-")
        assertNull(RemoteStringUtil.getKeyText(expr))
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew test --tests "ru.jobick.lapindex.util.RemoteStringUtilTest" 2>&1 | tail -20
```

Expected: error `Unresolved reference: RemoteStringUtil`

- [ ] **Step 3: Create `RemoteStringUtil.kt`**

```kotlin
package ru.jobick.lapindex.util

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

object RemoteStringUtil {

    fun isRemoteStringKey(expr: KtStringTemplateExpression): Boolean {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return false
        val arg = expr.parent as? KtValueArgument ?: return false
        val argList = arg.parent as? KtValueArgumentList ?: return false
        val call = argList.parent as? KtCallExpression ?: return false
        return call.calleeExpression?.text == "remoteString"
            && argList.arguments.firstOrNull() == arg
    }

    fun getKeyText(expr: KtStringTemplateExpression): String? {
        if (expr.entries.any { it !is KtLiteralStringTemplateEntry }) return null
        return expr.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
    }
}
```

- [ ] **Step 4: Run — expect all pass**

```bash
./gradlew test --tests "ru.jobick.lapindex.util.RemoteStringUtilTest"
```

Expected: `BUILD SUCCESSFUL`, 6 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/util/RemoteStringUtil.kt \
        src/test/kotlin/ru/jobick/lapindex/util/RemoteStringUtilTest.kt
git commit -m "feat: add RemoteStringUtil PSI helpers with tests"
```

---

### Task 4: Android Variant Resolver

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/android/ActiveVariantResolver.kt`

Must be created before `LapindexJsonIndex` because the index imports it. No Android plugin compile dependency — all Android class access is via reflection with exception guards.

- [ ] **Step 1: Create `ActiveVariantResolver.kt`**

```kotlin
package ru.jobick.lapindex.android

import com.intellij.openapi.module.Module

object ActiveVariantResolver {

    fun getActiveSourceSetNames(module: Module): List<String> {
        return try {
            val facetClass = Class.forName("com.android.tools.idea.facet.AndroidFacet")
            val facet = facetClass.getMethod("getInstance", Module::class.java)
                .invoke(null, module) ?: return emptyList()
            val modelClass = Class.forName(
                "com.android.tools.idea.gradle.project.model.GradleAndroidModel"
            )
            val model = modelClass.getMethod("get", facetClass)
                .invoke(null, facet) ?: return emptyList()
            val variantName = modelClass.getMethod("getSelectedVariantName")
                .invoke(model) as? String ?: return emptyList()
            decompose(variantName)
        } catch (_: ClassNotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // "googleDebug" → ["googleDebug", "google", "debug", "main"]
    private fun decompose(variantName: String): List<String> {
        val parts = variantName.split(Regex("(?=[A-Z])")).map { it.lowercase() }
        return buildList {
            add(variantName)
            addAll(parts)
            add("main")
        }.distinct()
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/android/ActiveVariantResolver.kt
git commit -m "feat: add ActiveVariantResolver for Android build variant detection"
```

---

### Task 5: JSON Index

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/index/JsonPropertyLocation.kt`
- Create: `src/main/kotlin/ru/jobick/lapindex/index/LapindexJsonIndex.kt`
- Create: `src/main/kotlin/ru/jobick/lapindex/index/LapindexIndexStartupActivity.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

The index is tested indirectly through reference and inspection tests in Tasks 6 and 7. `refreshBlocking()` is exposed for synchronous use in tests to avoid flaky `Thread.sleep`.

- [ ] **Step 1: Create `JsonPropertyLocation.kt`**

```kotlin
package ru.jobick.lapindex.index

import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.vfs.VirtualFile

data class JsonPropertyLocation(
    val file: VirtualFile,
    val property: JsonProperty
)
```

- [ ] **Step 2: Create `LapindexJsonIndex.kt`**

```kotlin
package ru.jobick.lapindex.index

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import ru.jobick.lapindex.android.ActiveVariantResolver
import ru.jobick.lapindex.settings.LapindexSettings
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class LapindexJsonIndex(private val project: Project) {

    private val cache = ConcurrentHashMap<String, MutableList<JsonPropertyLocation>>()
    private val log = Logger.getInstance(LapindexJsonIndex::class.java)

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val configured = resolvedPaths()
                    if (events.any { configured.contains(it.file?.path) }) refresh()
                }
            }
        )
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread { refreshBlocking() }
    }

    internal fun refreshBlocking() {
        cache.clear()
        val paths = LapindexSettings.getInstance(project).jsonFilePaths.toList()
        for (path in paths) {
            val vFile = resolveVirtualFile(path) ?: continue
            try {
                ApplicationManager.getApplication().runReadAction { loadFile(vFile) }
            } catch (e: Exception) {
                log.warn("Failed to load: $path", e)
            }
        }
    }

    fun find(key: String, module: Module?): JsonPropertyLocation? {
        val locations = cache[key] ?: return null
        if (locations.size == 1 || module == null) return locations.first()
        val activeSourceSets = ActiveVariantResolver.getActiveSourceSetNames(module)
        for (sourceSet in activeSourceSets) {
            val match = locations.firstOrNull { it.file.path.contains("/$sourceSet/") }
            if (match != null) return match
        }
        return locations.first()
    }

    fun allKeys(): Set<String> = cache.keys.toSet()

    private fun loadFile(vFile: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(vFile) as? JsonFile ?: return
        val obj = psiFile.topLevelValue as? JsonObject ?: return
        for (property in obj.propertyList) {
            cache.getOrPut(property.name) { mutableListOf() }
                .add(JsonPropertyLocation(vFile, property))
        }
    }

    private fun resolveVirtualFile(path: String): VirtualFile? {
        val absPath = if (File(path).isAbsolute) path else "${project.basePath}/$path"
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(absPath))
    }

    private fun resolvedPaths(): Set<String> {
        val base = project.basePath ?: return emptySet()
        return LapindexSettings.getInstance(project).jsonFilePaths.mapTo(mutableSetOf()) { path ->
            if (File(path).isAbsolute) path else "$base/$path"
        }
    }

    companion object {
        fun getInstance(project: Project): LapindexJsonIndex = project.service()
    }
}
```

- [ ] **Step 3: Create `LapindexIndexStartupActivity.kt`**

```kotlin
package ru.jobick.lapindex.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LapindexIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LapindexJsonIndex.getInstance(project).refresh()
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`**

Add to `<extensions defaultExtensionNs="com.intellij">`:

```xml
<projectService serviceImplementation="ru.jobick.lapindex.index.LapindexJsonIndex"/>
<postStartupActivity implementation="ru.jobick.lapindex.index.LapindexIndexStartupActivity"/>
```

- [ ] **Step 5: Build**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/index/ \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add LapindexJsonIndex project service with in-memory cache"
```

---

### Task 6: PsiReference + Tests

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/reference/RemoteStringReference.kt`
- Create: `src/main/kotlin/ru/jobick/lapindex/reference/RemoteStringReferenceContributor.kt`
- Create: `src/test/kotlin/ru/jobick/lapindex/reference/RemoteStringReferenceTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/ru/jobick/lapindex/reference/RemoteStringReferenceTest.kt`:

```kotlin
package ru.jobick.lapindex.reference

import com.intellij.json.psi.JsonProperty
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.settings.LapindexSettings

class RemoteStringReferenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val json = myFixture.addFileToProject(
            "strings.json",
            """{"home.title": "Home", "home.subtitle": "Welcome"}"""
        )
        LapindexSettings.getInstance(project).jsonFilePaths =
            mutableListOf(json.virtualFile.path)
        LapindexJsonIndex.getInstance(project).refreshBlocking()
    }

    fun `test known key resolves to JsonProperty`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { remoteString("home.title") }"""
        )
        val offset = myFixture.file.text.indexOf("\"home.title\"") + 1
        val resolved = myFixture.file.findReferenceAt(offset)?.resolve()
        assertNotNull(resolved)
        assertInstanceOf(resolved, JsonProperty::class.java)
        assertEquals("home.title", (resolved as JsonProperty).name)
    }

    fun `test unknown key resolves to null`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { remoteString("missing.key") }"""
        )
        val offset = myFixture.file.text.indexOf("\"missing.key\"") + 1
        val resolved = myFixture.file.findReferenceAt(offset)?.resolve()
        assertNull(resolved)
    }

    fun `test non-remoteString call has no navigable reference`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { otherMethod("home.title") }"""
        )
        val offset = myFixture.file.text.indexOf("\"home.title\"") + 1
        val resolved = myFixture.file.findReferenceAt(offset)?.resolve()
        assertFalse(resolved is JsonProperty)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew test --tests "ru.jobick.lapindex.reference.RemoteStringReferenceTest" 2>&1 | tail -10
```

Expected: error `Unresolved reference: RemoteStringReference`

- [ ] **Step 3: Create `RemoteStringReference.kt`**

```kotlin
package ru.jobick.lapindex.reference

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringReference(element: KtStringTemplateExpression) :
    PsiReferenceBase<KtStringTemplateExpression>(element, true) {

    override fun resolve(): PsiElement? {
        val key = RemoteStringUtil.getKeyText(element) ?: return null
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        return LapindexJsonIndex.getInstance(element.project).find(key, module)?.property
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
```

- [ ] **Step 4: Create `RemoteStringReferenceContributor.kt`**

```kotlin
package ru.jobick.lapindex.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val expr = element as? KtStringTemplateExpression
                        ?: return PsiReference.EMPTY_ARRAY
                    if (!RemoteStringUtil.isRemoteStringKey(expr)) return PsiReference.EMPTY_ARRAY
                    return arrayOf(RemoteStringReference(expr))
                }
            },
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
```

- [ ] **Step 5: Register in `plugin.xml`**

Add to `<extensions defaultExtensionNs="com.intellij">`:

```xml
<psi.referenceContributor language="Kotlin"
    implementation="ru.jobick.lapindex.reference.RemoteStringReferenceContributor"/>
```

- [ ] **Step 6: Run — expect all pass**

```bash
./gradlew test --tests "ru.jobick.lapindex.reference.RemoteStringReferenceTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/reference/ \
        src/test/kotlin/ru/jobick/lapindex/reference/ \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add PsiReference contributor for remoteString key navigation"
```

---

### Task 7: Inspection + Tests

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/inspection/RemoteStringKeyInspection.kt`
- Create: `src/test/kotlin/ru/jobick/lapindex/inspection/RemoteStringKeyInspectionTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/ru/jobick/lapindex/inspection/RemoteStringKeyInspectionTest.kt`:

```kotlin
package ru.jobick.lapindex.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.settings.LapindexSettings

class RemoteStringKeyInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val json = myFixture.addFileToProject(
            "strings.json",
            """{"known.key": "Known Value"}"""
        )
        LapindexSettings.getInstance(project).jsonFilePaths =
            mutableListOf(json.virtualFile.path)
        LapindexJsonIndex.getInstance(project).refreshBlocking()
        myFixture.enableInspections(RemoteStringKeyInspection::class.java)
    }

    fun `test no error for known key`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { remoteString("known.key") }"""
        )
        val errors = myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("Expected no errors", errors.isEmpty())
    }

    fun `test error for unknown key`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { remoteString("unknown.key") }"""
        )
        val errors = myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].description.contains("unknown.key"))
    }

    fun `test no error for non-remoteString call`() {
        myFixture.configureByText(
            "Test.kt",
            """fun f() { otherMethod("unknown.key") }"""
        )
        val errors = myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("Expected no errors", errors.isEmpty())
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
./gradlew test --tests "ru.jobick.lapindex.inspection.RemoteStringKeyInspectionTest" 2>&1 | tail -10
```

Expected: error `Unresolved reference: RemoteStringKeyInspection`

- [ ] **Step 3: Create `RemoteStringKeyInspection.kt`**

```kotlin
package ru.jobick.lapindex.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import ru.jobick.lapindex.index.LapindexJsonIndex
import ru.jobick.lapindex.util.RemoteStringUtil

class RemoteStringKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                if (!RemoteStringUtil.isRemoteStringKey(expression)) return
                val key = RemoteStringUtil.getKeyText(expression) ?: return
                val module = ModuleUtilCore.findModuleForPsiElement(expression)
                if (LapindexJsonIndex.getInstance(expression.project).find(key, module) == null) {
                    holder.registerProblem(
                        expression,
                        "Unknown remote string key: '$key'",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Register in `plugin.xml`**

Add to `<extensions defaultExtensionNs="com.intellij">`:

```xml
<localInspection language="Kotlin"
    implementationClass="ru.jobick.lapindex.inspection.RemoteStringKeyInspection"
    displayName="Unknown remote string key"
    groupName="Lapindex"
    enabledByDefault="true"
    level="ERROR"/>
```

- [ ] **Step 5: Run — expect all pass**

```bash
./gradlew test --tests "ru.jobick.lapindex.inspection.RemoteStringKeyInspectionTest"
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass

- [ ] **Step 6: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/inspection/ \
        src/test/kotlin/ru/jobick/lapindex/inspection/ \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add RemoteStringKeyInspection for unknown key error highlighting"
```

---

### Task 8: Settings Configurable UI

**Files:**
- Create: `src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettingsConfigurable.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create `LapindexSettingsConfigurable.kt`**

```kotlin
package ru.jobick.lapindex.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import ru.jobick.lapindex.index.LapindexJsonIndex
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class LapindexSettingsConfigurable(private val project: Project) : Configurable {

    private var pathsModel = DefaultListModel<String>()
    private var list = JBList(pathsModel)

    override fun getDisplayName(): String = "Lapindex"

    override fun createComponent(): JComponent {
        pathsModel = DefaultListModel()
        list = JBList(pathsModel)
        LapindexSettings.getInstance(project).jsonFilePaths.forEach { pathsModel.addElement(it) }

        val toolbar = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return@setAddAction
                val basePath = project.basePath
                val path = if (basePath != null && chosen.path.startsWith(basePath)) {
                    chosen.path.removePrefix("$basePath/")
                } else {
                    chosen.path
                }
                pathsModel.addElement(path)
            }
            .setRemoveAction {
                list.selectedIndices.reversed().forEach { pathsModel.remove(it) }
            }
            .createPanel()

        return JPanel(BorderLayout()).apply {
            add(JLabel("JSON file paths:"), BorderLayout.NORTH)
            add(toolbar, BorderLayout.CENTER)
        }
    }

    override fun isModified(): Boolean {
        val current = LapindexSettings.getInstance(project).jsonFilePaths
        val ui = (0 until pathsModel.size).map { pathsModel.getElementAt(it) }
        return current != ui
    }

    override fun apply() {
        val ui = (0 until pathsModel.size).map { pathsModel.getElementAt(it) }
        LapindexSettings.getInstance(project).jsonFilePaths = ui.toMutableList()
        LapindexJsonIndex.getInstance(project).refresh()
    }

    override fun reset() {
        pathsModel.clear()
        LapindexSettings.getInstance(project).jsonFilePaths.forEach { pathsModel.addElement(it) }
    }
}
```

- [ ] **Step 2: Register in `plugin.xml`**

Add to `<extensions defaultExtensionNs="com.intellij">`:

```xml
<projectConfigurable
    parentId="tools"
    instance="ru.jobick.lapindex.settings.LapindexSettingsConfigurable"
    id="ru.jobick.lapindex.settings.LapindexSettingsConfigurable"
    displayName="Lapindex"/>
```

- [ ] **Step 3: Build**

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/ru/jobick/lapindex/settings/LapindexSettingsConfigurable.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add Settings UI page for Lapindex JSON file path configuration"
```

---

### Task 9: Smoke Test in Sandbox IDE

- [ ] **Step 1: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Launch plugin in sandbox IDE**

```bash
./gradlew runIde
```

A sandboxed IntelliJ IDEA instance opens with Lapindex installed.

- [ ] **Step 3: Create test project and files in sandbox**

In the sandbox IDE:
1. File → New Project → Kotlin (any template)
2. Create `strings.json` in project root:
   ```json
   {
     "home.title": "Home",
     "home.subtitle": "Welcome",
     "home.button": "Click me"
   }
   ```
3. Create `src/main/kotlin/Main.kt`:
   ```kotlin
   fun main() {
       remoteString("home.title")
       remoteString("home.subtitle")
       remoteString("missing.key")
   }
   ```

- [ ] **Step 4: Configure plugin settings**

Settings → Tools → Lapindex → Add → select `strings.json` → Apply

- [ ] **Step 5: Verify navigation**

Cmd+Click on `"home.title"` → cursor jumps to the `"home.title"` line in `strings.json`

- [ ] **Step 6: Verify error highlighting**

`"missing.key"` should have a red underline. Hovering shows: `Unknown remote string key: 'missing.key'`

- [ ] **Step 7: Commit any fixes**

```bash
git add -A
git commit -m "fix: <describe fixes found during smoke test>"
```
