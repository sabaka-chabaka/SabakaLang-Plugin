package com.sabakachabaka.sabakalang.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "SabakaLangSettings",
    storages = [Storage("SabakaLang.xml")]
)
@Service(Service.Level.APP)
class SabakaSettings : PersistentStateComponent<SabakaSettings.State> {

    data class State(
        var interpreterPath: String = "sabaka",
        var defaultWorkingDir: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var interpreterPath: String
        get() = myState.interpreterPath.ifBlank { "sabaka" }
        set(v) { myState.interpreterPath = v }

    var defaultWorkingDir: String
        get() = myState.defaultWorkingDir
        set(v) { myState.defaultWorkingDir = v }

    companion object {
        fun getInstance(): SabakaSettings =
            ApplicationManager.getApplication().getService(SabakaSettings::class.java)
    }
}
