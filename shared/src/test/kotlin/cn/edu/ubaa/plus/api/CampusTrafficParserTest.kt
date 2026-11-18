package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.feature.parseCampusTraffic
import kotlin.test.*

class CampusTrafficParserTest {
  private fun page(packageHtml: String = "", free: String = "20G") =
      """
    <table><thead><tr>
    <th data-col-seq="9">产品名称</th><th data-col-seq="4">已用流量</th>
    <th data-col-seq="6">免费流量剩余（不含套餐）</th><th data-col-seq="2">计费流量剩余(不含套餐)</th>
    </tr></thead><tbody><tr><td data-col-seq="2">1T</td><td data-col-seq="6">$free</td>
    <td data-col-seq="9">合成产品</td><td data-col-seq="4">1.25G</td></tr>
    $packageHtml
    </tbody></table>
  """

  @Test
  fun readsReorderedColumnsBySequence() {
    val result = parseCampusTraffic(page()).products.single()
    assertEquals("合成产品", result.name)
    assertEquals("1.25", result.used.value)
    assertEquals("20", result.freeRemaining.value)
    assertEquals("T", result.billableRemaining.unit)
  }

  @Test
  fun nestedPackagesAreNotProductsAndZeroDaysDoNotDiscardAvailableTraffic() {
    val nested =
        """<table><thead><tr><th data-col-seq="1">套餐名称</th><th data-col-seq="3">有效期</th><th data-col-seq="8">统计</th></tr></thead>
      <tbody><tr><td data-col-seq="1">合成免费套餐</td><td data-col-seq="3">0天</td><td data-col-seq="8"><button>可用流量：512M</button></td></tr></tbody></table>"""
    val html = page().replace("合成产品</td>", "合成产品$nested</td>")
    val result = parseCampusTraffic(html).products.single()
    assertEquals("20.5", result.freeRemaining.value)
    assertEquals("G", result.freeRemaining.unit)
  }

  @Test
  fun malformedOrAmbiguousPagesFailInsteadOfShowingInventedZeroes() {
    assertFailsWith<ApiCallException> { parseCampusTraffic("<html>统一身份认证</html>") }
    assertFailsWith<ApiCallException> { parseCampusTraffic(page(free = "unknown")) }
    assertFailsWith<ApiCallException> { parseCampusTraffic(page() + page()) }
    assertFailsWith<ApiCallException> {
      parseCampusTraffic(page().replace("data-col-seq=\"6\"", "data-col-seq=\"4\""))
    }
  }
}
