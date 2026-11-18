package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import java.math.BigDecimal
import java.math.RoundingMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val labels = listOf("产品名称", "已用流量", "免费流量剩余(不含套餐)", "计费流量剩余(不含套餐)")
private val units = listOf("byte", "K", "M", "G", "T")

private fun invalid(): Nothing =
    throw ApiCallException("校园网流量页面格式已变化，请打开自助服务查看", code = "network_format")

private fun Element.ownTable() = parents().firstOrNull { it.tagName() == "table" }

private fun label(text: String) =
    text.replace("（", "(").replace("）", ")").replace(Regex("\\s+"), "")

private fun amount(text: String): CampusTrafficAmount {
  val match =
      Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*(byte|[KMGT]B?)$", RegexOption.IGNORE_CASE)
          .matchEntire(text.trim()) ?: invalid()
  val rawUnit = match.groupValues[2]
  return CampusTrafficAmount(
      match.groupValues[1],
      if (rawUnit.equals("byte", true)) "byte" else rawUnit.take(1).uppercase(),
  )
}

internal fun parseCampusTraffic(html: String): CampusTrafficDto {
  val candidates =
      Jsoup.parse(html).select("table").filter { table ->
        table.select("thead th").any { it.ownTable() == table && label(it.text()) == labels[1] }
      }
  if (candidates.size != 1) invalid()
  val table = candidates.single()
  val headers =
      table.select("thead th").filter { it.ownTable() == table && label(it.text()) in labels }
  if (headers.size != labels.size) invalid()
  val columns = headers.associate { label(it.text()) to it.attr("data-col-seq") }
  if (
      columns.size != labels.size ||
          columns.values.any { it.isBlank() } ||
          columns.values.toSet().size != labels.size
  )
      invalid()
  val products =
      table
          .select("tbody > tr")
          .filter { it.ownTable() == table }
          .mapNotNull { row ->
            val cells = row.children().filter { it.tagName() == "td" && it.hasAttr("data-col-seq") }
            if (cells.isEmpty()) return@mapNotNull null
            val bySequence = cells.associateBy { it.attr("data-col-seq") }
            if (bySequence.size != cells.size) invalid()
            fun cell(index: Int) = bySequence[columns[labels[index]]] ?: invalid()
            val name =
                cell(0)
                    .clone()
                    .also { it.select("table").remove() }
                    .text()
                    .trim()
                    .takeIf { it.isNotEmpty() && it.length <= 512 } ?: invalid()
            val free = amount(cell(2).text())
            val all = mutableListOf(free)
            for (nested in row.select("table")) {
              val nestedHeaders = nested.select("thead th").filter { it.ownTable() == nested }
              if (nestedHeaders.none { label(it.text()) == "套餐名称" }) continue
              val stats =
                  nestedHeaders
                      .singleOrNull { label(it.text()) == "统计" }
                      ?.attr("data-col-seq")
                      ?.takeIf { it.isNotEmpty() } ?: invalid()
              for (packageRow in nested.select("tbody > tr").filter { it.ownTable() == nested }) {
                val packageCells = packageRow.children().filter { it.tagName() == "td" }
                if (packageCells.isEmpty()) continue
                val statsCell =
                    packageCells.singleOrNull { it.attr("data-col-seq") == stats } ?: invalid()
                val available =
                    statsCell
                        .select("button")
                        .filter { it.ownTable() == nested }
                        .map { it.text() }
                        .filter { it.startsWith("可用流量：") || it.startsWith("可用流量:") }
                        .singleOrNull() ?: invalid()
                all += amount(available.drop(5))
              }
            }
            val rank = all.maxOf { units.indexOf(it.unit) }
            val sum =
                all.fold(BigDecimal.ZERO) { acc, value ->
                  acc +
                      value.value
                          .toBigDecimal()
                          .divide(
                              BigDecimal(1024).pow(rank - units.indexOf(value.unit)),
                              12,
                              RoundingMode.HALF_UP,
                          )
                }
            CampusTrafficProduct(
                name,
                amount(cell(1).text()),
                CampusTrafficAmount(
                    sum.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                    units[rank],
                ),
                amount(cell(3).text()),
            )
          }
  if (products.isEmpty()) invalid()
  return CampusTrafficDto(products)
}
