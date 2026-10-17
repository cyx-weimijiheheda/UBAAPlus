package cn.edu.ubaa.plus.api.core

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.AuthServiceBackend
import cn.edu.ubaa.plus.api.auth.UserServiceBackend
import cn.edu.ubaa.plus.api.feature.BykcApiBackend
import cn.edu.ubaa.plus.api.feature.CgyyApiBackend
import cn.edu.ubaa.plus.api.feature.ClassroomApiBackend
import cn.edu.ubaa.plus.api.feature.EvaluationServiceBackend
import cn.edu.ubaa.plus.api.feature.GradeApiBackend
import cn.edu.ubaa.plus.api.feature.JudgeApiBackend
import cn.edu.ubaa.plus.api.feature.LibBookApiBackend
import cn.edu.ubaa.plus.api.feature.ScheduleApiBackend
import cn.edu.ubaa.plus.api.feature.SigninApiBackend
import cn.edu.ubaa.plus.api.feature.SpocApiBackend
import cn.edu.ubaa.plus.api.feature.YgdkApiBackend
import cn.edu.ubaa.plus.api.local.LocalAuthServiceBackend
import cn.edu.ubaa.plus.api.local.LocalBykcApiBackend
import cn.edu.ubaa.plus.api.local.LocalCgyyApiBackend
import cn.edu.ubaa.plus.api.local.LocalClassroomApiBackend
import cn.edu.ubaa.plus.api.local.LocalEvaluationServiceBackend
import cn.edu.ubaa.plus.api.local.LocalGradeApiBackend
import cn.edu.ubaa.plus.api.local.LocalJudgeApiBackend
import cn.edu.ubaa.plus.api.local.LocalLibBookApiBackend
import cn.edu.ubaa.plus.api.local.LocalScheduleApiBackend
import cn.edu.ubaa.plus.api.local.LocalSigninApiBackend
import cn.edu.ubaa.plus.api.local.LocalSpocApiBackend
import cn.edu.ubaa.plus.api.local.LocalUserServiceBackend
import cn.edu.ubaa.plus.api.local.LocalYgdkApiBackend

interface ApiFactory {
  fun authService(): AuthServiceBackend

  fun userService(): UserServiceBackend

  fun scheduleApi(): ScheduleApiBackend

  fun signinApi(): SigninApiBackend

  fun spocApi(): SpocApiBackend

  fun judgeApi(): JudgeApiBackend

  fun bykcApi(): BykcApiBackend

  fun cgyyApi(): CgyyApiBackend

  fun ygdkApi(): YgdkApiBackend

  fun classroomApi(): ClassroomApiBackend

  fun evaluationService(): EvaluationServiceBackend

  fun gradeApi(): GradeApiBackend

  fun libBookApi(): LibBookApiBackend
}

internal object DefaultApiFactory : ApiFactory {
  private val directBackends = LocalBackendSet()
  private val webVpnBackends = LocalBackendSet()

  private fun mode(): ConnectionMode = ConnectionRuntime.currentMode() ?: ConnectionMode.DIRECT

  fun clearCachedBackends() {
    directBackends.clearCache()
    webVpnBackends.clearCache()
  }

  override fun authService(): AuthServiceBackend = localBackends(mode()).authService

  override fun userService(): UserServiceBackend = localBackends(mode()).userService

  override fun scheduleApi(): ScheduleApiBackend = localBackends(mode()).scheduleApi

  override fun signinApi(): SigninApiBackend = localBackends(mode()).signinApi

  override fun spocApi(): SpocApiBackend = localBackends(mode()).spocApi

  override fun judgeApi(): JudgeApiBackend = localBackends(mode()).judgeApi

  override fun bykcApi(): BykcApiBackend = localBackends(mode()).bykcApi

  override fun cgyyApi(): CgyyApiBackend = localBackends(mode()).cgyyApi

  override fun ygdkApi(): YgdkApiBackend = localBackends(mode()).ygdkApi

  override fun classroomApi(): ClassroomApiBackend = localBackends(mode()).classroomApi

  override fun evaluationService(): EvaluationServiceBackend =
      localBackends(mode()).evaluationService

  override fun gradeApi(): GradeApiBackend = localBackends(mode()).gradeApi

  override fun libBookApi(): LibBookApiBackend = localBackends(mode()).libBookApi

  private fun localBackends(mode: ConnectionMode): LocalBackendSet =
      when (mode) {
        ConnectionMode.DIRECT -> directBackends
        ConnectionMode.WEBVPN -> webVpnBackends
      }

  private class LocalBackendSet {
    val authService = LocalAuthServiceBackend()
    val userService = LocalUserServiceBackend()
    val scheduleApi = LocalScheduleApiBackend()
    val signinApi = LocalSigninApiBackend()
    val spocApi = LocalSpocApiBackend()
    val judgeApi = LocalJudgeApiBackend()
    val bykcApi = LocalBykcApiBackend()
    val cgyyApi = LocalCgyyApiBackend()
    val ygdkApi = LocalYgdkApiBackend()
    val classroomApi = LocalClassroomApiBackend()
    val evaluationService = LocalEvaluationServiceBackend()
    val gradeApi = LocalGradeApiBackend()
    val libBookApi = LocalLibBookApiBackend()

    fun clearCache() {
      signinApi.clearCache()
      spocApi.clearCache()
      judgeApi.clearCache()
      bykcApi.clearCache()
      cgyyApi.clearCache()
      ygdkApi.clearCache()
      classroomApi.clearCache()
      evaluationService.clearCache()
      libBookApi.clearCache()
    }
  }
}
