package cn.edu.ubaa.plus.ui.screens.bykc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.local.LocalPrivateRead
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.ui.common.components.UbaaTextButton
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal sealed interface BykcDescriptionBlock {
  data class Paragraph(val text: String) : BykcDescriptionBlock

  data class Picture(val url: String, val description: String) : BykcDescriptionBlock
}

internal fun bykcImageUrl(source: String): String? =
    runCatching {
          require(source.isNotBlank())
          val uri = URI("https://bykc.buaa.edu.cn/system/course-select").resolve(source.trim())
          require(
              uri.scheme in listOf("http", "https") &&
                  !uri.host.isNullOrBlank() &&
                  uri.userInfo == null
          )
          // Upgrade the school image transport; public HTTPS posters use the anonymous Coil loader.
          if (uri.host.equals("bykc.buaa.edu.cn", true)) {
            require(uri.port in listOf(-1, 80, 443))
            "https://bykc.buaa.edu.cn${uri.rawPath}" + (uri.rawQuery?.let { "?$it" } ?: "")
          } else {
            require(uri.scheme == "https")
            uri.toString()
          }
        }
        .getOrNull()

internal fun parseBykcDescription(
    html: String,
    imagesAsLineBreaks: Boolean = false,
): List<BykcDescriptionBlock> {
  val blocks = mutableListOf<BykcDescriptionBlock>()
  val text = StringBuilder()
  fun flush() {
    text
        .toString()
        .replace('\u00a0', ' ')
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { blocks += BykcDescriptionBlock.Paragraph(it) }
    text.clear()
  }
  fun visit(node: Node) {
    when (node) {
      is TextNode -> text.append(node.wholeText)
      is Element -> {
        when (node.normalName()) {
          "script",
          "style",
          "iframe",
          "object" -> return
          "img" -> {
            if (imagesAsLineBreaks) {
              text.append('\n')
              return
            }
            flush()
            val url = bykcImageUrl(node.attr("src").ifBlank { node.attr("data-src") })
            if (url != null) blocks += BykcDescriptionBlock.Picture(url, node.attr("alt"))
            else node.attr("alt").takeIf { it.isNotBlank() }?.let { text.append(it) }
            return
          }
          "br" -> {
            text.append('\n')
            return
          }
        }
        if (node.isBlock && text.isNotEmpty()) text.append('\n')
        node.childNodes().forEach(::visit)
        if (node.isBlock) text.append('\n')
      }
    }
  }
  visit(Jsoup.parseBodyFragment(html).body())
  flush()
  return blocks
}

@Composable
internal fun BykcCourseDescription(html: String) {
  val blocks = remember(html) { parseBykcDescription(html) }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    blocks.forEach { block ->
      when (block) {
        is BykcDescriptionBlock.Paragraph ->
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        is BykcDescriptionBlock.Picture -> key(block.url) { BykcDescriptionImage(block) }
      }
    }
  }
}

@Composable
private fun BykcDescriptionImage(block: BykcDescriptionBlock.Picture) {
  val scope = remember { SchoolReadCoordinator.capture() }
  val schoolImage = URI(block.url).host.equals("bykc.buaa.edu.cn", true)
  var retry by remember(block.url) { mutableIntStateOf(0) }
  var failed by remember(block.url, retry) { mutableStateOf(false) }
  var ratio by remember(block.url) { mutableFloatStateOf(1f) }
  var bytes by remember(block.url, retry) { mutableStateOf<ByteArray?>(null) }
  LaunchedEffect(block.url, retry) {
    if (!schoolImage) return@LaunchedEffect
    try {
      check(scope.isCurrent())
      val response =
          LocalUpstreamClientProvider.shared().get(localUpstreamUrl(block.url)) {
            attributes.put(LocalPrivateRead, true)
            header("Referer", localUpstreamUrl("https://bykc.buaa.edu.cn/system/course-select"))
          }
      check(response.status.value == 200)
      val data = response.bodyAsChannel().readRemaining(12L * 1024 * 1024 + 1).readByteArray()
      check(data.isNotEmpty() && data.size <= 12 * 1024 * 1024 && scope.isCurrent())
      bytes = data
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    }
  }
  Column(Modifier.fillMaxWidth()) {
    if (failed) {
      Text(block.description.ifBlank { "图片加载失败" }, style = MaterialTheme.typography.bodySmall)
      UbaaTextButton(onClick = { retry++ }) { Text("重新加载图片") }
    } else if (!schoolImage || bytes != null) {
      AsyncImage(
          model =
              ImageRequest.Builder(LocalContext.current)
                  .data(if (schoolImage) bytes else block.url)
                  .memoryCacheKey("bykc:${scope.owner}:${block.url}:$retry")
                  .memoryCachePolicy(if (schoolImage) CachePolicy.DISABLED else CachePolicy.ENABLED)
                  .diskCachePolicy(CachePolicy.DISABLED)
                  .build(),
          contentDescription = block.description.ifBlank { "课程简介图片" },
          modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
          contentScale = ContentScale.FillWidth,
          onSuccess = { result ->
            val image = result.result.image
            if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
          },
          onError = { failed = true },
      )
    } else {
      Text("图片加载中…", style = MaterialTheme.typography.bodySmall)
    }
  }
}

internal fun bykcDescriptionClipboardText(html: String): String =
    parseBykcDescription(html, imagesAsLineBreaks = true)
        .joinToString("\n") { block ->
          when (block) {
            is BykcDescriptionBlock.Paragraph -> block.text
            is BykcDescriptionBlock.Picture -> ""
          }
        }
        .trim()
