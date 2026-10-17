package cn.edu.ubaa.plus.api.auth

import cn.edu.ubaa.plus.model.dto.UserData
import kotlinx.serialization.Serializable

@Serializable
data class SessionStatusResponse(
    val user: UserData,
    val lastActivity: String,
    val authenticatedAt: String,
)
