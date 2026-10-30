@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.meshcore.web

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A radio this owner has connected before, identified by its public key. */
@Serializable
data class SavedRadio(
  val publicKey: String,
  val name: String,
  val usbVendorId: Int? = null,
  val usbProductId: Int? = null,
  val lastConnectedMs: Double = 0.0,
)

/**
 * The radios list, kept in localStorage. In the .wasmo build, meshcore-backend.js shares that key
 * through the Wasmo install's kv service: it seeds the key before the app starts, re-reads it on
 * [load], and writes each change back (docs/wasmo-app.md). The static web build has no backend and
 * keeps the list in the browser.
 */
object RadioStore {
  private const val KEY = "meshcore-radios-v1"
  private val json = Json { ignoreUnknownKeys = true }
  private val writes = Mutex()

  /** The latest list, refreshed from the Wasmo install when there is one. */
  suspend fun load(): List<SavedRadio> {
    refresh(KEY).await<JsAny?>()
    return read()
  }

  suspend fun remember(radio: SavedRadio): List<SavedRadio> = update { radios ->
    listOf(radio) + radios.filterNot { it.publicKey == radio.publicKey }
  }

  suspend fun forget(publicKey: String): List<SavedRadio> = update { radios ->
    radios.filterNot { it.publicKey == publicKey }
  }

  // Applies the change to the freshest list, so another browser's additions aren't overwritten.
  private suspend fun update(change: (List<SavedRadio>) -> List<SavedRadio>): List<SavedRadio> =
    writes.withLock {
      val radios = change(load()).sortedByDescending { it.lastConnectedMs }
      store(KEY, json.encodeToString(radios))
      radios
    }

  private fun read(): List<SavedRadio> =
    getItem(KEY)?.let { runCatching { json.decodeFromString<List<SavedRadio>>(it) }.getOrNull() }
      ?: emptyList()
}

fun nowMs(): Double = js("Date.now()")

private fun getItem(key: String): String? =
  js("(() => { try { return localStorage.getItem(key); } catch (_) { return null; } })()")

private fun store(key: String, value: String): Unit =
  js(
    """{
    try { localStorage.setItem(key, value); } catch (_) {}
    globalThis.meshcoreStorage?.changed(key, value);
  }"""
  )

private fun refresh(key: String): Promise<JsAny?> =
  js("Promise.resolve(globalThis.meshcoreStorage?.refresh(key)).catch(() => null)")
