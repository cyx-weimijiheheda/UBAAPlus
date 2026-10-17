package cn.edu.ubaa.plus.api.auth

import kotlinx.serialization.Serializable

@Serializable
enum class AppUpdateStatus {
  UP_TO_DATE,
  UPDATE_AVAILABLE,
  UNKNOWN_LATEST_VERSION,
}

@Serializable
data class AppVersionCheckResponse(
    val latestVersion: String,
    val status: AppUpdateStatus,
    val updateAvailable: Boolean,
    val downloadUrl: String,
    val releaseNotes: String? = null,
    val serverVersion: String? = null,
    val aligned: Boolean? = null,
)
