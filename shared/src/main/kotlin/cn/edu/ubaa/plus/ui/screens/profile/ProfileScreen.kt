package cn.edu.ubaa.plus.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.AppInfo
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.model.dto.UserInfo
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 个人中心。
 *
 * 首页并列的顶层页面：展示身份信息、随机小骚话、账号资料、设置/关于入口、版本与连接模式、作者链接与退出登录。
 */
@Composable
fun ProfileScreen(
    userData: UserData,
    userInfo: UserInfo?,
    connectionMode: ConnectionMode,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uriHandler = LocalUriHandler.current
  val quips = remember { profileQuips() }
  var quipIndex by remember { mutableIntStateOf(Random.nextInt(quips.size)) }
  val nextQuip = { quipIndex = (quipIndex + 1 + Random.nextInt(quips.size - 1)) % quips.size }

  LaunchedEffect(Unit) {
    while (true) {
      delay(8_000)
      quipIndex = (quipIndex + 1) % quips.size
    }
  }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
              modifier =
                  Modifier.size(72.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
          ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize().padding(10.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
              text = userData.name,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
              text = userData.schoolid,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
          )
        }
      }
    }

    item {
      Card(
          modifier = Modifier.fillMaxWidth().clickable { nextQuip() },
          colors =
              CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = quips[quipIndex],
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "换一句",
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    }

    item {
      ProfileSection(title = "账号资料") {
        ProfileInfoRow("姓名", userData.name)
        ProfileInfoRow("学号", userData.schoolid)
        if (userInfo == null) {
          Text(
              text = "正在加载账号资料…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          userInfo.username?.let { ProfileInfoRow("用户名", it) }
          userInfo.phone?.let { ProfileInfoRow("手机号", it) }
          userInfo.email?.let { ProfileInfoRow("邮箱", it) }
          userInfo.idCardTypeName?.let { ProfileInfoRow("证件类型", it) }
          userInfo.idCardNumber?.let { ProfileInfoRow("证件号码", maskIdCard(it)) }
        }
      }
    }

    item {
      ProfileSection {
        ProfileActionRow(Icons.Default.Settings, "设置", onSettingsClick)
        HorizontalDivider()
        ProfileActionRow(Icons.Default.Info, "关于", onAboutClick)
      }
    }

    item {
      ProfileSection(title = "应用信息") {
        ProfileInfoRow("版本", AppInfo.version)
        ProfileInfoRow("连接模式", connectionMode.displayName)
      }
    }

    item {
      ProfileSection(title = "开发者") {
        ProfileActionRow(Icons.Default.Code, "作者 GitHub") {
          uriHandler.openUri("https://github.com/cyx-weimijiheheda")
        }
        HorizontalDivider()
        ProfileActionRow(Icons.Default.Code, "项目仓库") {
          uriHandler.openUri("https://github.com/cyx-weimijiheheda/UBAAPlus")
        }
      }
    }

    item {
      OutlinedButton(
          onClick = onLogoutClick,
          modifier = Modifier.fillMaxWidth(),
          colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("退出登录")
      }
    }
  }
}

@Composable
private fun ProfileSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
      }
      content()
    }
  }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun ProfileActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.width(12.dp))
    Text(text = title, style = MaterialTheme.typography.bodyLarge)
  }
}

/** 对证件号进行脱敏处理。 */
private fun maskIdCard(idCard: String): String =
    if (idCard.length >= 8) {
      idCard.substring(0, 4) + "****" + idCard.substring(idCard.length - 4)
    } else {
      idCard
    }

private fun profileQuips(): List<String> =
    listOf(
        "今天的你，也在努力成为明天的卷王。",
        "课表可以空，梦想不能空。",
        "早八不是敌人，被窝才是。",
        "写代码一时爽，一直写一直爽。",
        "北航的风很大，记得把头发吹成理想的样子。",
        "食堂的队很长，人生的路更长。",
        "别卷了，去操场走两步吧。",
        "生活不止眼前的苟且，还有明天的 DDL。",
        "你已经很棒了，剩下的交给绩点。",
        "今天也要做一条快乐的咸鱼。",
        "代码跑不通的时候，就先跑个步。",
        "沙河的晚霞，值得你抬头看一眼。",
        "世界很大，但你的选课系统依旧很卡。",
        "万事胜意，包括今晚的抢课。",
        "闹钟响了三次，梦想醒了零次。",
        "实验报告不会自己写，但 DDL 会自己来。",
        "别问，问就是还在赶 due。",
        "图书馆的座位很抢手，你的自律也是。",
        "今天多学一分钟，明天少慌一小时。",
        "人生就像选课，手速和心态缺一不可。",
        "愿你的绩点像火箭，愿你的头发像森林。",
        "上课困了就想想，下课就能吃饭了。",
        "北航的秋天很短，短到只够谈一场恋爱和赶一次 due。",
        "把日子过成想要的样子，而不是课表的样子。",
        "跑完这圈，就去吃夜宵。",
        "你不是懒，你只是在为下一次爆发蓄力。",
        "考试周的你，比任何励志电影都热血。",
        "生活给你关上一扇门，顺便把空调也关了。",
        "别怕，最坏的结果也就是再选一次课。",
        "世界那么大，先去操场看看。",
        "今天也要相信，抢课系统会在最后一秒放票。",
        "前程似锦，也别忘了按时吃饭。",
        "你负责努力，剩下的交给运气和网速。",
        "心情不好的时候，就去看看飞机起飞。",
        "所有的熬夜，都会变成明天的黑眼圈和学分。",
        "保持热爱，奔赴下一场考试。",
        "慢一点也没关系，你又不是在抢课。",
        "生活不易，但食堂的鸡腿饭还在。",
        "愿你所求皆如愿，所抢皆有票。",
        "今天的风很温柔，适合去操场走一走。",
    )
