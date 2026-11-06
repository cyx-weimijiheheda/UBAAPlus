package cn.edu.ubaa.plus.ui.screens.exam

import cn.edu.ubaa.plus.model.dto.Exam
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExamClipboardTest {

  @Test
  fun `完整考试信息包含所有字段`() {
    val exam =
        Exam(
            courseName = "高等数学",
            courseNo = "A001",
            examTimeDescription = "第16周",
            examDate = "2026-11-20",
            startTime = "09:00",
            endTime = "11:00",
            examPlace = "主楼 201",
            examSeatNo = "12",
            week = 16,
            examStatus = 0,
            examType = "闭卷",
        )

    val text = exam.toClipboardText()

    assertTrue(text.contains("高等数学"))
    assertTrue(text.contains("课程号：A001"))
    assertTrue(text.contains("2026.11.20"))
    assertTrue(text.contains("时间：09:00-11:00"))
    assertTrue(text.contains("地点：主楼 201"))
    assertTrue(text.contains("座位：12号"))
    assertTrue(text.contains("周次：第16周"))
    assertTrue(text.contains("状态：未开始"))
  }

  @Test
  fun `空字段不输出`() {
    val text = Exam(courseName = "大学物理").toClipboardText()

    assertTrue(text.contains("大学物理"))
    assertFalse(text.contains("地点"))
    assertFalse(text.contains("座位"))
  }
}
