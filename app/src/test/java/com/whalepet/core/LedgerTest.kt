package com.whalepet.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * 余额差值记账。
 *
 * 首次观测只建立基准，余额下降才累计，跨天归档，币种变化只重置基准。
 */
class LedgerTest {

    private fun newLedger(): Ledger {
        val file = File.createTempFile("usage_ledger", ".json")
        file.delete()
        return Ledger(file)
    }

    private fun noonOf(date: LocalDate): Long {
        return date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @Test
    fun firstObservationOnlySetsBaseline() = runBlocking {
        val ledger = newLedger()
        val record = ledger.observe(100.0, "CNY", noonOf(LocalDate.now()))
        assertEquals(0.0, record.todayUsage, 1e-9)
        assertEquals("CNY", record.lastCurrency)
    }

    @Test
    fun balanceDropAccumulatesUsage() = runBlocking {
        val ledger = newLedger()
        val today = noonOf(LocalDate.now())
        ledger.observe(100.0, "CNY", today)
        val record = ledger.observe(92.5, "CNY", today)
        assertEquals(7.5, record.todayUsage, 1e-9)
    }

    @Test
    fun balanceRiseDoesNotAccumulate() = runBlocking {
        val ledger = newLedger()
        val today = noonOf(LocalDate.now())
        ledger.observe(100.0, "CNY", today)
        ledger.observe(90.0, "CNY", today)
        val record = ledger.observe(120.0, "CNY", today)
        assertEquals(10.0, record.todayUsage, 1e-9)
    }

    @Test
    fun currencyChangeOnlyResetsBaseline() = runBlocking {
        val ledger = newLedger()
        val today = noonOf(LocalDate.now())
        ledger.observe(100.0, "CNY", today)
        val record = ledger.observe(50.0, "USD", today)
        assertEquals(0.0, record.todayUsage, 1e-9)
        assertEquals("USD", record.lastCurrency)
    }

    @Test
    fun newDayArchivesUsageAndResetsToday() = runBlocking {
        val ledger = newLedger()
        val yesterday = noonOf(LocalDate.now().minusDays(1))
        val today = noonOf(LocalDate.now())
        ledger.observe(100.0, "CNY", yesterday)
        ledger.observe(80.0, "CNY", yesterday)
        val record = ledger.observe(70.0, "CNY", today)
        assertEquals(0.0, record.todayUsage, 1e-9)
        assertEquals(1, record.history.size)
        assertEquals(20.0, record.history[0].usage, 1e-9)
        assertEquals(LocalDate.now().minusDays(1).toString(), record.history[0].date)
    }

    @Test
    fun usageSurvivesReloadFromFile() = runBlocking {
        val file = File.createTempFile("usage_ledger", ".json")
        file.delete()
        val today = noonOf(LocalDate.now())
        Ledger(file).observe(100.0, "CNY", today)
        Ledger(file).observe(88.0, "CNY", today)
        val record = Ledger(file).observe(88.0, "CNY", today)
        assertEquals(12.0, record.todayUsage, 1e-9)
    }
}
