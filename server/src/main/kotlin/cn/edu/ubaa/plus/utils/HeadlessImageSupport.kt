package cn.edu.ubaa.plus.utils

import javax.imageio.ImageIO

internal object HeadlessImageSupport {
  private const val HEADLESS_PROPERTY = "java.awt.headless"

  fun ensureConfigured() {
    if (System.getProperty(HEADLESS_PROPERTY) != "true") {
      System.setProperty(HEADLESS_PROPERTY, "true")
    }
    ImageIO.setUseCache(false)
  }
}
