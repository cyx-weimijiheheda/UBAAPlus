package cn.edu.ubaa.plus.api.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class VenueParsersTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `解析场地列表并过滤不支持预约的项目`() {
    val data =
        json
            .parseToJsonElement(
                """
                {
                  "advanceReservationDays": 3,
                  "venueSiteInfo": {
                    "10": [
                      {"id": 5, "siteName": "羽毛球", "venueId": 10, "venueName": "体育馆",
                       "campusName": "学院路校区", "sportName": "羽毛球",
                       "openStartDate": "07:00", "openEndDate": "22:00",
                       "isSupportReservation": true, "isFee": 1},
                      {"id": 6, "siteName": "篮球", "isSupportReservation": false}
                    ]
                  }
                }
                """
                    .trimIndent()
            )
            .jsonObject

    val result = parseVenueSiteList(data)

    assertEquals(3, result.advanceReservationDays)
    assertEquals(1, result.sites.size)
    val site = result.sites.single()
    assertEquals(5, site.id)
    assertEquals(10, site.venueId)
    assertEquals("学院路校区 · 体育馆 · 羽毛球", site.displayName)
    assertTrue(site.isFee)
  }

  @Test
  fun `解析当日可用信息`() {
    val data =
        json
            .parseToJsonElement(
                """
                {
                  "token": "order-token",
                  "isFee": 1,
                  "spaceTimeInfo": [
                    {"id": 7700, "beginTime": "19:00:00", "endTime": "20:00:00"}
                  ],
                  "reservationDateSpaceInfo": {
                    "2026-09-26": [
                      {"id": 236, "spaceName": "3号", "venueSpaceGroupId": null,
                       "7700": {"reservationStatus": 1, "orderFee": 25.0, "adminRemark": null}}
                    ]
                  },
                  "orderParamView": {
                    "phone": "13800000000",
                    "isCaptchaCheck": 1,
                    "buddyList": [{"id": 9, "name": "张三", "userUid": "263*16"}]
                  },
                  "siteParam": {"buddyNumMin": 1, "buddyNumMax": 4, "reservationHour": 1}
                }
                """
                    .trimIndent()
            )
            .jsonObject

    val day = assertNotNull(parseVenueDayInfo(data, siteId = 5, date = "2026-09-26"))

    assertEquals("order-token", day.orderToken)
    assertEquals(listOf(VenueTimeSlot(7700, "19:00", "20:00")), day.slots)
    assertEquals(1, day.spaces.size)
    val cell = assertNotNull(day.spaces.single().slot(7700))
    assertEquals(VenueSpaceSlot.STATUS_FREE, cell.status)
    assertEquals(25.0, cell.fee)
    assertEquals("13800000000", day.phone)
    assertTrue(day.isCaptchaCheck)
    assertEquals("张三", day.buddies.single().name)
    assertEquals(4, day.buddyNumMax)
  }

  @Test
  fun `没有时段或场地时返回 null`() {
    val data = json.parseToJsonElement("""{"spaceTimeInfo": []}""").jsonObject

    assertNull(parseVenueDayInfo(data, 1, "2026-09-26"))
  }

  @Test
  fun `解析同伴列表`() {
    val element =
        json.parseToJsonElement(
            """{"content":[{"id":9,"name":"张三","userUid":"263*16","userId":1,"userRoleId":2}]}"""
        )

    val buddies = parseVenueBuddies(element)

    assertEquals(1, buddies.size)
    assertEquals("263*16", buddies.single().userUid)
    assertEquals(2, buddies.single().roleId)
  }

  @Test
  fun `解析点击文字验证码`() {
    val data =
        json
            .parseToJsonElement(
                """{"repCode":"0000","repData":{"token":"t","secretKey":"k","wordList":["一","二","三"],"originalImageBase64":"abc"}}"""
            )
            .jsonObject

    val challenge = assertNotNull(parseVenueCaptcha(data))
    assertEquals("t", challenge.token)
    assertEquals(listOf("一", "二", "三"), challenge.wordList)
    assertEquals("abc", challenge.imageBase64)
  }

  @Test
  fun `下单表单字段与 Python 客户端一致`() {
    val form =
        buildVenueSubmitForm(
            VenueOrderRequest(
                siteId = 5,
                date = "2026-09-26",
                selections = listOf(VenueSelection(236, 7700, null)),
                phone = "13800000000",
                buddyIds = listOf(9, 10),
                orderPrice = 25.0,
                captchaVerification = "verification",
                captchaToken = "captcha-token",
                orderToken = "order-token",
            )
        )

    assertEquals(5, form["venueSiteId"])
    assertEquals("2026-09-26", form["reservationDate"])
    assertEquals("[{\"spaceId\":\"236\",\"timeId\":\"7700\"}]", form["reservationOrderJson"])
    assertEquals("13800000000", form["phone"])
    assertEquals("9,10", form["buddyIds"])
    assertEquals("", form["buddyUids"])
    assertEquals("2026-09-26", form["weekStartDate"])
    assertEquals(-1, form["reservationType"])
    assertEquals("75c073a8fb0672f5684aa62b08be1891", form["orderPin"])
    assertEquals(25.0, form["orderPrice"])
    assertEquals("verification", form["captchaVerification"])
    assertEquals("captcha-token", form["captchaToken"])
    assertEquals("order-token", form["token"])
  }

  @Test
  fun `免费场地不发送 orderPrice 且空同伴不发送 buddyIds`() {
    val form =
        buildVenueSubmitForm(
            VenueOrderRequest(
                siteId = 5,
                date = "2026-09-26",
                selections = listOf(VenueSelection(236, 7700, null)),
                phone = null,
                buddyIds = emptyList(),
                orderPrice = null,
                captchaVerification = null,
                captchaToken = null,
                orderToken = "",
            )
        )

    assertFalse(form.containsKey("phone"))
    assertFalse(form.containsKey("buddyIds"))
    assertFalse(form.containsKey("orderPrice"))
    assertFalse(form.containsKey("token"))
  }

  @Test
  fun `解析会员码并兼容字符串形式的 isDynamicCode`() {
    val data =
        json
            .parseToJsonElement(
                """
                {
                  "qrCode": "iVBORw0KGgo=",
                  "dueDate": "2026-10-17 21:40:00",
                  "isDynamicCode": "1",
                  "currDate": "2026-10-17 21:39:50"
                }
                """
                    .trimIndent()
            )
            .jsonObject

    val result = parseVenueMembershipCode(data)

    assertEquals("iVBORw0KGgo=", result.qrCode)
    assertEquals("2026-10-17 21:40:00", result.dueDate)
    assertTrue(result.isDynamicCode)
    assertEquals("2026-10-17 21:39:50", result.currDate)
  }

  @Test
  fun `没有会员卡时 qrCode 为空`() {
    val data = json.parseToJsonElement("""{"isDynamicCode": false}""").jsonObject

    val result = parseVenueMembershipCode(data)

    assertNull(result.qrCode)
    assertNull(result.dueDate)
    assertFalse(result.isDynamicCode)
  }
}
