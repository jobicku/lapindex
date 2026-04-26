package ru.jobick.lapindex.index

import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.vfs.VirtualFile

data class JsonPropertyLocation(
    val file: VirtualFile,
    val property: JsonProperty
)
