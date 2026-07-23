package com.kidsphoneguard.data.repository

import com.kidsphoneguard.data.db.DailyUsageDao
import com.kidsphoneguard.data.model.DailyUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyUsageRepositoryTest {

    @Test
    fun getTodayDate_usesInjectedTrustedDate() {
        val repository = repository(trustedToday = "2030-01-02")

        assertEquals("2030-01-02", repository.getTodayDate())
    }

    @Test
    fun todayUsageOperations_useTrustedDateAndAccumulateSeconds() = runBlocking {
        val dao = FakeDailyUsageDao()
        val repository = repository(dao, trustedToday = "2030-01-02")

        repository.addTodayUsageTime("com.example.game", 40L)
        repository.addTodayUsageTime("com.example.game", 20L)

        assertEquals(60L, repository.getTodayUsageSeconds("com.example.game"))
        assertEquals(
            60L,
            dao.usages["2030-01-02" to "com.example.game"]?.usedTimeInSeconds
        )
    }

    @Test
    fun getTodayUsageFlow_readsOnlyInjectedTrustedDate() = runBlocking {
        val dao = FakeDailyUsageDao(
            mutableMapOf(
                ("2030-01-01" to "com.example.reader") to DailyUsage(
                    date = "2030-01-01",
                    packageName = "com.example.reader",
                    usedTimeInSeconds = 10L
                ),
                ("2030-01-02" to "com.example.reader") to DailyUsage(
                    date = "2030-01-02",
                    packageName = "com.example.reader",
                    usedTimeInSeconds = 80L
                )
            )
        )
        val repository = repository(dao, trustedToday = "2030-01-02")

        val usage = repository.getTodayUsageFlow("com.example.reader").first()

        assertEquals("2030-01-02", usage?.date)
        assertEquals(80L, usage?.usedTimeInSeconds)
    }

    @Test
    fun resetTodayUsage_usesTrustedDateAndLeavesOtherDatesUntouched() = runBlocking {
        val packageName = "com.example.game"
        val dao = FakeDailyUsageDao(
            mutableMapOf(
                ("2030-01-01" to packageName) to DailyUsage(
                    date = "2030-01-01",
                    packageName = packageName,
                    usedTimeInSeconds = 90L
                ),
                ("2030-01-02" to packageName) to DailyUsage(
                    date = "2030-01-02",
                    packageName = packageName,
                    usedTimeInSeconds = 180L
                )
            )
        )
        val repository = repository(dao, trustedToday = "2030-01-02")

        repository.resetTodayUsage(packageName)

        assertEquals(90L, dao.usages["2030-01-01" to packageName]?.usedTimeInSeconds)
        assertEquals(0L, dao.usages["2030-01-02" to packageName]?.usedTimeInSeconds)
    }

    @Test
    fun cleanupOldData_deletesOnlyDatesBeforeThirtyDayBoundary() = runBlocking {
        val dao = FakeDailyUsageDao(
            mutableMapOf(
                usage("2026-06-18"),
                usage("2026-06-19"),
                usage("2026-06-20")
            )
        )
        val repository = DailyUsageRepository(
            dailyUsageDao = dao,
            trustedTodayProvider = { "2026-07-19" },
            realTodayProvider = { LocalDate.of(2026, 7, 19) }
        )

        repository.cleanupOldData()

        assertEquals("2026-06-19", dao.lastDeleteBeforeDate)
        assertFalse(dao.usages.containsKey("2026-06-18" to PACKAGE_NAME))
        assertTrue(dao.usages.containsKey("2026-06-19" to PACKAGE_NAME))
        assertTrue(dao.usages.containsKey("2026-06-20" to PACKAGE_NAME))
    }

    private fun repository(
        dao: FakeDailyUsageDao = FakeDailyUsageDao(),
        trustedToday: String
    ): DailyUsageRepository = DailyUsageRepository(
        dailyUsageDao = dao,
        trustedTodayProvider = { trustedToday }
    )

    private fun usage(date: String): Pair<Pair<String, String>, DailyUsage> =
        (date to PACKAGE_NAME) to DailyUsage(
            date = date,
            packageName = PACKAGE_NAME,
            usedTimeInSeconds = 1L
        )

    companion object {
        private const val PACKAGE_NAME = "com.example.app"
    }
}

private class FakeDailyUsageDao(
    val usages: MutableMap<Pair<String, String>, DailyUsage> = mutableMapOf()
) : DailyUsageDao {
    var lastDeleteBeforeDate: String? = null

    override suspend fun getUsage(date: String, packageName: String): DailyUsage? =
        usages[date to packageName]

    override fun getUsageFlow(date: String, packageName: String): Flow<DailyUsage?> =
        flowOf(usages[date to packageName])

    override fun getAllUsageForDate(date: String): Flow<List<DailyUsage>> =
        flowOf(usages.values.filter { it.date == date })

    override fun getUsageHistory(packageName: String): Flow<List<DailyUsage>> =
        flowOf(
            usages.values
                .filter { it.packageName == packageName }
                .sortedByDescending { it.date }
        )

    override suspend fun insertOrUpdateUsage(usage: DailyUsage) {
        usages[usage.date to usage.packageName] = usage
    }

    override suspend fun addUsageTime(
        date: String,
        packageName: String,
        seconds: Long,
        timestamp: Long
    ) {
        val key = date to packageName
        val current = usages[key]
        usages[key] = DailyUsage(
            date = date,
            packageName = packageName,
            usedTimeInSeconds = (current?.usedTimeInSeconds ?: 0L) + seconds,
            lastUpdated = timestamp
        )
    }

    override suspend fun updateUsageTime(
        date: String,
        packageName: String,
        seconds: Long,
        timestamp: Long
    ) {
        val key = date to packageName
        val current = usages[key] ?: return
        usages[key] = current.copy(
            usedTimeInSeconds = seconds,
            lastUpdated = timestamp
        )
    }

    override suspend fun deleteUsageForDate(date: String) {
        usages.keys.removeAll { it.first == date }
    }

    override suspend fun deleteUsageBeforeDate(date: String) {
        lastDeleteBeforeDate = date
        usages.keys.removeAll { it.first < date }
    }
}
