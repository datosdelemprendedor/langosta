package com.langosta.mission.data.repository

import com.langosta.mission.data.api.OpenClawClient
import com.langosta.mission.domain.model.DashboardState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DashboardRepository(
    private val client: OpenClawClient
) {

    fun dashboardStream(): Flow<DashboardState> = flow {
        while (true) {
            val state = client.getDashboard()
            emit(state)
            delay(5_000)
        }
    }

    suspend fun getBootstrapConfig() = client.getBootstrapConfig()
}
