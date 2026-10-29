package cn.edu.ubaa.plus.api.venue

/** 非运动场地统一归入的分类。 */
const val VENUE_OTHER_CATEGORY = "其他"

private val nonSportKeywords =
    listOf(
        "艺术",
        "党课",
        "讲座",
        "会议",
        "培训",
        "活动",
        "展览",
        "展厅",
        "演出",
        "剧场",
        "影院",
        "其他",
    )

private val sportCategoryOrder =
    listOf(
        "羽毛球",
        "乒乓球",
        "网球",
        "篮球",
        "足球",
        "排球",
        "游泳",
        "田径",
        "健身",
        "台球",
        "瑜伽",
        "舞蹈",
        "武术",
        "跆拳道",
        "轮滑",
        "攀岩",
        "射箭",
        "击剑",
        "壁球",
        "飞盘",
    )

/** 校区的展示名；上游没有填校区时给出稳定占位。 */
fun VenueSite.campusLabel(): String = campusName.trim().ifBlank { "未标注校区" }

/** 场馆的展示名；上游没有填场馆时退回项目名。 */
fun VenueSite.venueLabel(): String =
    venueName.trim().ifBlank { siteName.trim() }.ifBlank { "未标注场馆" }

/**
 * 场馆下拉的统一文案。
 *
 * 选中按钮和菜单项必须使用同一段文字，否则选中后开放时间/收费信息会“消失”。 场馆名与项目名相同时不重复显示。
 */
fun VenueSite.venueOptionLabel(): String = buildString {
  val venue = venueLabel()
  append(venue)
  val name = siteName.trim()
  if (name.isNotEmpty() && name != venue) {
    append(" · ")
    append(name)
  }
  append(openRange)
  append(if (isFee) " · 收费" else " · 免费")
}

/**
 * 运动分类。
 *
 * 优先只看 `sportName`；只有它为空时才回退到场馆/项目名。艺术类、党课等非运动场地归入「其他」， 避免把「学生活动中心」这类正常场馆名误判。
 */
fun VenueSite.sportCategoryLabel(): String {
  val sport = sportName.trim()
  if (sport.isNotEmpty()) {
    return if (nonSportKeywords.any { sport.contains(it) }) VENUE_OTHER_CATEGORY else sport
  }
  val fallback = siteName.trim().ifBlank { venueName.trim() }
  if (fallback.isEmpty() || nonSportKeywords.any { fallback.contains(it) }) {
    return VENUE_OTHER_CATEGORY
  }
  return fallback
}

fun venueCampusOptions(sites: List<VenueSite>): List<String> =
    sites.map { it.campusLabel() }.distinct().sortedWith(compareBy({ campusRank(it) }, { it }))

fun venueSportOptions(sites: List<VenueSite>, campus: String): List<String> =
    sites
        .filter { it.campusLabel() == campus }
        .map { it.sportCategoryLabel() }
        .distinct()
        .sortedWith(compareBy({ sportRank(it) }, { it }))

fun venueSiteOptions(
    sites: List<VenueSite>,
    campus: String,
    sport: String,
): List<VenueSite> =
    sites
        .filter { it.campusLabel() == campus && it.sportCategoryLabel() == sport }
        .sortedWith(compareBy({ it.venueLabel() }, { it.siteName }, { it.id }))

private fun campusRank(label: String): Int =
    when {
      label.startsWith("学院路") -> 0
      label.startsWith("沙河") -> 1
      label.startsWith("杭州") -> 2
      label == "未标注校区" -> 3
      else -> 4
    }

private fun sportRank(label: String): Int {
  if (label == VENUE_OTHER_CATEGORY) return Int.MAX_VALUE
  val index = sportCategoryOrder.indexOf(label)
  return if (index >= 0) index else sportCategoryOrder.size
}
