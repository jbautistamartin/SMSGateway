// SMS Gateway para Android
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

package com.capicua.smsgateway.presentation.dashboard

import androidx.lifecycle.ViewModel
import com.capicua.smsgateway.domain.model.SmsMessage
import com.capicua.smsgateway.domain.usecase.GetSmsListUseCase
import com.capicua.smsgateway.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getSmsListUseCase: GetSmsListUseCase,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    /** Lista reactiva de todos los SMS, más recientes primero. */
    val smsList: Flow<List<SmsMessage>> = getSmsListUseCase()

    /** Estado reactivo de conectividad de red. */
    val isOnline: Flow<Boolean> = networkMonitor.isOnline
}