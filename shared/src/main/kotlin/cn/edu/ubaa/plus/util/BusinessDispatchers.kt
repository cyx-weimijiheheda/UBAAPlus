package cn.edu.ubaa.plus.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** 业务解析、加解密与磁盘访问不占用 UI 线程；测试可以注入确定性调度器。 */
internal object BusinessDispatchers {
  @Volatile var io: CoroutineDispatcher = Dispatchers.IO
}
