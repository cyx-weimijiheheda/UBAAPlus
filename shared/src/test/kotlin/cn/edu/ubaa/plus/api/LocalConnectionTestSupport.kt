package cn.edu.ubaa.plus.api

import kotlinx.coroutines.sync.Mutex

internal val localConnectionTestMutex = Mutex()
