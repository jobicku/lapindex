package ru.jobick.lapindex.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class LapindexIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        LapindexJsonIndex.getInstance(project).refresh()
    }
}
