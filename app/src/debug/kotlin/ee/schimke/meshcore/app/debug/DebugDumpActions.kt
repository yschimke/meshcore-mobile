package ee.schimke.meshcore.app.debug

import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.core.model.ContactType
import java.io.PrintWriter
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Verbs that mutate state, hit the radio, or otherwise leave a trace.
 * Each one is gated by a specific [DebugAllowlists] entry and emits a
 * human-readable failure when refused.
 *
 * Each action's total budget (AMS dumpsys watchdog is ~10 s) is
 * capped by [ACTION_TIMEOUT_MS]; the result line includes elapsed ms
 * so `--login`/`--send-*` callers can distinguish "no reply" from
 * "device said no".
 */
internal object DebugDumpActions {

    /** Stays comfortably under the ~10 s AMS dumpsys timer. */
    private const val ACTION_TIMEOUT_MS = 6_000L

    fun login(w: PrintWriter, app: MeshcoreApp, query: String, password: String) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        val contact = uniqueContact(w, client, query) ?: return
        if (!gateContact(w, contact.name)) return
        if (contact.type != ContactType.ROOM && contact.type != ContactType.REPEATER) {
            w.println("'${contact.name}' is ${contact.type}; login only applies to ROOM/REPEATER")
            return
        }
        w.println("login → ${contact.name} pubkey=${contact.publicKey.toHex().take(12)}…")
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) {
                client.login(contact.publicKey, password, timeoutMs = ACTION_TIMEOUT_MS)
            }
        }
        result.fold(
            onSuccess = { w.println("LoginSuccess (${elapsed}ms)") },
            onFailure = { w.println("LoginFail (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun sendDirect(w: PrintWriter, app: MeshcoreApp, query: String, text: String) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        val contact = uniqueContact(w, client, query) ?: return
        if (!gateContact(w, contact.name)) return
        w.println("sendDirect → ${contact.name} text='${text.take(40)}'")
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) {
                client.sendText(contact.publicKey, text, Clock.System.now())
            }
        }
        result.fold(
            onSuccess = { ack -> w.println("Sent (${elapsed}ms): ackHash=${ack.ackHash} flood=${ack.isFlood}") },
            onFailure = { w.println("Fail (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun sendChannel(w: PrintWriter, app: MeshcoreApp, channelQuery: String, text: String) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        val channel = client.channels.value.firstOrNull {
            it.name.equals(channelQuery, ignoreCase = true) || it.index.toString() == channelQuery
        } ?: run {
            w.println("no channel matches '$channelQuery'")
            return
        }
        if (channel.name !in DebugAllowlists.sendChannelNames) {
            w.println("refused: channel '${channel.name}' not in DebugAllowlists.sendChannelNames")
            return
        }
        w.println("sendChannel → [${channel.index}] '${channel.name}' text='${text.take(40)}'")
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) {
                client.sendChannelText(
                    channelIdx = channel.index,
                    text = text,
                    timestamp = Clock.System.now(),
                )
            }
        }
        result.fold(
            onSuccess = { ack -> w.println("Sent (${elapsed}ms): ackHash=${ack.ackHash} flood=${ack.isFlood}") },
            onFailure = { w.println("Fail (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun sync(w: PrintWriter, app: MeshcoreApp) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        w.println("syncMessages: draining …")
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) { client.syncMessages() }
        }
        result.fold(
            onSuccess = { w.println("syncMessages: done (${elapsed}ms)") },
            onFailure = { w.println("syncMessages (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun sendAdvert(w: PrintWriter, app: MeshcoreApp, flood: Boolean) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        w.println("sendSelfAdvert flood=$flood …")
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) { client.sendSelfAdvert(flood) }
        }
        result.fold(
            onSuccess = { w.println("advert sent (${elapsed}ms)") },
            onFailure = { w.println("advert failed (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun setAdvertName(w: PrintWriter, app: MeshcoreApp, name: String) {
        val client = DebugDumpRead.requireClient(w, app) ?: return
        if (name !in DebugAllowlists.selfNames) {
            w.println("refused: '$name' not in DebugAllowlists.selfNames")
            return
        }
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) { client.setAdvertName(name) }
        }
        result.fold(
            onSuccess = { w.println("setAdvertName → '$name' (${elapsed}ms)") },
            onFailure = { w.println("failed (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    fun reboot(w: PrintWriter, app: MeshcoreApp, token: String?) {
        if (token != DebugAllowlists.REBOOT_TOKEN) {
            w.println("refused: pass --confirm ${DebugAllowlists.REBOOT_TOKEN} to reboot")
            return
        }
        val client = DebugDumpRead.requireClient(w, app) ?: return
        val (elapsed, result) = timedBlocking {
            withTimeout(ACTION_TIMEOUT_MS) { client.reboot() }
        }
        result.fold(
            onSuccess = { w.println("reboot frame sent (${elapsed}ms) — device will disconnect") },
            onFailure = { w.println("failed (${elapsed}ms): ${it.javaClass.simpleName}: ${it.message}") },
        )
    }

    // ---- helpers ------------------------------------------------------

    private fun uniqueContact(
        w: PrintWriter,
        client: ee.schimke.meshcore.core.client.MeshCoreClient,
        query: String,
    ): ee.schimke.meshcore.core.model.Contact? {
        val matches = DebugDumpRead.findContacts(client, query)
        return when (matches.size) {
            0 -> { w.println("no contact matches '$query'"); null }
            1 -> matches[0]
            else -> {
                w.println("ambiguous '$query' — ${matches.size} matches; use a longer pubkey prefix:")
                matches.forEach { w.println("  ${DebugDumpRead.contactLine(it)}") }
                null
            }
        }
    }

    private fun gateContact(w: PrintWriter, name: String): Boolean {
        if (name in DebugAllowlists.contacts) return true
        w.println("refused: '$name' not in DebugAllowlists.contacts.")
        w.println("Add the contact name to DebugAllowlists in app/src/debug/…/DebugAllowlists.kt,")
        w.println("rebuild, try again.")
        return false
    }

    private inline fun <T> timedBlocking(crossinline block: suspend () -> T): Pair<Long, Result<T>> {
        val start = System.currentTimeMillis()
        val result = runCatching {
            runBlocking(Dispatchers.Default) { block() }
        }
        return (System.currentTimeMillis() - start) to result
    }
}
