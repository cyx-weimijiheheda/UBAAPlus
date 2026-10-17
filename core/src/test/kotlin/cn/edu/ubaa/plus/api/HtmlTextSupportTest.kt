package cn.edu.ubaa.plus.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlTextSupportTest {
  @Test
  fun `html sanitizer returns readable plain text`() {
    val plainText =
        HtmlTextSupport.toPlainText(
            "<h4>&nbsp;Lab1</h4>\n<p>- Solve a real-world problem.</p><p>- Parallelize it in R/Python.</p>"
        )

    assertEquals("Lab1 - Solve a real-world problem. - Parallelize it in R/Python.", plainText)
  }

  @Test
  fun `blank input yields null`() {
    assertNull(HtmlTextSupport.toPlainText(null))
    assertNull(HtmlTextSupport.toPlainText(""))
    assertNull(HtmlTextSupport.toPlainText("   <p>&nbsp;</p> "))
  }
}
