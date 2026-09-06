package com.david.mailapp.feature.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InboxTimeFormatterTest {

    private val timestamps = listOf(
        1700000000000L,
        1600000000000L,
        1725579144000L,
        0L
    )

    private val locales = listOf(
        Locale.US,
        Locale.forLanguageTag("es-ES")
    )

    private val timeZones = listOf(
        TimeZone.getTimeZone("UTC"),
        TimeZone.getTimeZone("America/El_Salvador")
    )

    private val patterns = listOf(
        "HH:mm",
        "h:mm a",
        "dd/MM/yyyy HH:mm"
    )

    @Test
    fun `InboxTimeFormatter produce exactamente el mismo resultado que SimpleDateFormat`() {
        for (pattern in patterns) {
            for (locale in locales) {
                for (tz in timeZones) {
                    val formatter = InboxTimeFormatter(pattern, locale, tz)
                    val legacySdf = SimpleDateFormat(pattern, locale).apply {
                        timeZone = tz
                    }

                    for (timestamp in timestamps) {
                        val expected = legacySdf.format(Date(timestamp))
                        val actual = formatter.format(timestamp)
                        assertEquals(
                            "Mismatch for pattern=$pattern, locale=$locale, tz=${tz.id}, timestamp=$timestamp",
                            expected,
                            actual
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `InboxTimeFormatter es seguro en llamadas concurrentes`() {
        val pattern = "HH:mm:ss.SSS"
        val locale = Locale.US
        val tz = TimeZone.getTimeZone("America/El_Salvador")
        val formatter = InboxTimeFormatter(pattern, locale, tz)
        val threadCount = 10
        val iterationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)

        val futures = (0 until threadCount).map { threadIdx ->
            executor.submit {
                for (i in 0 until iterationsPerThread) {
                    val ts = 1700000000000L + (threadIdx * 1000) + i
                    val legacySdf = SimpleDateFormat(pattern, locale).apply {
                        timeZone = tz
                    }
                    val expected = legacySdf.format(Date(ts))
                    val actual = formatter.format(ts)
                    assertEquals(expected, actual)
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals("Tasks should finish in time", true, finished)
        futures.forEach { it.get() }
    }
}
