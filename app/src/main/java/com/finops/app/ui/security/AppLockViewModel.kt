package com.finops.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finops.app.domain.usecase.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class LockState { LOCKED, UNLOCKED }

/**
 * Verrouillage de l'app : verrouillée au lancement si l'option est active, puis à chaque retour
 * après plus de [GRACE_MILLIS] en arrière-plan. null = préférences pas encore lues.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow<LockState?>(null)
    val state: StateFlow<LockState?> = _state.asStateFlow()

    private var enabled = false
    private var backgroundAt: Long? = null

    init {
        viewModelScope.launch {
            observeSettings().collect { s ->
                enabled = s.biometricLock
                when {
                    // Premier chargement : verrouillée si l'option est active.
                    _state.value == null -> _state.value = if (enabled) LockState.LOCKED else LockState.UNLOCKED
                    // Option coupée : plus de verrou. Option activée en cours d'usage : l'utilisateur vient de s'authentifier.
                    !enabled -> _state.value = LockState.UNLOCKED
                }
            }
        }
    }

    fun onBackground() {
        if (enabled && _state.value == LockState.UNLOCKED) backgroundAt = clock.millis()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        if (enabled && clock.millis() - since > GRACE_MILLIS) _state.value = LockState.LOCKED
    }

    fun onUnlocked() {
        _state.value = LockState.UNLOCKED
    }

    private companion object {
        /** Retour rapide (choix d'un fichier, lien ouvert) sans redemander l'empreinte. */
        const val GRACE_MILLIS = 30_000L
    }
}
