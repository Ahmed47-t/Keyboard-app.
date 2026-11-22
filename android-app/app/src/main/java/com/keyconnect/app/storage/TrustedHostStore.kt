package com.keyconnect.app.storage

import android.content.Context
import android.os.Build
import com.keyconnect.app.crypto.StaticKeyIdentity
import com.keyconnect.app.model.KeyboardLanguageLayer
import com.keyconnect.app.model.KeyboardSurfaceMode
import com.keyconnect.app.model.ProtocolConstants
import com.keyconnect.app.model.TransportPreference
import com.keyconnect.app.model.TrustedHost
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

class TrustedHostStore(context: Context) {
    private val prefs = context.getSharedPreferences("keyconnect_prefs_v1", Context.MODE_PRIVATE)

    fun getOrCreateIdentity(): StaticKeyIdentity {
        val existingHex = prefs.getString(KEY_IDENTITY_SECRET, null)
        if (!existingHex.isNullOrBlank()) {
            runCatching { return StaticKeyIdentity.fromSecretHex(existingHex) }
        }
        val created = StaticKeyIdentity.generate()
        prefs.edit().putString(KEY_IDENTITY_SECRET, created.secretHex).apply()
        return created
    }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value.trim()).apply()

    fun loadTrustedHosts(): List<TrustedHost> {
        val raw = prefs.getString(KEY_TRUSTED_HOSTS, null) ?: return emptyList()
        return runCatching {
            ProtocolConstants.json.decodeFromString(
                ListSerializer(TrustedHost.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

    fun saveTrustedHost(host: TrustedHost) {
        val current = loadTrustedHosts().associateBy { it.deviceId }.toMutableMap()
        current[host.deviceId] = host
        val encoded = ProtocolConstants.json.encodeToString(
            ListSerializer(TrustedHost.serializer()),
            current.values.toList()
        )
        prefs.edit()
            .putString(KEY_TRUSTED_HOSTS, encoded)
            .putString(KEY_LAST_HOST_ID, host.deviceId)
            .apply()
    }

    fun removeTrustedHost(deviceId: String) {
        val filtered = loadTrustedHosts().filterNot { it.deviceId == deviceId }
        val encoded = ProtocolConstants.json.encodeToString(
            ListSerializer(TrustedHost.serializer()),
            filtered
        )
        prefs.edit().putString(KEY_TRUSTED_HOSTS, encoded).apply()
    }

    var lastConnectedHostId: String?
        get() = prefs.getString(KEY_LAST_HOST_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_HOST_ID, value).apply()

    var transportPreference: TransportPreference
        get() = runCatching {
            TransportPreference.valueOf(
                prefs.getString(KEY_TRANSPORT_PREF, TransportPreference.AUTO_SMART.name)!!
            )
        }.getOrDefault(TransportPreference.AUTO_SMART)
        set(value) = prefs.edit().putString(KEY_TRANSPORT_PREF, value.name).apply()

    var surfaceMode: KeyboardSurfaceMode
        get() = runCatching {
            KeyboardSurfaceMode.valueOf(
                prefs.getString(KEY_SURFACE_MODE, KeyboardSurfaceMode.PC_FULL_CUSTOM.name)!!
            )
        }.getOrDefault(KeyboardSurfaceMode.PC_FULL_CUSTOM)
        set(value) = prefs.edit().putString(KEY_SURFACE_MODE, value.name).apply()

    var defaultLanguage: KeyboardLanguageLayer
        get() = runCatching {
            KeyboardLanguageLayer.valueOf(
                prefs.getString(KEY_DEFAULT_LANG, KeyboardLanguageLayer.ARABIC_101.name)!!
            )
        }.getOrDefault(KeyboardLanguageLayer.ARABIC_101)
        set(value) = prefs.edit().putString(KEY_DEFAULT_LANG, value.name).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var forceLandscapeKeyboard: Boolean
        get() = prefs.getBoolean(KEY_FORCE_LANDSCAPE, true)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_LANDSCAPE, value).apply()

    var autoOpenKeyboardOnConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OPEN_KB, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_OPEN_KB, value).apply()

    companion object {
        private const val KEY_IDENTITY_SECRET = "identity_secret_hex"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_TRUSTED_HOSTS = "trusted_hosts_json"
        private const val KEY_LAST_HOST_ID = "last_host_id"
        private const val KEY_TRANSPORT_PREF = "transport_pref"
        private const val KEY_SURFACE_MODE = "surface_mode"
        private const val KEY_DEFAULT_LANG = "default_lang"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_FORCE_LANDSCAPE = "force_landscape_kb"
        private const val KEY_AUTO_OPEN_KB = "auto_open_kb"
    }
}
