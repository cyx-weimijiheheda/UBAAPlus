package cn.edu.ubaa.plus.reminder

import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderSeenStoreTest {
  @BeforeTest
  fun setup() {
    ReminderSeenStore.settings = MapSettings()
  }

  @Test
  fun `same key only notifies once`() {
    assertTrue(ReminderSeenStore.markIfNew("22373333", "iclass:c1:2026-11-06:sign"))
    assertFalse(ReminderSeenStore.markIfNew("22373333", "iclass:c1:2026-11-06:sign"))
  }

  @Test
  fun `users are isolated`() {
    ReminderSeenStore.markIfNew("u1", "key")

    assertTrue(ReminderSeenStore.isSeen("u1", "key"))
    assertFalse(ReminderSeenStore.isSeen("u2", "key"))
  }

  @Test
  fun `homework source seeding suppresses existing assignments`() {
    assertFalse(ReminderSeenStore.isSourceSeeded("u1", "spoc"))

    ReminderSeenStore.seedSource("u1", "spoc", listOf("spoc-new:a1"))

    assertTrue(ReminderSeenStore.isSourceSeeded("u1", "spoc"))
    assertTrue(ReminderSeenStore.isSeen("u1", "spoc-new:a1"))
    assertFalse(ReminderSeenStore.isSourceSeeded("u1", "judge"))
  }
}
