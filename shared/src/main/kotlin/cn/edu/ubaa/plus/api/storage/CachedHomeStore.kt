package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoItem
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoSource
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 只有已成功认证并确认归属的账号才能建立缓存首页入口；主动退出会撤销。 */
object CachedUserStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: SecureSettings.get().also { backing = it }
    set(value) {
      backing = value
      revision.value++
    }

  private val revision = MutableStateFlow(0L)
  val changes = revision.asStateFlow()
  private val json = Json { ignoreUnknownKeys = true }

  fun remember(user: UserData) {
    if (user.schoolid.isBlank()) return
    ScheduleStore.useAccount(user.schoolid)
    runCatching { settings.putString("cached_authenticated_user", json.encodeToString(user)) }
    revision.value++
  }

  fun migrateVerifiedAccount() {
    val owner = ScheduleStore.account() ?: return
    if (current() != null) return
    val session =
        runCatching { cn.edu.ubaa.plus.api.local.LocalAuthSessionStore.get() }.getOrNull() ?: return
    if (session.user.schoolid == owner) remember(session.user)
  }

  fun current(): UserData? =
      runCatching {
            json.decodeFromString<UserData>(settings.getString("cached_authenticated_user", ""))
          }
          .getOrNull()
          ?.takeIf { it.schoolid.isNotBlank() && it.schoolid == ScheduleStore.account() }

  fun clear() {
    settings.remove("cached_authenticated_user")
    revision.value++
  }
}

@Serializable
internal data class CachedHomeSnapshot(
    val owner: String,
    val todos: List<HomeTodoItem> = emptyList(),
    val signin: List<SigninClassDto> = emptyList(),
    val library: LibBookBookingDto? = null,
    val todayClasses: List<TodayClass> = emptyList(),
    val todayDate: String = "",
    val signinDate: String = "",
) {
  fun coursesOn(date: String): List<TodayClass> =
      if (date == todayDate) todayClasses else emptyList()

  fun signinOn(date: String): List<SigninClassDto> = if (date == signinDate) signin else emptyList()
}

internal object CachedHomeStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  private val json = Json { ignoreUnknownKeys = true }

  fun read(owner: String): CachedHomeSnapshot? {
    if (owner != ScheduleStore.account()) return null
    return runCatching {
          json.decodeFromString<CachedHomeSnapshot>(settings.getString("home_snapshot:$owner", ""))
        }
        .getOrNull()
        ?.takeIf { it.owner == owner }
  }

  fun write(snapshot: CachedHomeSnapshot) {
    if (snapshot.owner != ScheduleStore.account()) return
    settings.putString("home_snapshot:${snapshot.owner}", json.encodeToString(snapshot))
  }

  fun clear() {
    settings.keys.filter { it.startsWith("home_snapshot:") }.forEach(settings::remove)
  }
}

internal fun mergeHomeTodos(
    live: List<HomeTodoItem>,
    cached: List<HomeTodoItem>,
    retainedSources: Set<HomeTodoSource>,
): List<HomeTodoItem> =
    (live.filter { it.source !in retainedSources } + cached.filter { it.source in retainedSources })
        .distinctBy { it.id }
        .sortedWith(compareBy<HomeTodoItem> { it.sortTime == null }.thenBy { it.sortTime })
