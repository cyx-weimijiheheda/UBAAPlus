package cn.edu.ubaa.plus.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.cdimascio.dotenv.dotenv
import java.time.Duration
import java.time.Instant
import java.util.Date

/** JWT 令牌处理工具。 负责用户身份令牌的签发、验证、解析以及过期检查。 */
object JwtUtil {

  private val dotenv = dotenv { ignoreIfMissing = true }

  /**
   * 从 .env 或环境变量读取 JWT 密钥。
   *
   * 不再提供默认值：此前未配置时会静默回退到硬编码的 `ubaa-dev-secret-unsafe`， 任何人只要知道这段源码就能伪造任意学号的 token。现在改为启动即失败，
   * 避免带着可预测密钥上线。
   */
  private fun resolveJwtSecret(): String {
    val secret = dotenv["JWT_SECRET"] ?: System.getenv("JWT_SECRET")
    require(!secret.isNullOrBlank()) {
      "JWT_SECRET 未配置。请在 .env 或环境变量中设置一个足够随机的密钥后再启动服务端，例如：\n" +
          "  JWT_SECRET=$(openssl rand -hex 32)"
    }
    require(secret.length >= 32) {
      "JWT_SECRET 过短（当前 ${secret.length} 字符），至少需要 32 字符。建议用：openssl rand -hex 32"
    }
    return secret
  }

  private val jwtSecret: String = resolveJwtSecret()

  val algorithm: Algorithm = Algorithm.HMAC256(jwtSecret)
  const val ISSUER = "ubaa-server"
  const val AUDIENCE = "ubaa-users"

  /**
   * 为指定用户生成 JWT 令牌。
   *
   * @param username 用户标识（学号）。
   * @param sessionTtl 令牌有效期。
   * @return 签名的 JWT 字符串。
   */
  fun generateToken(username: String, sessionTtl: Duration): String {
    val now = Instant.now()
    return JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withSubject(username)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(now.plus(sessionTtl)))
        .withClaim("username", username)
        .sign(algorithm)
  }

  /**
   * 验证令牌并提取其中的用户名。
   *
   * @param token 待验证的 JWT 字符串。
   * @return 用户名，若验证失败则返回 null。
   */
  fun validateTokenAndGetUsername(token: String): String? {
    return try {
      JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build().verify(token).subject
    } catch (_: JWTVerificationException) {
      null
    }
  }

  /** 检查令牌是否已过期。 */
  fun isTokenExpired(token: String): Boolean {
    return try {
      JWT.decode(token).expiresAt.before(Date())
    } catch (_: Exception) {
      true
    }
  }
}
