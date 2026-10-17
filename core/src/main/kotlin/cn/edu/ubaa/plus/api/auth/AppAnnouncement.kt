package cn.edu.ubaa.plus.api.auth

import kotlinx.serialization.Serializable

@Serializable
data class AppAnnouncement(
    val id: String,
    val title: String,
    val content: String,
    val confirmText: String? = null,
    val linkUrl: String? = null,
)
