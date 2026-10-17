package cn.edu.ubaa.plus.api.storage

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.russhwolf.settings.Settings
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的加密 [Settings]。
 *
 * 相比已弃用的 `EncryptedSharedPreferences`，这里直接用 Keystore 中的 AES-256-GCM 主密钥：
 * - 值：随机 IV + AES/GCM，Base64 后写入 SharedPreferences
 * - 键名：用「由键名派生的固定 IV」加密，保证同一键名映射到同一存储条目，同时不泄露明文键名
 *
 * 每个值前置一个类型标记字符，以便读回时还原原始类型。
 */
internal class KeystoreSettings(
    private val prefs: SharedPreferences,
    private val crypto: KeystoreCrypto = KeystoreCrypto(),
) : Settings {

  override val keys: Set<String>
    get() = prefs.all.keys.mapNotNull(crypto::decrypt).toSet()

  override val size: Int
    get() = keys.size

  override fun clear() {
    prefs.edit().clear().apply()
  }

  override fun remove(key: String) {
    prefs.edit().remove(crypto.encryptDeterministic(key)).apply()
  }

  override fun hasKey(key: String): Boolean = prefs.contains(crypto.encryptDeterministic(key))

  override fun getStringOrNull(key: String): String? = readTagged(key, TAG_STRING) { it }

  override fun putString(key: String, value: String) = writeTagged(key, TAG_STRING, value)

  override fun getIntOrNull(key: String): Int? = readTagged(key, TAG_INT, String::toIntOrNull)

  override fun putInt(key: String, value: Int) = writeTagged(key, TAG_INT, value.toString())

  override fun getLongOrNull(key: String): Long? = readTagged(key, TAG_LONG, String::toLongOrNull)

  override fun putLong(key: String, value: Long) = writeTagged(key, TAG_LONG, value.toString())

  override fun getFloatOrNull(key: String): Float? =
      readTagged(key, TAG_FLOAT, String::toFloatOrNull)

  override fun putFloat(key: String, value: Float) = writeTagged(key, TAG_FLOAT, value.toString())

  override fun getDoubleOrNull(key: String): Double? =
      readTagged(key, TAG_DOUBLE, String::toDoubleOrNull)

  override fun putDouble(key: String, value: Double) =
      writeTagged(key, TAG_DOUBLE, value.toString())

  override fun getBooleanOrNull(key: String): Boolean? =
      readTagged(key, TAG_BOOLEAN) {
        when (it) {
          "true" -> true
          "false" -> false
          else -> null
        }
      }

  override fun putBoolean(key: String, value: Boolean) =
      writeTagged(key, TAG_BOOLEAN, value.toString())

  override fun getString(key: String, defaultValue: String): String =
      getStringOrNull(key) ?: defaultValue

  override fun getInt(key: String, defaultValue: Int): Int = getIntOrNull(key) ?: defaultValue

  override fun getLong(key: String, defaultValue: Long): Long = getLongOrNull(key) ?: defaultValue

  override fun getFloat(key: String, defaultValue: Float): Float =
      getFloatOrNull(key) ?: defaultValue

  override fun getDouble(key: String, defaultValue: Double): Double =
      getDoubleOrNull(key) ?: defaultValue

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
      getBooleanOrNull(key) ?: defaultValue

  private fun <T> readTagged(key: String, tag: Char, parse: (String) -> T?): T? {
    val raw = prefs.getString(crypto.encryptDeterministic(key), null) ?: return null
    val plain = crypto.decrypt(raw) ?: return null
    if (plain.isEmpty() || plain[0] != tag) return null
    return parse(plain.substring(1))
  }

  private fun writeTagged(key: String, tag: Char, value: String) {
    prefs.edit().putString(crypto.encryptDeterministic(key), crypto.encrypt("$tag$value")).apply()
  }

  private companion object {
    const val TAG_STRING = 's'
    const val TAG_INT = 'i'
    const val TAG_LONG = 'l'
    const val TAG_FLOAT = 'f'
    const val TAG_DOUBLE = 'd'
    const val TAG_BOOLEAN = 'b'
  }
}

/** Keystore 中的 AES-256-GCM 主密钥及其加解密。 */
internal class KeystoreCrypto(private val keyAlias: String = DEFAULT_ALIAS) {

  private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  @Volatile private var cachedKey: SecretKey? = null

  private fun key(): SecretKey = cachedKey ?: loadOrCreateKey().also { cachedKey = it }

  /** 随机 IV 加密（用于值）。 */
  fun encrypt(plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    return encode(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
  }

  /**
   * 确定性加密（用于键名）。
   *
   * IV 由键名本身派生，因而同一键名总是得到同一密文；不同键名得到不同 IV。 这里泄露的唯一信息是"两个条目是否同名"，对内部常量键而言可以接受。
   */
  fun encryptDeterministic(plain: String): String =
      try {
        encryptDeterministicOnce(plain)
      } catch (_: InvalidAlgorithmParameterException) {
        // 设备上残留了旧版创建的密钥（不允许调用方提供 IV）：删除重建后重试。
        // 旧密钥下既有的加密值将不可读，等同于首次使用。
        keyStore.deleteEntry(keyAlias)
        cachedKey = null
        encryptDeterministicOnce(plain)
      }

  private fun encryptDeterministicOnce(plain: String): String {
    val nonce =
        MessageDigest.getInstance("SHA-256")
            .digest(plain.toByteArray(Charsets.UTF_8))
            .copyOf(GCM_IV_LENGTH)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, nonce))
    return encode(nonce, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
  }

  /** 解密失败返回 null（例如 Keystore 密钥已失效）。 */
  fun decrypt(encoded: String): String? =
      runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val nonce = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, nonce))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
          }
          .getOrNull()

  private fun encode(nonce: ByteArray, ciphertext: ByteArray): String =
      Base64.encodeToString(nonce + ciphertext, Base64.NO_WRAP)

  private fun loadOrCreateKey(): SecretKey {
    (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
      return it.secretKey
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
        KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // 键名需要确定性加密（同一键名映射到同一条目），因此允许调用方提供 IV。
            // 值仍使用随机 IV。
            .setRandomizedEncryptionRequired(false)
            .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val DEFAULT_ALIAS = "ubaa_settings_key"
    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_BITS = 128
  }
}
