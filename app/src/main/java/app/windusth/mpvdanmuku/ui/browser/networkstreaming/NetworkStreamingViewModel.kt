package app.windusth.mpvdanmuku.ui.browser.networkstreaming

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.windusth.mpvdanmuku.domain.network.NetworkConnection
import app.windusth.mpvdanmuku.repository.NetworkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel for managing network connections
 * Follows MVVM pattern with proper separation of concerns
 */
class NetworkStreamingViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  private val repository: NetworkRepository by inject()

  /**
   * Observable list of all saved network connections
   */
  val connections: StateFlow<List<NetworkConnection>> =
    repository
      .getAllConnections()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  val connectionStatuses = repository.connectionStatuses

  /**
   * Add a new network connection
   */
  fun addConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.addConnection(connection)
    }
  }

  /**
   * Update an existing connection
   */
  fun updateConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.updateConnection(connection)
    }
  }

  /**
   * Delete a connection
   */
  fun deleteConnection(connection: NetworkConnection) {
    viewModelScope.launch {
      repository.deleteConnection(connection)
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          NetworkStreamingViewModel(application)
        }
      }
  }
}
