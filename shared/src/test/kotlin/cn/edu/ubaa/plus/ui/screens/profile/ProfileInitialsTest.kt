package cn.edu.ubaa.plus.ui.screens.profile

import kotlin.test.*

class ProfileInitialsTest {
  @Test
  fun nameFallback() {
    assertEquals("王", profileInitials("王小明"))
    assertEquals("AL", profileInitials("Ada Lovelace"))
    assertEquals("AD", profileInitials("ada"))
    assertEquals("", profileInitials("  "))
  }
}
