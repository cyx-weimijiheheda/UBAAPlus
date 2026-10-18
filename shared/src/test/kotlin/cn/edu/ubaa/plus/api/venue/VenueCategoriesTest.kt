package cn.edu.ubaa.plus.api.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenueCategoriesTest {
  private fun site(
      id: Int,
      campus: String,
      sport: String,
      venue: String,
      siteName: String,
  ): VenueSite =
      VenueSite(
          id = id,
          siteName = siteName,
          venueId = id,
          venueName = venue,
          campusName = campus,
          sportName = sport,
          openStart = "07:00",
          openEnd = "22:00",
          isFee = false,
      )

  private val sites =
      listOf(
          site(1, "学院路校区", "羽毛球", "体育馆", "羽毛球"),
          site(2, "学院路校区", "羽毛球", "体育馆", "羽毛球2"),
          site(3, "学院路校区", "篮球", "篮球场", "篮球"),
          site(4, "沙河校区", "乒乓球", "体育馆", "乒乓球"),
          site(5, "沙河校区", "艺术类", "艺术中心", "舞蹈教室"),
          site(6, "沙河校区", "党课", "学生活动中心", "党课教室"),
      )

  @Test
  fun `校区与运动类型按层级过滤`() {
    assertEquals(listOf("学院路校区", "沙河校区"), venueCampusOptions(sites))
    assertEquals(
        listOf("羽毛球", "篮球"),
        venueSportOptions(sites, "学院路校区"),
    )
    assertEquals(
        listOf(1, 2, 3),
        venueSiteOptions(sites, "学院路校区", "羽毛球").map { it.id } +
            venueSiteOptions(sites, "学院路校区", "篮球").map { it.id },
    )
  }

  @Test
  fun `艺术与党课归入其他且排在最后`() {
    val sports = venueSportOptions(sites, "沙河校区")
    assertEquals(listOf("乒乓球", "其他"), sports)
    assertEquals(
        setOf(5, 6),
        venueSiteOptions(sites, "沙河校区", VENUE_OTHER_CATEGORY).map { it.id }.toSet(),
    )
  }

  @Test
  fun `sportName 为空时才回退到场馆名`() {
    val fallback = site(7, "学院路校区", "", "体育馆", "羽毛球")
    assertEquals("羽毛球", fallback.sportCategoryLabel())

    val nonSportFallback = site(8, "学院路校区", "", "艺术中心", "展厅")
    assertEquals(VENUE_OTHER_CATEGORY, nonSportFallback.sportCategoryLabel())
  }

  @Test
  fun `正常场馆名不会被误判为其他`() {
    val site = site(9, "学院路校区", "篮球", "学生活动中心", "篮球场")
    assertEquals("篮球", site.sportCategoryLabel())
    assertTrue("篮球" != VENUE_OTHER_CATEGORY)
  }
}
