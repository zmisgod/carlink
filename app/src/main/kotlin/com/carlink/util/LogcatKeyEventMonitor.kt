package com.carlink.util

import com.carlink.logging.logWarn
import com.carlink.ui.settings.LogcatKeyMapping
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

enum class LogcatMonitorStatus(
    val label: String,
) {
    STOPPED("Stopped"),
    STARTING("Starting"),
    LISTENING("Listening"),
    RESTARTING("Restarting"),
    ERROR("Read error"),
}

/** Reads system logcat on a background thread and emits debounced rule matches. */
class LogcatKeyEventMonitor(
    private val rulesProvider: () -> List<LogcatKeyMapping>,
    private val onMatch: (LogcatKeyMapping) -> Unit,
    private val onStatusChanged: (LogcatMonitorStatus) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val lastTriggeredAtNanos = ConcurrentHashMap<String, Long>()

    @Volatile private var process: Process? = null

    @Volatile private var workerThread: Thread? = null

    @Volatile private var status = LogcatMonitorStatus.STOPPED

    fun start() {
        if (!running.compareAndSet(false, true)) return
        lastTriggeredAtNanos.clear()
        val runGeneration = generation.incrementAndGet()
        workerThread =
            Thread(
                { readLoop(runGeneration) },
                "LogcatKeyEventMonitor",
            ).apply {
                isDaemon = true
                start()
            }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        generation.incrementAndGet()
        process?.destroy()
        workerThread?.interrupt()
        process = null
        workerThread = null
        updateStatus(LogcatMonitorStatus.STOPPED)
    }

    private fun readLoop(runGeneration: Long) {
        while (isActive(runGeneration)) {
            updateStatus(LogcatMonitorStatus.STARTING)
            try {
                val currentProcess =
                    ProcessBuilder("logcat", "-b", "all", "-v", "threadtime", "-T", "1")
                        .redirectErrorStream(true)
                        .start()
                process = currentProcess
                updateStatus(LogcatMonitorStatus.LISTENING)

                currentProcess.inputStream.bufferedReader().use { reader ->
                    while (isActive(runGeneration)) {
                        val line = reader.readLine() ?: break
                        handleLine(line)
                    }
                }
                val exitCode = runCatching { currentProcess.waitFor() }.getOrDefault(0)
                if (exitCode != 0 && isActive(runGeneration)) {
                    updateStatus(LogcatMonitorStatus.ERROR)
                    logWarn(
                        "[LOGCAT_MONITOR_INTERNAL] logcat exited with code=$exitCode",
                        tag = "INPUT_KEY",
                    )
                }
                if (process === currentProcess) process = null
                currentProcess.destroy()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                if (!isActive(runGeneration)) break
                updateStatus(LogcatMonitorStatus.ERROR)
                logWarn(
                    "[LOGCAT_MONITOR_INTERNAL] reader failed: ${error.javaClass.simpleName}",
                    tag = "INPUT_KEY",
                )
            }

            if (isActive(runGeneration)) {
                updateStatus(LogcatMonitorStatus.RESTARTING)
                try {
                    Thread.sleep(RESTART_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        if (!running.get()) updateStatus(LogcatMonitorStatus.STOPPED)
    }

    private fun handleLine(line: String) {
        if (!THREADTIME_LINE.matchesAt(line, 0)) return
        if (line.contains(INTERNAL_MONITOR_MARKER) || line.contains(INTERNAL_TRIGGER_MARKER)) return

        val now = System.nanoTime()
        rulesProvider().forEach { rule ->
            if (!rule.matches(line)) return@forEach
            val previous = lastTriggeredAtNanos[rule.id] ?: Long.MIN_VALUE
            if (now - previous < DEBOUNCE_NANOS) return@forEach
            lastTriggeredAtNanos[rule.id] = now
            try {
                onMatch(rule)
            } catch (error: Exception) {
                logWarn(
                    "[LOGCAT_MONITOR_INTERNAL] match callback failed: ${error.javaClass.simpleName}",
                    tag = "INPUT_KEY",
                )
            }
        }
    }

    private fun isActive(runGeneration: Long): Boolean =
        running.get() && generation.get() == runGeneration && !Thread.currentThread().isInterrupted

    private fun updateStatus(newStatus: LogcatMonitorStatus) {
        if (status == newStatus) return
        status = newStatus
        onStatusChanged(newStatus)
    }

    companion object {
        private const val RESTART_DELAY_MS = 1500L
        private const val INTERNAL_MONITOR_MARKER = "[LOGCAT_MONITOR_INTERNAL]"
        const val INTERNAL_TRIGGER_MARKER = "[LOGCAT_TRIGGER]"
        private val THREADTIME_LINE = Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+")
        private val DEBOUNCE_NANOS = 500L * 1_000_000L
    }
}
