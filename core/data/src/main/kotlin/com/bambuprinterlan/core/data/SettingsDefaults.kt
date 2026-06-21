package com.bambuprinterlan.core.data

/**
 * Faithful port of BambuLan's custom AppConfig defaults (AppConfig.cpp:279-407)
 * plus the feature-flag registry defaults. Same keys + defaults so `.bambulan`
 * settings bundles remain compatible across desktop and Android.
 */
object SettingsDefaults {
    val strings: Map<String, String> = mapOf(
        "user_mode" to "advanced",
        "material_self_fix_retry_limit" to "5",
        "material_auto_fix_opencode_model" to "anthropic/claude-opus-4-8",
        "discord_webhook_profile_count" to "0",
        "discord_webhook_active_profile" to "",
        "home_assistant_url" to "http://homeassistant.local:8123",
        "home_assistant_token" to "",
        "home_assistant_entity_cache" to "",
        "material_clock_color" to "#1A73E8",
        "material_clock_prefix" to "",
        "severity_level" to "info",
        // BambuPrinterLan addition
        "app_language_mode" to "bilingual_yue_en",
    )

    val booleans: Map<String, Boolean> = mapOf(
        "developer_mode" to false,
        "material_auto_save" to true,
        "material_clock_show_seconds" to false,
        "material_clock_show_date" to false,
        "material_library_hover_preview" to true,
        "material_self_fix_enabled" to true,
        "material_auto_fix_sandbox" to false,
        "discord_webhooks_enabled" to false,
        "discord_notify_startup" to true,
        "discord_notify_project" to true,
        "discord_notify_export" to true,
        "discord_notify_autofix" to true,
        "discord_notify_errors" to true,
        "home_assistant_enabled" to false,
        "home_assistant_auto_ai" to false,
        "home_assistant_allow_lights" to true,
        "home_assistant_allow_scenes" to true,
        "home_assistant_allow_switches" to false,
        "home_assistant_allow_climate" to false,
    )

    val ints: Map<String, Int> = mapOf(
        "material_clock_text_size" to 18,
    )

    /** Feature-flag keys + their default-on state (port of bambulan_feature_flags). */
    val featureFlags: Map<String, Boolean> = mapOf(
        "bambulan_material_file_hub" to true,
        "bambulan_material_fidget_lab" to true,
        "bambulan_project_tabs" to true,
        "material_auto_save" to true,
        "material_library_hover_preview" to true,
        "bambulan_all_formats_zip" to true,
        "bambulan_settings_bundle" to true,
        "bambulan_store" to true,
        "bambulan_model_edit_lab" to true,
        "bambulan_batch_slicer" to true,
        "bambulan_batch_printer_sender" to true,
        "bambulan_printer_control_lab" to true,
        "bambulan_auto_fix" to true,
        "bambulan_sidechat_inbox" to true,
        "bambulan_feature_suggestion" to true,
        "bambulan_community_miner" to true,
        "bambulan_ai_filament" to true,
        "bambulan_hms_ai_resources" to true,
        "discord_webhooks_enabled" to true,
        "home_assistant_enabled" to true,
        "bambulan_haha_dialog" to true,
        "bambulan_quiet_source_update" to true,
        "bambulan_ftp_ai_repair" to true,
        "bambulan_opencode_dependency_repair" to true,
    )
}
