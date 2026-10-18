package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.FeatureLayout
import cn.edu.ubaa.plus.api.storage.ThemeMode
import cn.edu.ubaa.plus.api.storage.ThemePalette
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesStoreTest {
  @BeforeTest
  fun setup() {
    AppPreferencesStore.settings = MapSettings()
    AppPreferencesStore.resetFeatureCustomization()
    AppPreferencesStore.setThemePalette(ThemePalette.BUAA_BLUE)
    AppPreferencesStore.setThemeMode(ThemeMode.SYSTEM)
    AppPreferencesStore.setFollowWallpaper(false)
    AppPreferencesStore.setFeatureLayout(FeatureLayout.GRID)
    AppPreferencesStore.setDormDefaults("", "", "")
  }

  @Test
  fun `默认偏好正确`() {
    val state = AppPreferencesStore.state.value
    assertEquals(ThemePalette.BUAA_BLUE, state.themePalette)
    assertEquals(ThemeMode.SYSTEM, state.themeMode)
    assertFalse(state.followWallpaper)
    assertEquals(FeatureLayout.GRID, state.featureLayout)
    assertTrue(state.featureOrder.isEmpty())
    assertTrue(state.hiddenFeatures.isEmpty())
  }

  @Test
  fun `主题与布局会持久化到 flow`() {
    AppPreferencesStore.setThemePalette(ThemePalette.PKU_RED)
    AppPreferencesStore.setThemeMode(ThemeMode.DARK)
    AppPreferencesStore.setFollowWallpaper(true)
    AppPreferencesStore.setFeatureLayout(FeatureLayout.COMPACT_LIST)

    val state = AppPreferencesStore.state.value
    assertEquals(ThemePalette.PKU_RED, state.themePalette)
    assertEquals(ThemeMode.DARK, state.themeMode)
    assertTrue(state.followWallpaper)
    assertEquals(FeatureLayout.COMPACT_LIST, state.featureLayout)
  }

  @Test
  fun `功能顺序去重并过滤空白`() {
    AppPreferencesStore.setFeatureOrder(listOf("judge", " ", "judge", "schedule"))

    assertEquals(listOf("judge", "schedule"), AppPreferencesStore.state.value.featureOrder)
  }

  @Test
  fun `同时更新顺序与隐藏集合`() {
    AppPreferencesStore.updateFeatureCustomization(
        order = listOf("judge", "schedule", "judge", ""),
        hidden = setOf("judge"),
    )

    val state = AppPreferencesStore.state.value
    assertEquals(listOf("judge", "schedule"), state.featureOrder)
    assertEquals(setOf("judge"), state.hiddenFeatures)
  }

  @Test
  fun `隐藏功能与寝室默认值`() {
    AppPreferencesStore.setHiddenFeatures(setOf("judge", "judge", ""))
    AppPreferencesStore.setDormDefaults(" 学院路校区 ", " 3号楼 ", " 101 ")

    val state = AppPreferencesStore.state.value
    assertEquals(setOf("judge"), state.hiddenFeatures)
    assertEquals("学院路校区", state.dormCampus)
    assertEquals("3号楼", state.dormBuilding)
    assertEquals("101", state.dormRoom)
  }
}
