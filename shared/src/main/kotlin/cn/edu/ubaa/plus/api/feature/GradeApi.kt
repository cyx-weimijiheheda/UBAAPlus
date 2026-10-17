package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.ApiClientProvider
import cn.edu.ubaa.plus.api.auth.safeApiCall
import cn.edu.ubaa.plus.api.core.ApiClient
import cn.edu.ubaa.plus.model.dto.GradeData
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface GradeApiBackend {
  suspend fun getGrades(termCode: String): Result<GradeData>
}

class GradeApi(
    private val backendProvider: () -> GradeApiBackend = {
      ConnectionRuntime.apiFactory().gradeApi()
    }
) {
  internal constructor(backend: GradeApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayGradeApiBackend(apiClient) })

  private fun currentBackend(): GradeApiBackend = backendProvider()

  suspend fun getGrades(termCode: String): Result<GradeData> = currentBackend().getGrades(termCode)
}

internal class RelayGradeApiBackend(private val apiClient: ApiClient = ApiClientProvider.shared) :
    GradeApiBackend {
  override suspend fun getGrades(termCode: String): Result<GradeData> {
    return safeApiCall {
      apiClient.getClient().get("api/v1/grade/list") { parameter("termCode", termCode) }
    }
  }
}
