package cn.edu.ubaa.plus.api

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AutoTransportProbeTest {
  private val originalReachability = AutoTransportProbe.reachability

  @AfterTest
  fun tearDown() {
    AutoTransportProbe.reachability = originalReachability
  }

  @Test
  fun `campus reachable selects direct`() = runTest {
    AutoTransportProbe.reachability = { it == AutoTransportProbe.CAMPUS_PROBE_URL }

    assertEquals(ConnectionMode.DIRECT, AutoTransportProbe.resolve(previous = ConnectionMode.WEBVPN))
  }

  @Test
  fun `only webvpn reachable selects webvpn`() = runTest {
    AutoTransportProbe.reachability = { it == AutoTransportProbe.WEBVPN_PROBE_URL }

    assertEquals(ConnectionMode.WEBVPN, AutoTransportProbe.resolve(previous = ConnectionMode.DIRECT))
  }

  @Test
  fun `no probe reachable keeps previous transport`() = runTest {
    AutoTransportProbe.reachability = { false }

    assertEquals(ConnectionMode.WEBVPN, AutoTransportProbe.resolve(previous = ConnectionMode.WEBVPN))
    assertEquals(ConnectionMode.DIRECT, AutoTransportProbe.resolve(previous = null))
  }
}
