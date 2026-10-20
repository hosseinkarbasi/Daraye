package com.darayi.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class MarketKeys(val stocks: String = "", val navasan: String = "")
interface KeyCipher { fun seal(text: String): String; fun open(text: String): String }
class AesKeyCipher(private val key: () -> SecretKey): KeyCipher {
    override fun seal(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
    override fun open(text: String): String {
        val parts = text.split(':'); require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        return String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8)
    }
}
class SecureKeys(private val prefs: SharedPreferences, private val legacy: SharedPreferences, private val cipher: KeyCipher) {
    constructor(context: Context): this(context.getSharedPreferences("market_credentials", 0), context.getSharedPreferences("portfolio", 0), AesKeyCipher {
        val alias = "darayi.marketKeys.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    })
    fun load(): MarketKeys {
        val sealed = prefs.getString("sealed", null)
        if (sealed != null) {
            val keys = decode(cipher.open(sealed))
            check(legacy.edit().remove("apiKey").remove("navasanApiKey").commit())
            return keys
        }
        val keys = MarketKeys(legacy.getString("apiKey", "") ?: "", legacy.getString("navasanApiKey", "") ?: "")
        if (keys.stocks.isBlank() && keys.navasan.isBlank()) return MarketKeys()
        save(keys)
        return keys
    }
    fun save(keys: MarketKeys) {
        val json = JSONObject().put("stocks", keys.stocks).put("navasan", keys.navasan).toString()
        val sealed = cipher.seal(json)
        check(decode(cipher.open(sealed)) == keys)
        check(prefs.edit().putString("sealed", sealed).commit())
        // Plaintext is removed only after the encrypted, authenticated value was verified and persisted.
        check(legacy.edit().remove("apiKey").remove("navasanApiKey").commit())
    }
    private fun decode(raw: String) = JSONObject(raw).let { MarketKeys(it.getString("stocks"), it.getString("navasan")) }
}
