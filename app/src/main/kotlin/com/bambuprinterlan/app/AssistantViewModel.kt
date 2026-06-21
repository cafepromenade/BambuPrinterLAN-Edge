package com.bambuprinterlan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.net.bambu.integrations.AiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The BambuLan AI labs, each a prompt template over the shared model client. */
enum class AssistantMode(val title: Bi, val hint: Bi) {
    FEATURE(Bi("Feature Suggestion", "功能建議"), Bi("Describe a feature to implement", "描述想實作嘅功能")),
    FILAMENT(Bi("AI Filament Intake", "AI 線材登記"), Bi("Describe the spool (brand/type/colour/temps)", "描述線材（牌子／類型／顏色／溫度）")),
    SIDECHAT(Bi("Sidechat Inbox", "Sidechat 收件匣"), Bi("Paste notes to turn into tasks", "貼上筆記轉做任務")),
    MINER(Bi("Community Miner", "社群挖掘"), Bi("Paste GitHub/forum feature requests", "貼上 GitHub／論壇功能請求")),
    ASK(Bi("Ask", "提問"), Bi("Ask anything about 3D printing", "問任何 3D 打印問題"));

    fun prompt(input: String): String = when (this) {
        FEATURE -> "You are a 3D-printer slicer product engineer. Turn this feature request into a concise implementation plan (steps, files, risks):\n\n$input"
        FILAMENT -> "From this filament spool description, output a filament preset as key: value lines (type, brand, color hex, nozzle_temp_min, nozzle_temp_max, bed_temp, max_volumetric_speed):\n\n$input"
        SIDECHAT -> "Convert these notes into a clear, deduplicated checklist of actionable tasks:\n\n$input"
        MINER -> "From these community feature requests, extract a prioritized list of distinct, actionable feature tasks:\n\n$input"
        ASK -> input
    }
}

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val ai = AiClient()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun run(mode: AssistantMode, input: String) {
        if (input.isBlank()) { _output.value = "Enter some text  請輸入文字"; return }
        viewModelScope.launch {
            val key = repo.getString("anthropic_api_key").first()
            if (key.isBlank()) {
                _output.value = "Set your Anthropic API key in Settings  喺設定填 Anthropic API key"
                return@launch
            }
            val model = repo.getString("material_auto_fix_opencode_model").first()
            _busy.value = true
            _output.value = "Thinking…  諗緊…"
            val result = ai.complete(key, model, mode.prompt(input), maxTokens = 1200)
            _output.value = result.getOrElse { "Error  錯誤: ${it.message ?: ""}" }
            _busy.value = false
        }
    }
}
