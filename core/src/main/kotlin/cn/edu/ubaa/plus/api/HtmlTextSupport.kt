package cn.edu.ubaa.plus.api

/**
 * 上游返回的 HTML 片段转纯文本。
 *
 * 原先放在 `local/LocalSpocSupport.kt` 的 `LocalSpocParsers` 里，SPOC 本地实现（shared）都要用，故提到 core 作为唯一实现。
 */
object HtmlTextSupport {
  fun toPlainText(html: String?): String? {
    if (html.isNullOrBlank()) return null
    return html
        .replace(Regex("(?i)<br\\s*/?>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .decodeHtmlEntities()
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { null }
  }

  private fun String.decodeHtmlEntities(): String =
      replace("&nbsp;", " ")
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .replace("&#x27;", "'")
}
