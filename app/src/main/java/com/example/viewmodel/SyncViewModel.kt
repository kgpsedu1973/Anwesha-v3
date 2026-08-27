package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.repository.SchoolRepository
import com.example.util.DriveFileInfo
import com.example.util.DriveOperationResult
import com.example.util.GoogleDriveManager
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
    val repository = SchoolRepository(db)
    val googleDriveManager = GoogleDriveManager(application)

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

    fun backupNow() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.BackingUp("বিদ্যালয়ের ডাটাবেস প্রস্তুত করা হচ্ছে...", 0.2f)
            val masterModel = repository.exportToMasterModel()
            val jsonString = masterModel.toJson(indent = true)
            val size = jsonString.toByteArray(Charsets.UTF_8).size.toLong()
            val totalRecords = masterModel.studentsList.size + masterModel.usersList.size + masterModel.attendanceList.size + masterModel.examResultsList.size

            _uiState.value = SyncUiState.BackingUp("Google Drive AppData তে আপলোড হচ্ছে...", 0.6f)

            when (val res = googleDriveManager.uploadDatabase(jsonString, totalRecords)) {
                is DriveOperationResult.Success -> {
                    _lastBackupTime.value = System.currentTimeMillis()
                    _lastBackupSize.value = size
                    _remoteFileInfo.value = res.data
                    _uiState.value = SyncUiState.Success("সফলভাবে Google Drive এ ব্যাকআপ সম্পন্ন হয়েছে!")
                    userMessage.value = "Google Drive এ ব্যাকআপ সফল হয়েছে!"
                }
                is DriveOperationResult.ConsentRequired -> {
                    _uiState.value = SyncUiState.ConsentNeeded(res.consentIntent)
                }
                is DriveOperationResult.Error -> {
                    _uiState.value = SyncUiState.Error("ব্যাকআপ ব্যর্থ: ${res.message}")
                    userMessage.value = "ব্যাকআপ ত্রুটি: ${res.message}"
                }
                is DriveOperationResult.Progress -> {
                    _uiState.value = SyncUiState.BackingUp(res.status, res.percentage)
                }
                else -> {
                    _uiState.value = SyncUiState.Idle
                }
            }
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Restoring("Google Drive থেকে ডাটাবেস ডাউনলোড করা হচ্ছে...", 0.3f)
            when (val res = googleDriveManager.downloadDatabase(repository = repository)) {
                is DriveOperationResult.Success -> {
                    _lastBackupTime.value = System.currentTimeMillis()
                    _uiState.value = SyncUiState.Success("সফলভাবে সমস্ত তথ্য পুনরুদ্ধার (Restore) সম্পন্ন হয়েছে!")
                    userMessage.value = "ডাটাবেস রিস্টোর সম্পন্ন! মোট ${res.data.studentsList.size} জন শিক্ষার্থী লোড হয়েছে।"
                }
                is DriveOperationResult.NotFound -> {
                    _uiState.value = SyncUiState.Error("কোনো ব্যাকআপ পাওয়া যায়নি")
                    userMessage.value = "কোনো ব্যাকআপ পাওয়া যায়নি"
                }
                is DriveOperationResult.ConsentRequired -> {
                    _uiState.value = SyncUiState.ConsentNeeded(res.consentIntent)
                }
                is DriveOperationResult.Error -> {
                    _uiState.value = SyncUiState.Error("রিস্টোর ব্যর্থ: ${res.message}")
                    userMessage.value = "রিস্টোর ত্রুটি: ${res.message}"
                }
                is DriveOperationResult.Progress -> {
                    _uiState.value = SyncUiState.Restoring(res.status, res.percentage)
                }
            }
        }
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

    fun dismissState() {
        _uiState.value = SyncUiState.Idle
    }
}
