package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuthorizedUserEntity
import com.example.data.local.entity.BackupHistoryEntity
import com.example.data.local.entity.SyncConflictEntity
import com.example.repository.SchoolRepository
import com.example.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class SyncUiState {
    object Idle : SyncUiState()
    data class Authenticating(val message: String = "Google অ্যাকাউন্টে লগইন করা হচ্ছে...") : SyncUiState()
    data class CheckingRemote(val message: String = "Google Drive (AppData) অনুসন্ধান করা হচ্ছে...") : SyncUiState()
    data class BackingUp(val message: String = "Google Drive এ ব্যাকআপ আপলোড হচ্ছে...", val progress: Float = 0f) : SyncUiState()
    data class Restoring(val message: String = "Google Drive থেকে তথ্য রিস্টোর করা হচ্ছে...", val progress: Float = 0f) : SyncUiState()
    data class Success(val message: String) : SyncUiState()
    data class Error(val error: String) : SyncUiState()
    data class ConsentNeeded(val intent: Intent) : SyncUiState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val googleDriveManager = GoogleDriveManager(application)
    val repository = SchoolRepository(db)
    val syncManager = MultiUserSyncManager.getInstance(application, db, repository, googleDriveManager)

    init {
        repository.syncManager = syncManager
    }

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private val _accountEmail = MutableStateFlow(googleDriveManager.getAccountEmail())
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _accountName = MutableStateFlow(googleDriveManager.getAccountName())
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    private val _accountPhotoUrl = MutableStateFlow(googleDriveManager.getAccountPhotoUrl())
    val accountPhotoUrl: StateFlow<String?> = _accountPhotoUrl.asStateFlow()

    private val _isSignedIn = MutableStateFlow(googleDriveManager.isSignedIn())
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _hasCompletedAuthOnboarding = MutableStateFlow(googleDriveManager.hasCompletedAuthOnboarding())
    val hasCompletedAuthOnboarding: StateFlow<Boolean> = _hasCompletedAuthOnboarding.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(googleDriveManager.getLastSyncTime())
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _lastBackupSize = MutableStateFlow(googleDriveManager.getLastBackupSize())
    val lastBackupSize: StateFlow<Long> = _lastBackupSize.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(googleDriveManager.isAutoBackupEnabled())
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _backupFrequency = MutableStateFlow(googleDriveManager.getBackupFrequency())
    val backupFrequency: StateFlow<String> = _backupFrequency.asStateFlow()

    private val _wifiOnly = MutableStateFlow(googleDriveManager.isWifiOnly())
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _remoteFileInfo = MutableStateFlow<DriveFileInfo?>(null)
    val remoteFileInfo: StateFlow<DriveFileInfo?> = _remoteFileInfo.asStateFlow()

    // Multi-User Real-time Sync Properties
    val syncState: StateFlow<SyncState> = syncManager.syncState
    val isOnline: StateFlow<Boolean> = syncManager.isOnline
    val statusMessage: StateFlow<String> = syncManager.statusMessage
    val scriptUrl: StateFlow<String> = syncManager.scriptUrl
    val currentUserRole: StateFlow<String> = syncManager.currentUserRole

    val pendingCount: StateFlow<Int> = syncManager.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val authorizedUsers: StateFlow<List<AuthorizedUserEntity>> = syncManager.allAuthorizedUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conflicts: StateFlow<List<SyncConflictEntity>> = syncManager.allConflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = syncManager.backupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStudentsCount: StateFlow<Int> = repository.allStudents
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userMessage = MutableStateFlow<String?>(null)

    init {
        refreshAccountState()
        if (googleDriveManager.isSignedIn()) {
            checkRemoteBackup()
        }
    }

    fun refreshAccountState() {
        _accountEmail.value = googleDriveManager.getAccountEmail()
        _accountName.value = googleDriveManager.getAccountName()
        _accountPhotoUrl.value = googleDriveManager.getAccountPhotoUrl()
        _isSignedIn.value = googleDriveManager.isSignedIn()
        _lastBackupTime.value = googleDriveManager.getLastSyncTime()
        _lastBackupSize.value = googleDriveManager.getLastBackupSize()
        _autoBackupEnabled.value = googleDriveManager.isAutoBackupEnabled()
        _backupFrequency.value = googleDriveManager.getBackupFrequency()
        _wifiOnly.value = googleDriveManager.isWifiOnly()
    }

    /**
     * Handles result from Native Google Sign In Activity Result Contract.
     */
    fun onGoogleSignInResult(data: Intent?) {
        _uiState.value = SyncUiState.Authenticating()
        val result = googleDriveManager.handleSignInResult(data)
        result.onSuccess { account ->
            refreshAccountState()
            val email = account.email ?: googleDriveManager.getAccountEmail() ?: ""
            _uiState.value = SyncUiState.Success("সফলভাবে Google অ্যাকাউন্ট সংযুক্ত হয়েছে: $email")
            userMessage.value = "Google অ্যাকাউন্ট সংযুক্ত হয়েছে: $email"
            checkRemoteBackup()
            viewModelScope.launch {
                syncManager.triggerSync()
            }
        }.onFailure { err ->
            refreshAccountState()
            _uiState.value = SyncUiState.Idle
            userMessage.value = "Google সাইন-ইন সম্পন্ন হয়নি"
        }
    }

    fun checkRemoteBackup() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.CheckingRemote()
            when (val res = googleDriveManager.checkExistingDatabase()) {
                is DriveOperationResult.Success -> {
                    _remoteFileInfo.value = res.data
                    if (res.data != null) {
                        _lastBackupSize.value = res.data.size
                    }
                    _uiState.value = SyncUiState.Idle
                }
                is DriveOperationResult.ConsentRequired -> {
                    _uiState.value = SyncUiState.ConsentNeeded(res.consentIntent)
                }
                is DriveOperationResult.Error -> {
                    _uiState.value = SyncUiState.Error(res.message)
                }
                else -> {
                    _uiState.value = SyncUiState.Idle
                }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val success = syncManager.triggerSync()
            if (success) {
                userMessage.value = "ক্লাউড সিঙ্ক সফলভাবে সম্পন্ন হয়েছে"
            } else {
                userMessage.value = syncManager.statusMessage.value
            }
        }
    }

    fun backupNow(note: String = "ম্যানুয়াল ব্যাকআপ") {
        viewModelScope.launch {
            _uiState.value = SyncUiState.BackingUp("বিদ্যালয়ের ডাটাবেস প্রস্তুত করা হচ্ছে...", 0.2f)
            val result = syncManager.createManualBackup(note)
            if (result.isSuccess) {
                _lastBackupTime.value = System.currentTimeMillis()
                _uiState.value = SyncUiState.Success(result.getOrNull() ?: "ব্যাকআপ সফল হয়েছে!")
                userMessage.value = "Google Drive এ ব্যাকআপ সফল হয়েছে!"
            } else {
                _uiState.value = SyncUiState.Error("ব্যাকআপ ব্যর্থ হয়েছে")
                userMessage.value = "ব্যাকআপ ব্যর্থ: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun restoreBackup(targetBackupId: String? = null) {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Restoring("Google Drive থেকে ডাটাবেস ডাউনলোড করা হচ্ছে...", 0.3f)
            val result = syncManager.restoreBackupWithSafetySnapshot(targetBackupId)
            if (result.isSuccess) {
                _lastBackupTime.value = System.currentTimeMillis()
                _uiState.value = SyncUiState.Success(result.getOrNull() ?: "রিস্টোর সম্পন্ন হয়েছে!")
                userMessage.value = "ডাটাবেস রিস্টোর সম্পন্ন হয়েছে।"
            } else {
                _uiState.value = SyncUiState.Error("রিস্টোর সম্পন্ন করা যায়নি")
                userMessage.value = "রিস্টোর ত্রুটি: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun setScriptUrl(url: String) {
        syncManager.setScriptUrl(url)
        userMessage.value = "Google Apps Script ব্যাকএন্ড URL সংরক্ষিত হয়েছে"
    }

    fun saveAuthorizedUser(user: AuthorizedUserEntity) {
        viewModelScope.launch {
            syncManager.saveAuthorizedUser(user)
            userMessage.value = "ব্যবহারকারী সংরক্ষিত হয়েছে: ${user.displayName.ifEmpty { user.email }}"
        }
    }

    fun deleteAuthorizedUser(email: String) {
        viewModelScope.launch {
            syncManager.deleteAuthorizedUser(email)
            userMessage.value = "ব্যবহারকারী তালিকা থেকে বাদ দেওয়া হয়েছে"
        }
    }

    fun syncAuthorizedUsers() {
        viewModelScope.launch {
            val res = syncManager.syncAuthorizedUsersFromBackend()
            if (res) {
                userMessage.value = "ব্যবহারকারী তালিকা ব্যাকএন্ডের সাথে সিঙ্ক করা হয়েছে"
            }
        }
    }

    fun getSampleBackendScript(): String {
        return syncManager.getSampleBackendScript()
    }

    fun signOut() {
        viewModelScope.launch {
            googleDriveManager.signOut()
            refreshAccountState()
            _remoteFileInfo.value = null
            _uiState.value = SyncUiState.Idle
            userMessage.value = "Google অ্যাকাউন্ট বিচ্ছিন্ন করা হয়েছে"
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        googleDriveManager.setAutoBackupEnabled(enabled)
        _autoBackupEnabled.value = enabled
    }

    fun setBackupFrequency(frequency: String) {
        googleDriveManager.setBackupFrequency(frequency)
        _backupFrequency.value = frequency
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        googleDriveManager.setWifiOnly(wifiOnly)
        _wifiOnly.value = wifiOnly
    }

    fun completeAuthOnboarding() {
        googleDriveManager.setAuthOnboardingCompleted(true)
        _hasCompletedAuthOnboarding.value = true
    }

    fun resetAuthOnboarding() {
        googleDriveManager.setAuthOnboardingCompleted(false)
        _hasCompletedAuthOnboarding.value = false
    }

    fun signInWithDirectEmail(email: String, name: String = "") {
        val ok = googleDriveManager.signInWithDirectEmail(email, name)
        if (ok) {
            refreshAccountState()
            _hasCompletedAuthOnboarding.value = true
            _uiState.value = SyncUiState.Success("সফলভাবে অ্যাকাউন্ট সংযুক্ত হয়েছে: $email")
            userMessage.value = "Google অ্যাকাউন্ট সংযুক্ত হয়েছে: $email"
            viewModelScope.launch {
                syncManager.triggerSync()
            }
        }
    }

    fun dismissState() {
        _uiState.value = SyncUiState.Idle
    }
}
