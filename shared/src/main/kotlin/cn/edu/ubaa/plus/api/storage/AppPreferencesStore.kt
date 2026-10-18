package cn.edu.ubaa.plus.api.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 应用可选的颜色主题。 */
enum class ThemePalette(val displayName: String) {
  BUAA_BLUE("北航蓝"),
  PKU_RED("北大红"),
  THU_PURPLE("清华紫"),
  BJFU_GREEN("北林绿"),
  ROOMMATE_PINK("室友粉"),
}

/** 功能列表的展示方式。 */
enum class FeatureLayout(val displayName: String) {
  GRID("网格"),
  COMPACT_LIST("紧凑列表"),
}

/** 非敏感的用户外观与功能偏好。 */
data class AppPreferences(
    val themePalette: ThemePalette = ThemePalette.BUAA_BLUE,
    val followWallpaper: Boolean = false,
    val featureLayout: FeatureLayout = FeatureLayout.GRID,
    val featureOrder: List<String> = emptyList(),
    val hiddenFeatures: Set<String> = emptySet(),
    val dormCampus: String = "",
    val dormBuilding: String = "",
    val dormRoom: String = "",
)

/** 主题、功能排序/显示、寝室默认值等偏好的统一存储。 */
object AppPreferencesStore {
  private const val KEY_THEME_PALETTE = "app_theme_palette"
  private const val KEY_FOLLOW_WALLPAPER = "app_follow_wallpaper"
  private const val KEY_FEATURE_LAYOUT = "app_feature_layout"
  private const val KEY_FEATURE_ORDER = "app_feature_order"
  private const val KEY_FEATURE_HIDDEN = "app_feature_hidden"
  private const val KEY_DORM_CAMPUS = "dorm_campus"
  private const val KEY_DORM_BUILDING = "dorm_building"
  private const val KEY_DORM_ROOM = "dorm_room"

  private val json = Json { ignoreUnknownKeys = true }
  private var _settings: Settings? = null

  var settings: Settings
    get() = _settings ?: Settings().also { _settings = it }
    set(value) {
      _settings = value
      _state.value = load()
    }

  private val _state by lazy { MutableStateFlow(load()) }
  val state: StateFlow<AppPreferences> by lazy { _state.asStateFlow() }

  fun reload() {
    _state.value = load()
  }

  fun setThemePalette(palette: ThemePalette) {
    settings.putString(KEY_THEME_PALETTE, palette.name)
    _state.value = _state.value.copy(themePalette = palette)
  }

  fun setFollowWallpaper(enabled: Boolean) {
    settings.putBoolean(KEY_FOLLOW_WALLPAPER, enabled)
    _state.value = _state.value.copy(followWallpaper = enabled)
  }

  fun setFeatureLayout(layout: FeatureLayout) {
    settings.putString(KEY_FEATURE_LAYOUT, layout.name)
    _state.value = _state.value.copy(featureLayout = layout)
  }

  fun setFeatureOrder(order: List<String>) {
    val sanitized = order.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    settings.putString(KEY_FEATURE_ORDER, json.encodeToString(sanitized))
    _state.value = _state.value.copy(featureOrder = sanitized)
  }

  fun setHiddenFeatures(hidden: Set<String>) {
    val sanitized = hidden.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    settings.putString(KEY_FEATURE_HIDDEN, json.encodeToString(sanitized.toList()))
    _state.value = _state.value.copy(hiddenFeatures = sanitized)
  }

  fun setDormDefaults(campus: String, building: String, room: String) {
    val normalizedCampus = campus.trim()
    val normalizedBuilding = building.trim()
    val normalizedRoom = room.trim()
    settings.putString(KEY_DORM_CAMPUS, normalizedCampus)
    settings.putString(KEY_DORM_BUILDING, normalizedBuilding)
    settings.putString(KEY_DORM_ROOM, normalizedRoom)
    _state.value =
        _state.value.copy(
            dormCampus = normalizedCampus,
            dormBuilding = normalizedBuilding,
            dormRoom = normalizedRoom,
        )
  }

  fun resetFeatureCustomization() {
    settings.remove(KEY_FEATURE_ORDER)
    settings.remove(KEY_FEATURE_HIDDEN)
    _state.value = _state.value.copy(featureOrder = emptyList(), hiddenFeatures = emptySet())
  }

  private fun load(): AppPreferences {
    val palette =
        runCatching { ThemePalette.valueOf(settings.getString(KEY_THEME_PALETTE, "")) }
            .getOrDefault(ThemePalette.BUAA_BLUE)
    val layout =
        runCatching { FeatureLayout.valueOf(settings.getString(KEY_FEATURE_LAYOUT, "")) }
            .getOrDefault(FeatureLayout.GRID)
    val order =
        runCatching {
              json.decodeFromString<List<String>>(settings.getString(KEY_FEATURE_ORDER, "[]"))
            }
            .getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    val hidden =
        runCatching {
              json.decodeFromString<List<String>>(settings.getString(KEY_FEATURE_HIDDEN, "[]"))
            }
            .getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    return AppPreferences(
        themePalette = palette,
        followWallpaper = settings.getBoolean(KEY_FOLLOW_WALLPAPER, false),
        featureLayout = layout,
        featureOrder = order,
        hiddenFeatures = hidden,
        dormCampus = settings.getString(KEY_DORM_CAMPUS, ""),
        dormBuilding = settings.getString(KEY_DORM_BUILDING, ""),
        dormRoom = settings.getString(KEY_DORM_ROOM, ""),
    )
  }
}
