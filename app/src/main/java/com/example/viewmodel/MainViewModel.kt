package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.local.util.FormulaEvaluator
import com.example.data.model.SchoolDatabaseModel
import com.example.repository.SchoolRepository
import com.example.util.DriveFileInfo
import com.example.util.DriveOperationResult
import com.example.util.GoogleDriveManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class DriveSyncUiState {
    object Idle : DriveSyncUiState()
    data class Checking(val message: String = "Google Drive এ মাস্টার ডাটাবেস অনুসন্ধান করা হচ্ছে...") : DriveSyncUiState()
    data class FoundExisting(val fileInfo: DriveFileInfo) : DriveSyncUiState()
    object NotFound : DriveSyncUiState()
    data class Syncing(val message: String) : DriveSyncUiState()
    data class Success(val message: String) : DriveSyncUiState()
    data class Error(val error: String) : DriveSyncUiState()
}

data class StudentFilterState(
    val query: String = "",
    val clazz: String? = null,
    val gender: String? = null,
    val status: String? = "Current",
    val village: String? = null,
    val specialNeeds: Boolean? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = SchoolRepository(db)

    // School Info
    val schoolInfo: StateFlow<SchoolInfoEntity?> = repository.schoolInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Students
    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filters & Search
    val searchQuery = MutableStateFlow("")
    val filterClass = MutableStateFlow<String?>(null)
    val filterGender = MutableStateFlow<String?>(null)
    val filterStatus = MutableStateFlow<String?>("Current") // Default show Current students
    val filterVillage = MutableStateFlow<String?>(null)
    val filterSpecialNeeds = MutableStateFlow<Boolean?>(null)

    private val filterState = combine(
        combine(searchQuery, filterClass, filterGender) { query, clazz, gender ->
            Triple(query, clazz, gender)
        },
        combine(filterStatus, filterVillage, filterSpecialNeeds) { status, village, specialNeeds ->
            Triple(status, village, specialNeeds)
        }
    ) { (query, clazz, gender), (status, village, specialNeeds) ->
        StudentFilterState(query, clazz, gender, status, village, specialNeeds)
    }

    val filteredStudents: StateFlow<List<StudentEntity>> = combine(allStudents, filterState) { students, filter ->
        students.filter { student ->
            val matchesQuery = if (filter.query.isBlank()) true else {
                student.name.contains(filter.query, ignoreCase = true) ||
                        student.fatherName.contains(filter.query, ignoreCase = true) ||
                        student.motherName.contains(filter.query, ignoreCase = true) ||
                        student.id.contains(filter.query, ignoreCase = true) ||
                        student.rollNumber.toString().contains(filter.query) ||
                        student.village.contains(filter.query, ignoreCase = true) ||
                        student.mobile.contains(filter.query)
            }
            val matchesClass = filter.clazz == null || student.studentClass == filter.clazz
            val matchesGender = filter.gender == null || student.gender == filter.gender
            val matchesStatus = filter.status == null || filter.status == "ALL" || student.status == filter.status
            val matchesVillage = filter.village == null || student.village == filter.village
            val matchesSpecialNeeds = filter.specialNeeds == null || student.isSpecialNeeds == filter.specialNeeds

            matchesQuery && matchesClass && matchesGender && matchesStatus && matchesVillage && matchesSpecialNeeds
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom Fields & Formula Rules
    val customFields: StateFlow<List<CustomFieldEntity>> = repository.customFields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val formulaRules: StateFlow<List<FormulaRuleEntity>> = repository.formulaRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Attendance
    val attendanceRecords: StateFlow<List<AttendanceEntity>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Routine
    val routineItems: StateFlow<List<RoutineItemEntity>> = repository.allRoutineItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Document Templates
    val documentTemplates: StateFlow<List<DocumentTemplateEntity>> = repository.allDocumentTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Surveys
    val surveys: StateFlow<List<SurveyEntity>> = repository.allSurveys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Users
    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exam Results
    val allExamResults: StateFlow<List<ExamResultEntity>> = repository.allExamResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Google Drive Integration
    val googleDriveManager = GoogleDriveManager(application)
    val driveSyncState = MutableStateFlow<DriveSyncUiState>(DriveSyncUiState.Idle)
    val lastSyncTimestamp = MutableStateFlow(googleDriveManager.getLastSyncTime())
    val signedInAccountEmail = MutableStateFlow(googleDriveManager.getAccountEmail())

    // Status / Alert message
    val userMessage = MutableStateFlow<String?>(null)

    // Language state
    val appLanguage = MutableStateFlow(com.example.util.AppLanguage.getSavedLanguage(application))

    fun setAppLanguage(language: com.example.util.Language) {
        appLanguage.value = language
        com.example.util.AppLanguage.saveLanguage(getApplication(), language)
    }

    fun setAppLanguage(langName: String) {
        val lang = if (langName.contains("en", ignoreCase = true) || langName.contains("english", ignoreCase = true)) {
            com.example.util.Language.ENGLISH
        } else {
            com.example.util.Language.BANGLA
        }
        setAppLanguage(lang)
    }

    fun checkDriveDatabase() {
        viewModelScope.launch {
            driveSyncState.value = DriveSyncUiState.Checking()
            when (val result = googleDriveManager.checkExistingDatabase()) {
                is DriveOperationResult.Success -> {
                    if (result.data != null) {
                        driveSyncState.value = DriveSyncUiState.FoundExisting(result.data)
                    } else {
                        driveSyncState.value = DriveSyncUiState.NotFound
                    }
                }
                is DriveOperationResult.Error -> {
                    driveSyncState.value = DriveSyncUiState.Error(result.message)
                }
                is DriveOperationResult.ConsentRequired -> {
                    driveSyncState.value = DriveSyncUiState.Error(result.message)
                }
                is DriveOperationResult.NotFound -> {
                    driveSyncState.value = DriveSyncUiState.NotFound
                }
                is DriveOperationResult.Progress -> {
                    driveSyncState.value = DriveSyncUiState.Checking(result.status)
                }
            }
        }
    }

    fun restoreFromDrive(fileId: String? = null, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            driveSyncState.value = DriveSyncUiState.Syncing("Google Drive থেকে ডাটাবেস ডাউনলোড ও রিস্টোর করা হচ্ছে...")
            when (val result = googleDriveManager.downloadDatabase(fileId, repository)) {
                is DriveOperationResult.Success -> {
                    lastSyncTimestamp.value = System.currentTimeMillis()
                    driveSyncState.value = DriveSyncUiState.Success("সফলভাবে Google Drive থেকে ডাটাবেস রিস্টোর সম্পন্ন হয়েছে!")
                    userMessage.value = "ডাটাবেস রিস্টোর সম্পন্ন! মোট ${result.data.studentsList.size} শিক্ষার্থী লোড হয়েছে।"
                    onComplete?.invoke(true)
                }
                is DriveOperationResult.NotFound -> {
                    driveSyncState.value = DriveSyncUiState.Error("কোনো ব্যাকআপ পাওয়া যায়নি")
                    userMessage.value = "কোনো ব্যাকআপ পাওয়া যায়নি"
                    onComplete?.invoke(false)
                }
                is DriveOperationResult.Error -> {
                    driveSyncState.value = DriveSyncUiState.Error("রিস্টোর ব্যর্থ হয়েছে: ${result.message}")
                    userMessage.value = "রিস্টোর ব্যর্থ হয়েছে: ${result.message}"
                    onComplete?.invoke(false)
                }
                is DriveOperationResult.ConsentRequired -> {
                    driveSyncState.value = DriveSyncUiState.Error("Google Drive অনুমতি প্রয়োজন")
                    onComplete?.invoke(false)
                }
                is DriveOperationResult.Progress -> {}
            }
        }
    }

    fun initializeNewSchoolAndUpload(
        schoolName: String,
        eiinCode: String,
        adminName: String,
        adminPhone: String,
        adminEmail: String,
        securityPin: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            driveSyncState.value = DriveSyncUiState.Syncing("নতুন বিদ্যালয় ডাটাবেস তৈরি ও Google Drive এ আপলোড হচ্ছে...")
            val initialModel = SchoolDatabaseModel.createInitial(
                schoolName = schoolName,
                eiinCode = eiinCode,
                adminName = adminName,
                adminEmail = adminEmail,
                adminPhone = adminPhone,
                pinHash = securityPin
            )

            // Save to local Room DB first
            repository.importMasterModel(initialModel)

            // Upload master json to Drive
            when (val result = googleDriveManager.uploadDatabase(initialModel.toJson(true))) {
                is DriveOperationResult.Success -> {
                    lastSyncTimestamp.value = System.currentTimeMillis()
                    driveSyncState.value = DriveSyncUiState.Success("বিদ্যালয় ডাটাবেস সফলভাবে শুরু ও ক্লাউডে আপলোড হয়েছে!")
                    userMessage.value = "বিদ্যালয় কনফিগারেশন সফল হয়েছে!"
                    onComplete(true)
                }
                is DriveOperationResult.Error -> {
                    // Local DB was initialized even if drive network failed
                    lastSyncTimestamp.value = System.currentTimeMillis()
                    driveSyncState.value = DriveSyncUiState.Success("বিদ্যালয় লোকাল ডাটাবেসে সেভ হয়েছে (ড্রাইভ কিউতে সংরক্ষিত)")
                    userMessage.value = "বিদ্যালয় ডাটাবেস তৈরি হয়েছে (অফলাইন মোড)"
                    onComplete(true)
                }
                else -> {
                    lastSyncTimestamp.value = System.currentTimeMillis()
                    driveSyncState.value = DriveSyncUiState.Success("বিদ্যালয় লোকাল ডাটাবেসে সেভ হয়েছে")
                    onComplete(true)
                }
            }
        }
    }

    fun syncCurrentDatabaseToDrive(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            driveSyncState.value = DriveSyncUiState.Syncing("Google Drive এ ডাটাবেস ব্যাকআপ আপলোড করা হচ্ছে...")
            val masterModel = repository.exportToMasterModel()
            val totalRecords = masterModel.studentsList.size + masterModel.usersList.size + masterModel.attendanceList.size + masterModel.examResultsList.size
            when (val result = googleDriveManager.uploadDatabase(masterModel.toJson(true), totalRecords)) {
                is DriveOperationResult.Success -> {
                    lastSyncTimestamp.value = System.currentTimeMillis()
                    driveSyncState.value = DriveSyncUiState.Success("সফলভাবে Google Drive এ ডাটাবেস সিঙ্ক সম্পন্ন হয়েছে!")
                    userMessage.value = "Google Drive এ সর্বশেষ তথ্য ব্যাকআপ হয়েছে!"
                    onComplete?.invoke(true)
                }
                is DriveOperationResult.Error -> {
                    driveSyncState.value = DriveSyncUiState.Error(result.message)
                    userMessage.value = "সিঙ্ক ত্রুটি: ${result.message}"
                    onComplete?.invoke(false)
                }
                else -> {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun updateAccountInfo(email: String, name: String) {
        googleDriveManager.saveAccountState(email, name)
        signedInAccountEmail.value = email
    }

    // Actions
    fun insertStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.insertStudent(student)
            userMessage.value = "শিক্ষার্থীর তথ্য সংরক্ষিত হয়েছে"
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.updateStudent(student)
            userMessage.value = "শিক্ষার্থীর তথ্য আপডেট করা হয়েছে"
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.deleteStudent(student)
            userMessage.value = "শিক্ষার্থী মুছে ফেলা হয়েছে"
        }
    }

    fun insertCustomField(field: CustomFieldEntity) {
        viewModelScope.launch {
            repository.insertCustomField(field)
            userMessage.value = "নতুন ফিল্ড যুক্ত হয়েছে"
        }
    }

    fun deleteCustomField(field: CustomFieldEntity) {
        viewModelScope.launch {
            repository.deleteCustomField(field)
            userMessage.value = "ফিল্ড মুছে ফেলা হয়েছে"
        }
    }

    fun insertFormulaRule(rule: FormulaRuleEntity) {
        viewModelScope.launch {
            repository.insertFormulaRule(rule)
            userMessage.value = "নতুন সূত্র/নিয়ম যুক্ত হয়েছে"
        }
    }

    fun deleteFormulaRule(rule: FormulaRuleEntity) {
        viewModelScope.launch {
            repository.deleteFormulaRule(rule)
            userMessage.value = "সূত্র মুছে ফেলা হয়েছে"
        }
    }

    fun insertAttendance(attendance: AttendanceEntity) {
        viewModelScope.launch {
            repository.insertAttendance(attendance)
            userMessage.value = "উপস্থিতি রেকর্ড করা হয়েছে"
        }
    }

    fun deleteAttendance(attendance: AttendanceEntity) {
        viewModelScope.launch {
            repository.deleteAttendance(attendance)
        }
    }

    fun insertRoutineItem(item: RoutineItemEntity) {
        viewModelScope.launch {
            repository.insertRoutineItem(item)
            userMessage.value = "রুটিন এনট্রি যুক্ত হয়েছে"
        }
    }

    fun deleteRoutineItem(item: RoutineItemEntity) {
        viewModelScope.launch {
            repository.deleteRoutineItem(item)
        }
    }

    fun insertDocumentTemplate(template: DocumentTemplateEntity) {
        viewModelScope.launch {
            repository.insertDocumentTemplate(template)
            userMessage.value = "ডকুমেন্ট টেমপ্লেট সংরক্ষিত হয়েছে"
        }
    }

    fun deleteDocumentTemplate(template: DocumentTemplateEntity) {
        viewModelScope.launch {
            repository.deleteDocumentTemplate(template)
        }
    }

    fun insertSurvey(survey: SurveyEntity) {
        viewModelScope.launch {
            repository.insertSurvey(survey)
            userMessage.value = "সার্ভে ডেটা সংরক্ষিত হয়েছে"
        }
    }

    fun deleteSurvey(survey: SurveyEntity) {
        viewModelScope.launch {
            repository.deleteSurvey(survey)
        }
    }

    fun updateSchoolInfo(info: SchoolInfoEntity) {
        viewModelScope.launch {
            repository.saveSchoolInfo(info)
            userMessage.value = "বিদ্যালয়ের তথ্য সংরক্ষণ করা হয়েছে"
        }
    }

    // Helper method to evaluate student internal/external status based on rules or village
    fun getStudentCategory(student: StudentEntity): String {
        val rules = formulaRules.value
        val villageRule = rules.firstOrNull { it.targetFieldName == "শিক্ষার্থীর ধরণ" || it.sourceField == "village" }
        if (villageRule != null) {
            return FormulaEvaluator.evaluateRule(student, villageRule)
        }
        val school = schoolInfo.value
        val internalList = school?.internalVillages?.split(",")?.map { it.trim() } ?: listOf("পশ্চিম রামপুর", "আমতলী", "কৃষ্ণপুর")
        return if (internalList.any { it.equals(student.village, ignoreCase = true) }) "অভ্যন্তরীণ" else "বহিরাগত"
    }

    val currentUserRole = MutableStateFlow("School Admin")

    fun setCurrentUserRole(role: String) {
        currentUserRole.value = role
    }

    fun insertUser(user: UserEntity) {
        viewModelScope.launch {
            repository.insertUser(user)
            userMessage.value = "ব্যবহারকারী যুক্ত হয়েছে: ${user.name}"
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            repository.deleteUser(user)
            userMessage.value = "ব্যবহারকারী অপসারণ করা হয়েছে"
        }
    }

    fun clearLocalData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllLocalData()
            userMessage.value = "স্থানীয় রেকর্ড সফলভাবে মুছে ফেলা হয়েছে"
            onComplete()
        }
    }

    fun importJsonBackup(jsonContent: String, onComplete: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = repository.importDataFromJson(jsonContent)
                if (count > 0) {
                    userMessage.value = "সফলভাবে $count জন শিক্ষার্থীর তথ্য ইম্পোর্ট হয়েছে"
                    onComplete(true, count)
                } else {
                    userMessage.value = "ইম্পোর্ট ব্যর্থ হয়েছে: সঠিক ফরম্যাট পাওয়া যায়নি"
                    onComplete(false, 0)
                }
            } catch (e: Exception) {
                userMessage.value = "ইম্পোর্ট ত্রুটি: ${e.localizedMessage}"
                onComplete(false, 0)
            }
        }
    }

    fun importStudentsFromList(students: List<StudentEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            repository.insertAllStudents(students)
            userMessage.value = "সফলভাবে ${students.size} জন শিক্ষার্থীর তথ্য ইম্পোর্ট সম্পন্ন হয়েছে"
            onComplete(students.size)
        }
    }

    fun importUsersFromList(users: List<UserEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            repository.insertAllUsers(users)
            userMessage.value = "সফলভাবে ${users.size} জন শিক্ষক/স্টাফের তথ্য ইম্পোর্ট সম্পন্ন হয়েছে"
            onComplete(users.size)
        }
    }

    fun importAttendanceFromList(records: List<AttendanceEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            records.forEach { repository.insertAttendance(it) }
            userMessage.value = "সফলভাবে ${records.size}টি উপস্থিতি রেকর্ড ইম্পোর্ট সম্পন্ন হয়েছে"
            onComplete(records.size)
        }
    }

    fun importExamResultsFromList(results: List<ExamResultEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            repository.insertAllExamResults(results)
            userMessage.value = "সফলভাবে ${results.size}টি পরীক্ষার ফলাফল ইম্পোর্ট সম্পন্ন হয়েছে"
            onComplete(results.size)
        }
    }

    fun importRoutineFromList(items: List<RoutineItemEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            items.forEach { repository.insertRoutineItem(it) }
            userMessage.value = "সফলভাবে ${items.size}টি রুটিন এন্ট্রি ইম্পোর্ট সম্পন্ন হয়েছে"
            onComplete(items.size)
        }
    }

    fun shareCsvContent(fileName: String, csvContent: String, title: String = "CSV Data Export") {
        try {
            val context = getApplication<Application>()
            val cacheDir = java.io.File(context.cacheDir, "csv_exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = java.io.File(cacheDir, fileName)
            java.io.FileOutputStream(file).use { fos ->
                fos.write(csvContent.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(intent, title).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            userMessage.value = "শেয়ার করতে সমস্যা হয়েছে: ${e.localizedMessage}"
        }
    }

    fun clearUserMessage() {
        userMessage.value = null
    }
}
