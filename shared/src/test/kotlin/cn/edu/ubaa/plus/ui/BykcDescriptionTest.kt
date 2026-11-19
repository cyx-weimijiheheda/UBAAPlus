package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.model.dto.BykcCourseDetailDto
import cn.edu.ubaa.plus.model.dto.BykcCourseStatus
import cn.edu.ubaa.plus.ui.screens.bykc.*
import org.junit.Assert.*
import org.junit.Test

class BykcDescriptionTest {
  @Test
  fun `简介保留文字图片顺序并解码实体与相对地址`() {
    val blocks =
        parseBykcDescription(
            "<p>介绍 &amp; 说明<br>第二行</p><img src='/uploads/poster.png' alt='海报'><p>结尾</p>"
        )
    assertEquals(
        listOf(
            BykcDescriptionBlock.Paragraph("介绍 & 说明\n第二行"),
            BykcDescriptionBlock.Picture("https://bykc.buaa.edu.cn/uploads/poster.png", "海报"),
            BykcDescriptionBlock.Paragraph("结尾"),
        ),
        blocks,
    )
  }

  @Test
  fun `纯图片简介和延迟图片不会丢失`() {
    assertEquals(
        listOf(BykcDescriptionBlock.Picture("https://bykc.buaa.edu.cn/system/p.png", "")),
        parseBykcDescription("<p><img data-src='p.png'></p>"),
    )
    assertEquals("https://cdn.example.org/a.jpg", bykcImageUrl("//cdn.example.org/a.jpg"))
  }

  @Test
  fun `简介不执行嵌入代码且拒绝非网络图片`() {
    assertEquals(
        listOf(BykcDescriptionBlock.Paragraph("正文")),
        parseBykcDescription(
            "<script>secret()</script><style>body{}</style><iframe>hidden</iframe><p>正文</p><img src='javascript:alert(1)'>"
        ),
    )
    listOf(
            "",
            "file:///tmp/a",
            "data:image/png;base64,AA",
            "https://user:pass@bykc.buaa.edu.cn/a",
            "http://external.example/a",
        )
        .forEach { assertNull(bykcImageUrl(it)) }
  }

  @Test
  fun `复制简介仅保留文本并在图片处换行`() {
    assertEquals(
        "前文\n\n后文",
        bykcDescriptionClipboardText("<p>前文</p><img src='/p.png' alt='不复制'><p>后文</p>"),
    )
  }

  @Test
  fun `复制课程信息包含正文不泄露图片链接或HTML`() {
    val course =
        BykcCourseDetailDto(
            id = 1,
            courseName = "测试课程",
            status = BykcCourseStatus.PREVIEW,
            courseTeacher = "讲师",
            coursePosition = "教室",
            courseDesc = "<p>简介 &amp; 说明</p><img src='/p.png?token=private' alt='讲座海报'>",
        )
    val text = course.bykcClipboardText()
    assertTrue(text.contains("授课教师：讲师"))
    assertTrue(text.contains("上课地点：教室"))
    assertTrue(text.contains("简介 & 说明"))
    assertFalse(text.contains("讲座海报"))
    assertFalse(text.contains("<p>"))
    assertFalse(text.contains("token"))
  }
}
