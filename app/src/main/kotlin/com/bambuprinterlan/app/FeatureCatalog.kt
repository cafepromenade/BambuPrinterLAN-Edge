package com.bambuprinterlan.app

import com.bambuprinterlan.core.design.Bi

/**
 * Port of BambuLan's `bambulan_feature_flags()` registry (MainFrame.cpp:863).
 * Each custom feature is gated by a flag with the same key, so `.bambulan`
 * settings bundles remain compatible. Titles/details are bilingual (Cantonese +
 * English). `phase` notes the roadmap phase (plan §8).
 */
data class FeatureFlag(
    val key: String,
    val title: Bi,
    val detail: Bi,
    val defaultOn: Boolean = true,
    val phase: Int,
)

object FeatureCatalog {
    val flags = listOf(
        FeatureFlag("bambulan_material_file_hub", Bi("Material File Hub", "Material 檔案中心"),
            Bi("Material Design project/import/export hub.", "Material 風格嘅專案／匯入匯出中心。"), phase = 7),
        FeatureFlag("bambulan_material_fidget_lab", Bi("Material Fidget Lab", "Material 玩具實驗室"),
            Bi("Animated Material control playground.", "識郁嘅 Material 控制項遊樂場。"), phase = 7),
        FeatureFlag("bambulan_project_tabs", Bi("Project Tabs", "專案分頁"),
            Bi("Tabbed project/session switching.", "用分頁切換唔同專案。"), phase = 7),
        FeatureFlag("material_auto_save", Bi("Auto Save", "自動儲存"),
            Bi("Auto-save project/config while working.", "做嘢期間自動儲存專案同設定。"), phase = 7),
        FeatureFlag("material_library_hover_preview", Bi("Library Preview", "資料庫預覽"),
            Bi("Preview models in the library.", "喺資料庫度預覽模型。"), phase = 7),
        FeatureFlag("bambulan_all_formats_zip", Bi("All Formats ZIP Export", "全格式 ZIP 匯出"),
            Bi("Project/config bundle + sliced-output request.", "專案／設定打包加切片輸出。"), phase = 7),
        FeatureFlag("bambulan_settings_bundle", Bi("Bambu Printer LAN Import/Export", "Bambu Printer LAN 匯入匯出"),
            Bi("Base64 .bambulan settings import/export.", "Base64 .bambulan 設定匯入匯出。"), phase = 7),
        FeatureFlag("bambulan_store", Bi("Bambu Store", "Bambu 商店"),
            Bi("Bambu model store entry point.", "Bambu 模型商店入口。"), phase = 7),
        FeatureFlag("bambulan_model_edit_lab", Bi("Model Edit Lab", "模型編輯實驗室"),
            Bi("Model create/transform/mesh/diagnostics.", "建立／變形／網格／診斷模型。"), phase = 6),
        FeatureFlag("bambulan_batch_slicer", Bi("Batch Slicer", "批次切片"),
            Bi("Queue and slice many projects.", "排隊一次過切好多專案。"), phase = 7),
        FeatureFlag("bambulan_batch_printer_sender", Bi("Batch Printer Sender", "批次列印傳送"),
            Bi("Queue many sliced files to the printer.", "排隊將好多切片檔傳去打印機。"), phase = 7),
        FeatureFlag("bambulan_printer_control_lab", Bi("Printer Control Lab", "打印機控制實驗室"),
            Bi("All controllable printer features.", "所有可以控制嘅打印機功能。"), phase = 4),
        FeatureFlag("bambulan_auto_fix", Bi("Auto Fix Dashboard", "自動修復面板"),
            Bi("AI task dashboard with retries and chats.", "有重試同對話嘅 AI 任務面板。"), phase = 8),
        FeatureFlag("bambulan_sidechat_inbox", Bi("Sidechat Feature Inbox", "Sidechat 功能收件匣"),
            Bi("Notes -> automated feature tasks.", "筆記變自動功能任務。"), phase = 8),
        FeatureFlag("bambulan_feature_suggestion", Bi("Feature Suggestion", "功能建議"),
            Bi("Submit a feature for AI implementation.", "提交功能畀 AI 自動實作。"), phase = 8),
        FeatureFlag("bambulan_community_miner", Bi("Community Feature Miner", "社群功能挖掘"),
            Bi("Import GitHub/forum requests as tasks.", "將 GitHub／論壇請求變任務。"), phase = 8),
        FeatureFlag("bambulan_ai_filament", Bi("AI Filament Intake", "AI 線材登記"),
            Bi("Photo/description -> filament preset.", "影相／描述變線材預設。"), phase = 8),
        FeatureFlag("bambulan_hms_ai_resources", Bi("HMS AI Resources", "HMS AI 資源"),
            Bi("AI-selected wiki/YouTube fix resources.", "AI 揀嘅 wiki／YouTube 維修資源。"), phase = 8),
        FeatureFlag("discord_webhooks_enabled", Bi("Discord Webhooks", "Discord Webhook"),
            Bi("Event webhooks, wizard, profile manager.", "事件 webhook、精靈同設定檔管理。"), phase = 8),
        FeatureFlag("home_assistant_enabled", Bi("Home Assistant Manager", "Home Assistant 管理"),
            Bi("HA discovery + printer-event automation.", "HA 探索同打印機事件自動化。"), phase = 8),
        FeatureFlag("bambulan_haha_dialog", Bi("haha Dialog", "haha 對話框"),
            Bi("1% startup haha dialog.", "開機 1% 機會出 haha。"), phase = 7),
        FeatureFlag("bambulan_quiet_source_update", Bi("Quiet Update", "靜默更新"),
            Bi("Silent self-update (Play Store on Android).", "靜默自我更新（Android 用 Play 商店）。"), phase = 9),
        FeatureFlag("bambulan_ftp_ai_repair", Bi("FTP Retry AI Repair", "FTP 重試 AI 修復"),
            Bi("Upload retry + AI repair attempt.", "上載重試加 AI 嘗試修復。"), phase = 4),
        FeatureFlag("bambulan_opencode_dependency_repair", Bi("AI Dependency Repair", "AI 相依修復"),
            Bi("Assistant connectivity/setup checks.", "助手連線／設定檢查。"), phase = 8),
    )
}
