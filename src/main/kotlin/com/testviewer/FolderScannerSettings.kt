package com.testviewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(
    name = "FolderScannerSettings",
    storages = [Storage("folder-scanner.xml")]
)
@Service
class FolderScannerSettings : PersistentStateComponent<FolderScannerSettings.State> {
    data class State(
        var folderPaths: MutableList<String> = mutableListOf(),
        var useModulePrefix: Boolean = false
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        // Обеспечиваем обратную совместимость: если список пуст, но есть старый формат
        if (this.state.folderPaths.isEmpty() && state.folderPaths.isEmpty()) {
            // Проверяем наличие старого поля через reflection или просто оставляем пустой список
        }
    }

    companion object {
        fun getInstance(): FolderScannerSettings {
            return ApplicationManager.getApplication().getService(FolderScannerSettings::class.java)
        }
    }
}

