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
