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
