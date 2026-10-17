package cn.edu.ubaa.plus.api.venue

import kotlin.test.Test
import kotlin.test.assertEquals

class VenueCryptoTest {

  @Test
  fun `点击坐标归一化到 310x155`() {
    val normalized =
        VenueCrypto.normalizePoints(
            points = listOf(VenuePoint(2f, 4f), VenuePoint(620f, 310f)),
            imageWidth = 620,
            imageHeight = 310,
        )

    assertEquals(listOf(VenuePoint(1f, 2f), VenuePoint(310f, 155f)), normalized)
  }

  @Test
  fun `紧凑 JSON 与 Python 一致`() {
    assertEquals(
        "[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]",
        VenueCrypto.pointJson(listOf(VenuePoint(1f, 2f), VenuePoint(3f, 4f))),
    )
  }

  @Test
  fun `AES-ECB 加密结果与 Python 一致`() {
    assertEquals(
        "jOOa+o3uL/kUX7vNzBx8sA==",
        VenueCrypto.encryptEcbBase64("[{\"x\":1,\"y\":2}]", "abcdefghijklmnop"),
    )
  }

  @Test
  fun `captchaVerification 与 Python 一致`() {
    val challenge =
        VenueCaptchaChallenge(
            token = "tok",
            secretKey = "abcdefghijklmnop",
            wordList = listOf("a", "b"),
            imageBase64 = "",
        )

    val payload =
        VenueCrypto.captchaPayload(
            challenge = challenge,
            points = listOf(VenuePoint(2f, 4f)),
            imageWidth = 620,
            imageHeight = 310,
        )

    assertEquals("jOOa+o3uL/kUX7vNzBx8sA==", payload.pointJson)
    assertEquals(
        "bc7Ch2UMqaxzORj6TNCZt4YECh0lEfloMzeHPdk+UGU=",
        payload.verification,
    )
  }

  @Test
  fun `orderPin 与 Python 一致`() {
    assertEquals("75c073a8fb0672f5684aa62b08be1891", VenueCrypto.orderPin())
  }

  @Test
  fun `预约 JSON 的 ID 必须是字符串`() {
    assertEquals(
        "[{\"spaceId\":\"236\",\"timeId\":\"7700\"},{\"spaceId\":\"237\",\"timeId\":\"7701\",\"venueSpaceGroupId\":9}]",
        VenueCrypto.selectionJson(
            listOf(
                VenueSelection(236, 7700, null),
                VenueSelection(237, 7701, 9),
            )
        ),
    )
  }
}
