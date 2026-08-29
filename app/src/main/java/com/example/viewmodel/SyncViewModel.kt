package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.usecase.AuthCheckResult
import com.example.domain.usecase.AuthenticateUserUseCase
import com.example.domain.usecase.CreateSchoolUseCase
import com.example.domain.usecase.JoinSchoolUseCase
import com.example.repository.SchoolRepository
import com.example.sync.backup.DriveBackupManager
import com.example.sync.config.SchoolConfigManager
import com.example.sync.engine.DriveSyncEngine
import com.example.sync.role.PermissionGuard
import com.example.sync.role.RoleAccessManager
import com.example.sync.role.UserRole
import com.example.sync.work.SyncWorkManager
import com.example.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SyncUiState {
    object Idle : SyncUiState()
    data class Authenticating(val message: String = "Google অ্যাকাউন্টে লগইন করা হচ্ছে...") : SyncUiState()
    data class CheckingRemote(val message: String = "Google Drive (AppData) অনুসন্ধান করা হচ্ছে...") : SyncUiState()
    data class BackingUp(val message: String = "Google Drive এ ব্যাকআপ আপলোড হচ্ছে...", val progress: Float = 0f) : SyncUiState()
    data class Restoring(val message: String = "Google Drive থেকে তথ্য রিস্টোর করা হচ্ছে...", val progress: Float = 0f) : SyncUiState()
    data class Syncing(val message: String = "Google Drive-এর সাথে তথ্য সিঙ্ক হচ্ছে...") : SyncUiState()
    data class Success(val message: String) : SyncUiState()
    data class Error(val error: String) : SyncUiState()
    data class ConsentNeeded(val intent: Intent) : SyncUiState()
}

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val googleDriveManager = GoogleDriveManager(application)
    val repository = SchoolRepository(db)
    val syncManager = MultiUserSyncManager.getInstance(application, db, repository, googleDriveManager)

    val driveSyncEngine = DriveSyncEngine(application, db)
    val driveBackupManager = DriveBackupManager(application, db)

    // School Setup & Entry UseCases
    val configManager = SchoolConfigManager.getInstance(application, db)
    val createSchoolUseCase = CreateSchoolUseCase(application, db, configManager)
    val joinSchoolUseCase = JoinSchoolUseCase(application, db, configManager)
    val authenticateUserUseCase = AuthenticateUserUseCase(db, configManager)

    init {
        repository.syncManager = syncManager
        // Auto-schedule periodic work
        SyncWorkManager.schedulePeriodicSync(application, 15)
        SyncWorkManager.scheduleDailyBackup(application)
    }

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    val isSchoolConfigured: StateFlow<Boolean> = configManager.isConfigured
    val currentSchoolConfig: StateFlow<SchoolConfig?> = configManager.currentConfig

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

    val userAccounts: StateFlow<List<UserAccountEntity>> = db.userAccountDao().getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Fail-Closed Role Evaluation:
     * Defaults to VIEW_ONLY / Guest if not configured or unverified.
     */
    val currentUserRole: StateFlow<String> = combine(_accountEmail, isSchoolConfigured) { email, configured ->
        Pair(email, configured)
    }.flatMapLatest { (email, configured) ->
        if (!configured) {
            flowOf("ViewOnly")
        } else if (email.isNullOrBlank()) {
            flowOf("Teacher")
        } else {
            db.userAccountDao().observeUserByEmail(email).map { user ->
                val cfg = configManager.currentConfig.value
                if (cfg != null && cfg.adminEmail.equals(email, ignoreCase = true)) {
                    "Admin"
                } else {
                    user?.role ?: "ViewOnly"
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Teacher")

    /**
     * Fail-Closed User Approval State:
     * If no school configured, not approved.
     * If email is set, verifies active user status in users.json or Admin role in config.
     */
    val isApprovedUser: StateFlow<Boolean> = combine(_accountEmail, isSchoolConfigured) { email, configured ->
        Pair(email, configured)
    }.flatMapLatest { (email, configured) ->
        if (!configured) {
            flowOf(false) // Fail-closed until school is created or joined
        } else if (email.isNullOrBlank()) {
            flowOf(true) // Offline / Demo guest usage allowed in safe mode
        } else {
            db.userAccountDao().observeUserByEmail(email).map { user ->
                val cfg = configManager.currentConfig.value
                val isAdmin = cfg != null && cfg.adminEmail.equals(email, ignoreCase = true)
                isAdmin || (user != null && !user.isDeleted && user.status.equals("Active", true))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pendingCount: StateFlow<Int> = syncManager.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val authorizedUsers: StateFlow<List<AuthorizedUserEntity>> = syncManager.allAuthorizedUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conflicts: StateFlow<List<SyncConflictEntity>> = syncManager.allConflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = db.backupHistoryDao().getAllBackups()
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

    fun getSignInIntent(): Intent {
        return googleDriveManager.getSignInIntent()
    }

    // =========================================================================
    // FIRST-LAUNCH SCHOOL SETUP & TWO-STEP ENTRY FLOW
    // =========================================================================

    suspend fun createSchool(
        schoolName: String,
        adminEmail: String,
        adminName: String = "",
        eiinCode: String = "",
        headTeacherName: String = ""
    ): Result<SchoolConfig> {
        val result = createSchoolUseCase.execute(
            schoolName = schoolName,
            adminEmail = adminEmail,
            adminName = adminName,
            eiinCode = eiinCode,
            headTeacherName = headTeacherName
        )
        if (result.isSuccess) {
            val cfg = result.getOrNull()
            if (cfg != null) {
                googleDriveManager.signInWithDirectEmail(cfg.adminEmail, adminName)
                refreshAccountState()
                completeAuthOnboarding()
                userMessage.value = "বিদ্যালয় তৈরি সম্পন্ন! রুম কোড: ${cfg.roomCode}"
            }
        }
        return result
    }

    suspend fun joinSchool(
        roomCode: String,
        userEmail: String,
        userName: String = ""
    ): Result<SchoolConfig> {
        val result = joinSchoolUseCase.execute(
            roomCode = roomCode,
            userEmail = userEmail,
            userName = userName
        )
        if (result.isSuccess) {
            val cfg = result.getOrNull()
            if (cfg != null) {
                googleDriveManager.signInWithDirectEmail(userEmail, userName)
                refreshAccountState()
                completeAuthOnboarding()
                userMessage.value = "বিদ্যালয়ে সংযুক্ত হওয়া হয়েছে! অনুমোদন যাচাই করা হচ্ছে..."
                viewModelScope.launch {
                    syncNow()
                }
            }
        }
        return result
    }

    suspend fun setupDemoSchool(): Result<SchoolConfig> {
        val demoConfig = SchoolConfig(
            schoolName = "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
            roomCode = "DEMO-2026",
            createdDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            schemaVersion = 1,
            adminEmail = "demo.admin@school.edu",
            updatedAt = System.currentTimeMillis(),
            updatedBy = "demo"
        )
        configManager.saveSchoolConfig(demoConfig, method = "DEMO")
        completeAuthOnboarding()
        userMessage.value = "ডেমো বিদ্যালয় মোড সক্রিয় হয়েছে"
        return Result.success(demoConfig)
    }

    suspend fun resetSchoolSetup() {
        configManager.resetSchoolConfig()
        resetAuthOnboarding()
        signOut()
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
            val email = _accountEmail.value ?: ""
            if (email.isNotBlank()) {
                _uiState.value = SyncUiState.Syncing("Google Drive-এর সাথে তথ্য সিঙ্ক হচ্ছে...")
                val result = driveSyncEngine.syncAll(email)
                if (result.success) {
                    _uiState.value = SyncUiState.Success(result.message)
                    userMessage.value = result.message
                } else {
                    _uiState.value = SyncUiState.Error(result.message)
                    userMessage.value = result.message
                }
            } else {
                val success = syncManager.triggerSync()
                if (success) {
                    userMessage.value = "ক্লাউড সিঙ্ক সফলভাবে সম্পন্ন হয়েছে"
                } else {
                    userMessage.value = syncManager.statusMessage.value
                }
            }
        }
    }

    fun addSchoolUser(email: String, name: String, role: String) {
        viewModelScope.launch {
            val adminEmail = _accountEmail.value ?: "Admin"
            val user = UserAccountEntity(
                email = email.trim().lowercase(),
                name = name.trim(),
                role = role,
                addedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                status = "Active",
                updatedAt = System.currentTimeMillis(),
                updatedBy = adminEmail,
                syncStatus = SyncStatus.PENDING
            )
            db.userAccountDao().insertUser(user)
            userMessage.value = "$email কে $role হিসেবে যুক্ত করা হয়েছে"
            syncNow()
        }
    }

    fun removeSchoolUser(email: String) {
        viewModelScope.launch {
            val adminEmail = _accountEmail.value ?: "Admin"
            val existing = db.userAccountDao().getUserByEmail(email)
            if (existing != null) {
                val updated = existing.copy(
                    isDeleted = true,
                    deletedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    updatedBy = adminEmail,
                    syncStatus = SyncStatus.PENDING
                )
                db.userAccountDao().updateUser(updated)
                userMessage.value = "$email কে অপসারণ করা হয়েছে"
                syncNow()
            }
        }
    }

    fun deleteSchoolUser(email: String) {
        removeSchoolUser(email)
    }

    fun createBackup(note: String = "ম্যানুয়াল ব্যাকআপ") {
        viewModelScope.launch {
            _uiState.value = SyncUiState.BackingUp("Google Drive এ ব্যাকআপ আপলোড হচ্ছে...", 0.2f)
            val res = syncManager.createManualBackup(note)
            res.onSuccess { msg ->
                refreshAccountState()
                _uiState.value = SyncUiState.Success(msg)
                userMessage.value = msg
            }.onFailure { err ->
                _uiState.value = SyncUiState.Error(err.localizedMessage ?: "ব্যাকআপ ব্যর্থ")
                userMessage.value = err.localizedMessage ?: "ব্যাকআপ ব্যর্থ"
            }
        }
    }

    fun backupNow(note: String = "ম্যানুয়াল ব্যাকআপ") {
        createBackup(note)
    }

    fun restoreBackup(targetBackupId: String? = null) {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Restoring("Google Drive থেকে ডাটাবেস রিস্টোর করা হচ্ছে...", 0.3f)
            val res = syncManager.restoreBackupWithSafetySnapshot(targetBackupId)
            res.onSuccess { msg ->
                refreshAccountState()
                _uiState.value = SyncUiState.Success(msg)
                userMessage.value = msg
            }.onFailure { err ->
                _uiState.value = SyncUiState.Error(err.localizedMessage ?: "রিস্টোর ব্যর্থ")
                userMessage.value = err.localizedMessage ?: "রিস্টোর ব্যর্থ"
            }
        }
    }

    fun saveScriptUrl(url: String) {
        syncManager.setScriptUrl(url)
        userMessage.value = "Apps Script URL সংরক্ষিত হয়েছে"
    }

    fun setScriptUrl(url: String) {
        saveScriptUrl(url)
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
