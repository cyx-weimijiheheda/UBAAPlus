package cn.edu.ubaa.plus.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import cn.edu.ubaa.plus.model.dto.BykcUserProfileDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ProfileMetricsState(
    val profile: BykcUserProfileDto? = FeatureReadCache.get("bykc", "profile"),
    val card: CampusCardBalanceDto? = FeatureReadCache.get("card", "balance"),
    val network: ItPersonalDetailDto? = FeatureReadCache.get("network", "fee"),
    val loading: Boolean = false,
    val cardError: Boolean = false,
    val networkError: Boolean = false,
)

internal class ProfileMetricsViewModel(
    private val bykc: BykcApi = BykcApi(),
    private val card: CampusCardApi = CampusCardApi(),
    private val portal: ItPortalApi = ItPortalApi(),
) : ViewModel() {
  private val mutable = MutableStateFlow(ProfileMetricsState())
  val state = mutable.asStateFlow()

  fun refresh(online: Boolean) {
    if (!online || mutable.value.loading) return
    val scope = SchoolReadCoordinator.capture()
    mutable.value = mutable.value.copy(loading = true)
    viewModelScope.launch {
      val profile = async { bykc.getProfile().getOrNull() }
      val balance = async { card.getBalance() }
      val fee = async {
        SchoolReadCoordinator.read("network", "fee") { portal.findPersonalValue("net.netFee") }
      }
      val profileResult = profile.await()
      val cardResult = balance.await()
      val feeResult = fee.await()
      if (!scope.isCurrent()) return@launch
      mutable.value =
          mutable.value.copy(
              profile = profileResult ?: mutable.value.profile,
              card = cardResult.getOrNull() ?: mutable.value.card,
              network = feeResult.getOrNull() ?: mutable.value.network,
              loading = false,
              cardError = cardResult.isFailure,
              networkError = feeResult.isFailure,
          )
    }
  }
}
