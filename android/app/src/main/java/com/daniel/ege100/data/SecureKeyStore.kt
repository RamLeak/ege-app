package com.daniel.ege100.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Phase 4 Stage A2 — безопасное хранение API ключей (Convention #39).
 *
 * `EncryptedSharedPreferences` шифрует и ключи, и значения AES-256 через
 * Android Keystore (мастер-ключ привязан к устройству, не экспортируется).
 * Файл `ai_secure_keys.xml` лежит в `data/data/<pkg>/shared_prefs/`.
 *
 * **Исключение из бэкапа** (Convention #40) — `backup_rules.xml` +
 * `data_extraction_rules.xml` имеют `<exclude domain="sharedpref"
 * path="ai_secure_keys.xml"/>`. Это критично: ключи не должны утечь
 * через Google Auto Backup или transfer на новое устройство.
 *
 * Provider type здесь хранится как String (`"OPENROUTER"` / `"GEMINI"` /
 * `"ANTHROPIC"`) — кроме A3 это enum AiProviderType.name. Forward-ref
 * чтобы избежать циклической зависимости data ↔ ai-модуля.
 */
class SecureKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "ai_secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveKey(providerName: String, key: String) {
        prefs.edit().putString(keyFor(providerName), key.trim()).apply()
    }

    fun getKey(providerName: String): String? =
        prefs.getString(keyFor(providerName), null)?.takeIf { it.isNotBlank() }

    fun hasKey(providerName: String): Boolean = !getKey(providerName).isNullOrBlank()

    fun removeKey(providerName: String) {
        prefs.edit().remove(keyFor(providerName)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(providerName: String): String = "key_${providerName.lowercase()}"
}
