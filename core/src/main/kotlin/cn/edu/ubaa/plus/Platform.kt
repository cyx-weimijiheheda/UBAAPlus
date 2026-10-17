package cn.edu.ubaa.plus

interface Platform {
  val name: String
}

class JvmPlatform : Platform {
  override val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform(): Platform = JvmPlatform()
