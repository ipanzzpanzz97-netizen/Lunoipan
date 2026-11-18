package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CachedSnapshotData
import com.example.data.model.DcaFrequency
import com.example.data.model.TradePlanRecord
import com.example.data.model.TradingPair
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.quantDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "quantpulse_vault_prefs")

data class VaultCredentials(
  val apiKeyId: String,
  val apiKeySecret: String,
) {
  val isConfigured: Boolean
    get() = apiKeyId.isNotBlank() && apiKeySecret.isNotBlank()
}

data class UserQuantPreferences(
  val selectedPair: TradingPair = TradingPair.XBTMYR,
  val totalCapitalMyr: Double = 10000.0,
  val riskTolerancePct: Double = 1.5,
  val defaultStopLossPct: Double = 3.0,
  val dcaMonthlyAmount: Double = 500.0,
  val dcaDurationMonths: Int = 12,
  val dcaAnnualYieldPct: Double = 18.0,
  val dcaReminderEnabled: Boolean = false,
  val dcaFrequency: DcaFrequency = DcaFrequency.WEEKLY,
  val profitGoalPct: Double = 95.0,
  val autoPilotEnabled: Boolean = true,
)

class SecureVault(context: Context) {

  private val appContext = context.applicationContext
  private val dataStore = appContext.quantDataStore

  private val moshi: Moshi by lazy {
    Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  }
  private val tradeListAdapter by lazy {
    val type = Types.newParameterizedType(List::class.java, TradePlanRecord::class.java)
    moshi.adapter<List<TradePlanRecord>>(type)
  }
  private val cachedSnapshotAdapter by lazy {
    moshi.adapter(CachedSnapshotData::class.java)
  }

  private object Keys {
    val ENCRYPTED_KEY_ID = stringPreferencesKey("enc_luno_key_id")
    val ENCRYPTED_KEY_SECRET = stringPreferencesKey("enc_luno_key_secret")
    val SELECTED_PAIR = stringPreferencesKey("selected_pair")
    val TOTAL_CAPITAL = doublePreferencesKey("total_capital_myr")
    val RISK_TOLERANCE = doublePreferencesKey("risk_tolerance_pct")
    val STOP_LOSS_PCT = doublePreferencesKey("default_stop_loss_pct")
    val DCA_MONTHLY_AMOUNT = doublePreferencesKey("dca_monthly_amount")
    val DCA_DURATION_MONTHS = intPreferencesKey("dca_duration_months")
    val DCA_ANNUAL_YIELD = doublePreferencesKey("dca_annual_yield_pct")
    val DCA_REMINDER_ENABLED = booleanPreferencesKey("dca_reminder_enabled")
    val DCA_FREQUENCY = stringPreferencesKey("dca_frequency")
    val PROFIT_GOAL_PCT = doublePreferencesKey("profit_goal_pct")
    val TRADE_PLANS_JSON = stringPreferencesKey("trade_plans_json")
    val AUTO_PILOT_ENABLED = booleanPreferencesKey("auto_pilot_enabled")
    fun cachedSnapshotKey(pairCode: String) = stringPreferencesKey("cached_snapshot_$pairCode")
  }

  val credentialsFlow: Flow<VaultCredentials> =
    dataStore.data.map { prefs ->
      val encId = prefs[Keys.ENCRYPTED_KEY_ID].orEmpty()
      val encSecret = prefs[Keys.ENCRYPTED_KEY_SECRET].orEmpty()
      VaultCredentials(
        apiKeyId = decryptSafe(encId),
        apiKeySecret = decryptSafe(encSecret),
      )
    }

  val preferencesFlow: Flow<UserQuantPreferences> =
    dataStore.data.map { prefs ->
      val rawRisk = prefs[Keys.RISK_TOLERANCE] ?: 1.5
      UserQuantPreferences(
        selectedPair = TradingPair.fromCode(prefs[Keys.SELECTED_PAIR] ?: TradingPair.XBTMYR.pairCode),
        totalCapitalMyr = (prefs[Keys.TOTAL_CAPITAL] ?: 10000.0).coerceAtLeast(10.0),
        riskTolerancePct = rawRisk.coerceIn(1.0, 2.0),
        defaultStopLossPct = (prefs[Keys.STOP_LOSS_PCT] ?: 3.0).coerceIn(0.5, 25.0),
        dcaMonthlyAmount = (prefs[Keys.DCA_MONTHLY_AMOUNT] ?: 500.0).coerceAtLeast(10.0),
        dcaDurationMonths = (prefs[Keys.DCA_DURATION_MONTHS] ?: 12).coerceIn(3, 36),
        dcaAnnualYieldPct = (prefs[Keys.DCA_ANNUAL_YIELD] ?: 18.0).coerceIn(1.0, 60.0),
        dcaReminderEnabled = prefs[Keys.DCA_REMINDER_ENABLED] ?: false,
        dcaFrequency =
          runCatching {
            DcaFrequency.valueOf(prefs[Keys.DCA_FREQUENCY] ?: DcaFrequency.WEEKLY.name)
          }.getOrDefault(DcaFrequency.WEEKLY),
        profitGoalPct = (prefs[Keys.PROFIT_GOAL_PCT] ?: 95.0).coerceIn(10.0, 500.0),
        autoPilotEnabled = prefs[Keys.AUTO_PILOT_ENABLED] ?: true,
      )
    }

  val tradePlansFlow: Flow<List<TradePlanRecord>> =
    dataStore.data.map { prefs ->
      val raw = prefs[Keys.TRADE_PLANS_JSON].orEmpty()
      if (raw.isBlank()) {
        emptyList()
      } else {
        runCatching { tradeListAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
      }
    }

  suspend fun saveTradePlans(plans: List<TradePlanRecord>) {
    val json = runCatching { tradeListAdapter.toJson(plans) }.getOrDefault("[]")
    dataStore.edit { it[Keys.TRADE_PLANS_JSON] = json }
  }

  suspend fun updateProfitGoalPct(goalPct: Double) {
    dataStore.edit { it[Keys.PROFIT_GOAL_PCT] = goalPct.coerceIn(10.0, 500.0) }
  }

  suspend fun setAutoPilotEnabled(enabled: Boolean) {
    dataStore.edit { it[Keys.AUTO_PILOT_ENABLED] = enabled }
  }

  suspend fun saveCachedSnapshot(data: CachedSnapshotData) {
    val json = runCatching { cachedSnapshotAdapter.toJson(data) }.getOrNull() ?: return
    dataStore.edit { it[Keys.cachedSnapshotKey(data.pairCode)] = json }
  }

  suspend fun getCachedSnapshot(pairCode: String): CachedSnapshotData? {
    val prefs = dataStore.data.first()
    val raw = prefs[Keys.cachedSnapshotKey(pairCode)] ?: return null
    return runCatching { cachedSnapshotAdapter.fromJson(raw) }.getOrNull()
  }

  suspend fun getCredentials(): VaultCredentials = credentialsFlow.first()

  suspend fun saveCredentials(apiKeyId: String, apiKeySecret: String) {
    val cleanId = apiKeyId.trim()
    val cleanSecret = apiKeySecret.trim()
    val encId = if (cleanId.isNotEmpty()) encrypt(cleanId) else ""
    val encSecret = if (cleanSecret.isNotEmpty()) encrypt(cleanSecret) else ""
    dataStore.edit { prefs ->
      prefs[Keys.ENCRYPTED_KEY_ID] = encId
      prefs[Keys.ENCRYPTED_KEY_SECRET] = encSecret
    }
  }

  suspend fun clearCredentials() {
    dataStore.edit { prefs ->
      prefs.remove(Keys.ENCRYPTED_KEY_ID)
      prefs.remove(Keys.ENCRYPTED_KEY_SECRET)
    }
  }

  suspend fun updateSelectedPair(pair: TradingPair) {
    dataStore.edit { it[Keys.SELECTED_PAIR] = pair.pairCode }
  }

  suspend fun updateRiskSettings(
    totalCapitalMyr: Double,
    riskTolerancePct: Double,
    stopLossPct: Double,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.TOTAL_CAPITAL] = totalCapitalMyr.coerceAtLeast(10.0)
      // Hardcode quantitative portfolio risk cap strictly within 1% - 2%
      prefs[Keys.RISK_TOLERANCE] = riskTolerancePct.coerceIn(1.0, 2.0)
      prefs[Keys.STOP_LOSS_PCT] = stopLossPct.coerceIn(0.5, 25.0)
    }
  }

  suspend fun updateDcaSettings(
    monthlyAmount: Double,
    durationMonths: Int,
    annualYieldPct: Double,
    reminderEnabled: Boolean,
    frequency: DcaFrequency,
  ) {
    dataStore.edit { prefs ->
      prefs[Keys.DCA_MONTHLY_AMOUNT] = monthlyAmount.coerceAtLeast(10.0)
      prefs[Keys.DCA_DURATION_MONTHS] = durationMonths.coerceIn(3, 36)
      prefs[Keys.DCA_ANNUAL_YIELD] = annualYieldPct.coerceIn(1.0, 60.0)
      prefs[Keys.DCA_REMINDER_ENABLED] = reminderEnabled
      prefs[Keys.DCA_FREQUENCY] = frequency.name
    }
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
    if (existing != null) return existing.secretKey

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun encrypt(plainText: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
    val combined = ByteArray(1 + iv.size + cipherBytes.size)
    combined[0] = iv.size.toByte()
    System.arraycopy(iv, 0, combined, 1, iv.size)
    System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
    return Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  private fun decryptSafe(encoded: String): String {
    if (encoded.isBlank()) return ""
    return runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
      }
      .getOrDefault("")
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "quantpulse_luno_vault_aes256"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
