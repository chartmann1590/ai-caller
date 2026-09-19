package com.charles.localcallagent.benchmark

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {

    @Test
    fun testBenchmarkRunnerGeneratesReport() = runBlocking {
        val runner = BenchmarkRunner()
        val report = runner.runFullBenchmark()

        assertNotNull(report)
        assertTrue(report.overallScore > 50)
        assertTrue(report.decodeTokensPerSecond > 0)
        assertTrue(report.ttsFirstChunkMs >= 0)

        val csv = report.toCsv()
        assertTrue(csv.contains("overallScore"))
        assertTrue(csv.contains(report.overallScore.toString()))
    }
}
