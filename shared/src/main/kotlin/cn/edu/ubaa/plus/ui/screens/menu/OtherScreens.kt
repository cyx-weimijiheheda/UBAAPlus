package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.AppInfo
import cn.edu.ubaa.plus.ui.common.components.FIRST_RUN_DISCLAIMER

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
  val uriHandler = LocalUriHandler.current

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
              text = "UBAAPlus",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = "版本：${AppInfo.version}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
              text =
                  "UBAAPlus 是个人自用的北京航空航天大学校园服务客户端，整合统一认证、课表、成绩、场馆与生活服务等常用入口，减少在多个网站之间反复切换和登录。它不是学校官方应用，也不代表学校立场。",
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    item {
      AboutSection(title = "主要功能") {
        AboutParagraph(
            "统一认证登录与账号资料、课程表、空教室查询、智慧教室签到、图书馆座位预约、图书借阅记录、SPOC 作业、博雅课程、运动场地与研讨室预约、阳光打卡、寝室用电、校园网络、校园卡、自动评教、考试与成绩查询、希冀作业。"
        )
        AboutParagraph(
            "支持直连与 WebVPN 两种连接方式；支持网格/紧凑列表两种功能页布局、五套配色与跟随壁纸、深浅色模式、功能排序与隐藏、入口长按在浏览器打开以及二维码/条形码扫描。"
        )
      }
    }

    item {
      AboutSection(title = "数据与隐私") {
        AboutParagraph("学号与密码只保存在本机的加密存储中，用于登录学校统一身份认证；应用不会把它们上传到开发者服务器，也不会写入日志。")
        AboutParagraph("课表、成绩、借阅记录、用电与校园网信息均直接来自学校提供的服务。网页跳转使用一次性 CAS ticket，ticket 不会被持久化保存。")
        AboutParagraph("应用会访问更新检查与公告接口以获取版本信息；除此之外不会收集与业务无关的个人数据。卸载应用会清除本机保存的登录凭据。")
      }
    }

    item {
      AboutSection(title = "免责声明") {
        FIRST_RUN_DISCLAIMER.forEach { paragraph -> AboutParagraph(paragraph) }
      }
    }

    item {
      AboutSection(title = "开源与技术栈") {
        AboutParagraph(
            "项目最初基于 BUAASubnet/UBAA，现已独立演进，只保留 Android 平台。客户端使用 Kotlin、Jetpack Compose、Ktor 与 kotlinx.serialization 构建，扫码功能基于 ZXing。"
        )
        AboutParagraph("项目以 MIT 许可证开源，欢迎提交 Issue 反馈问题或建议。")
      }
    }

    item {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          AboutLink("开源项目（GitHub）") {
            uriHandler.openUri("https://github.com/cyx-weimijiheheda/UBAAPlus")
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          AboutLink("反馈建议（Issues）") {
            uriHandler.openUri("https://github.com/cyx-weimijiheheda/UBAAPlus/issues")
          }
          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          AboutLink("作者 GitHub") { uriHandler.openUri("https://github.com/cyx-weimijiheheda") }
        }
      }
    }
  }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 8.dp),
      )
      content()
    }
  }
}

@Composable
private fun AboutParagraph(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(bottom = 8.dp),
  )
}

@Composable
private fun AboutLink(title: String, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = "打开",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
  }
}
