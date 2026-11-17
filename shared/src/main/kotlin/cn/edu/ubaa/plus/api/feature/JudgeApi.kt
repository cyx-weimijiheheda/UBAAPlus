package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.model.dto.JudgeAssignmentDetailDto
import cn.edu.ubaa.plus.model.dto.JudgeAssignmentDetailKeyDto
import cn.edu.ubaa.plus.model.dto.JudgeAssignmentDetailsResponse
import cn.edu.ubaa.plus.model.dto.JudgeAssignmentsResponse

interface JudgeApiBackend {
  suspend fun getAssignments(
      includeExpired: Boolean = false,
      userKey: String? = null,
  ): Result<JudgeAssignmentsResponse>

  suspend fun getAssignmentDetail(
      courseId: String,
      assignmentId: String,
  ): Result<JudgeAssignmentDetailDto>

  suspend fun getAssignmentDetails(
      keys: List<JudgeAssignmentDetailKeyDto>
  ): Result<JudgeAssignmentDetailsResponse>
}

/** 希冀作业查询 API。 */
open class JudgeApi(
    private val backendProvider: () -> JudgeApiBackend = {
      ConnectionRuntime.apiFactory().judgeApi()
    }
) {
  internal constructor(backend: JudgeApiBackend) : this({ backend })

  private fun currentBackend(): JudgeApiBackend = backendProvider()

  /** 获取所有课程下的希冀作业摘要。 */
  open suspend fun getAssignments(
      includeExpired: Boolean = false,
      userKey: String? = null,
  ): Result<JudgeAssignmentsResponse> {
    return SchoolReadCoordinator.read("judge", "assignments:$includeExpired") {
      currentBackend().getAssignments(includeExpired, userKey)
    }
  }

  /** 获取指定课程下的指定作业详情。 */
  open suspend fun getAssignmentDetail(
      courseId: String,
      assignmentId: String,
  ): Result<JudgeAssignmentDetailDto> {
    return SchoolReadCoordinator.read("judge", "detail:$courseId:$assignmentId") {
      currentBackend().getAssignmentDetail(courseId, assignmentId)
    }
  }

  /** 批量获取希冀作业详情，用于列表摘要的增量补全。 */
  open suspend fun getAssignmentDetails(
      keys: List<JudgeAssignmentDetailKeyDto>
  ): Result<JudgeAssignmentDetailsResponse> {
    return SchoolReadCoordinator.read(
        "judge",
        "details:${keys.joinToString { "${it.courseId}:${it.assignmentId}" }}",
        cache = false,
    ) {
      currentBackend().getAssignmentDetails(keys)
    }
  }
}
