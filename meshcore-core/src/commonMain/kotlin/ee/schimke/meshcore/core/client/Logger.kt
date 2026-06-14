package ee.schimke.meshcore.core.client

/**
 * Minimal logging abstraction so `meshcore-core` never hard-codes `println`. Inject a platform
 * implementation (Android `Log`, SLF4J, Kermit, …) when constructing a [MeshCoreClient].
 *
 * The default is [None], which discards everything — a library has no business writing to a
 * consumer's stdout uninvited. CLIs, tests, and debug tooling that *do* want the old chatty output
 * can pass [Println].
 */
interface Logger {
  fun log(level: Level, message: String, throwable: Throwable? = null)

  enum class Level {
    Debug,
    Info,
    Warn,
    Error,
  }

  companion object {
    /** Discards all log output. The default for [MeshCoreClient]. */
    val None: Logger =
      object : Logger {
        override fun log(level: Level, message: String, throwable: Throwable?) {}
      }

    /**
     * Writes every record to stdout via `println`, prefixed with the level. Handy for CLIs, the
     * TUI, and tests that want to see the old `MeshCoreClient` trace output.
     */
    val Println: Logger =
      object : Logger {
        override fun log(level: Level, message: String, throwable: Throwable?) {
          println("[$level] $message")
          throwable?.let { println(it.stackTraceToString()) }
        }
      }
  }
}

internal fun Logger.debug(message: String) = log(Logger.Level.Debug, message)

internal fun Logger.warn(message: String, throwable: Throwable? = null) =
  log(Logger.Level.Warn, message, throwable)
