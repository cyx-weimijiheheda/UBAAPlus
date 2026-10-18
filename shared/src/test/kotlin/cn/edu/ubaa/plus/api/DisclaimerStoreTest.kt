package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.storage.DisclaimerStore
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisclaimerStoreTest {
  @BeforeTest
  fun setUp() {
    DisclaimerStore.settings = MapSettings()
  }

  @Test
  fun `默认未接受`() {
    assertFalse(DisclaimerStore.isAccepted())
  }

  @Test
  fun `同意后持久化为已接受`() {
    DisclaimerStore.accept()

    assertTrue(DisclaimerStore.isAccepted())
  }
}
