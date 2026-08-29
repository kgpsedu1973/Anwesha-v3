package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.local.util.FormulaEvaluator
import com.example.data.model.SchoolDatabaseModel
import com.example.repository.SchoolRepository
import com.example.util.ConnectedDriveAccountInfo
import com.example.util.DriveSetupState
import com.example.util.GoogleDriveSetupManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

data class StudentFilterState(
    val query: String = "",
    val clazz: String? = null,
    val selectedClasses: Set<String> = emptySet(),
    val gender: String? = null,
    val selectedGenders: Set<String> = emptySet(),
    val status: String? = "Current",
    val selectedStatuses: Set<String> = emptySet(),
    val village: String? = null,
    val selectedVillages: Set<String> = emptySet(),
    val specialNeeds: Boolean? = null,
    val customFilters: Map<String, Set<String>> = emptyMap()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = SchoolRepository(db)
    val driveSetupManager = GoogleDriveSetupManager(application)

    val driveSetupState: StateFlow<DriveSetupState> = driveSetupManager.setupState
    val driveConnectedAccount: StateFlow<ConnectedDriveAccountInfo?> = driveSetupManager.connectedAccountInfo

    // School Info
    val schoolInfo: StateFlow<SchoolInfoEntity?> = repository.schoolInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Students
    val allStudents: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filters & Search (Supports both single and multiple values)
    val searchQuery = MutableStateFlow("")
    val filterClass = MutableStateFlow<String?>(null)
    val filterClasses = MutableStateFlow<Set<String>>(emptySet())
    val filterGender = MutableStateFlow<String?>(null)
    val filterGenders = MutableStateFlow<Set<String>>(emptySet())
    val filterStatus = MutableStateFlow<String?>("Current") // Default show Current students
    val filterStatuses = MutableStateFlow<Set<String>>(setOf("Current"))
    val filterVillage = MutableStateFlow<String?>(null)
    val filterVillages = MutableStateFlow<Set<String>>(emptySet())
    val filterSpecialNeeds = MutableStateFlow<Boolean?>(null)
    val filterCustomValues = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private val filterState = combine(
        combine(searchQuery, filterClass, filterClasses, filterGender, filterGenders) { query, clazz, classes, gender, genders ->
            Tuple5(query, clazz, classes, gender, genders)
        },
        combine(filterStatus, filterStatuses, filterVillage, filterVillages, filterSpecialNeeds) { status, statuses, village, villages, specialNeeds ->
            Tuple5(status, statuses, village, villages, specialNeeds)
        },
        filterCustomValues
    ) { (query, clazz, classes, gender, genders), (status, statuses, village, villages, specialNeeds), customFilters ->
        val effectiveClasses = if (classes.isNotEmpty()) classes else (if (clazz != null && clazz != "ALL") setOf(clazz) else emptySet())
        val effectiveGenders = if (genders.isNotEmpty()) genders else (if (gender != null && gender != "ALL") setOf(gender) else emptySet())
        val effectiveStatuses = if (statuses.isNotEmpty()) statuses else (if (status != null && status != "ALL") setOf(status) else emptySet())
        val effectiveVillages = if (villages.isNotEmpty()) villages else (if (village != null && village != "ALL") setOf(village) else emptySet())

        StudentFilterState(
            query = query,
            clazz = clazz,
            selectedClasses = effectiveClasses,
            gender = gender,
            selectedGenders = effectiveGenders,
            status = status,
            selectedStatuses = effectiveStatuses,
            village = village,
            selectedVillages = effectiveVillages,
            specialNeeds = specialNeeds,
            customFilters = customFilters
        )
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
            val matchesClass = if (filter.selectedClasses.isNotEmpty()) {
                filter.selectedClasses.contains(student.studentClass)
            } else {
                filter.clazz == null || filter.clazz == "ALL" || student.studentClass == filter.clazz
            }

            val matchesGender = if (filter.selectedGenders.isNotEmpty()) {
                filter.selectedGenders.contains(student.gender)
            } else {
                filter.gender == null || filter.gender == "ALL" || student.gender == filter.gender
            }

            val matchesStatus = if (filter.selectedStatuses.isNotEmpty()) {
                if (filter.selectedStatuses.contains("ALL")) true else filter.selectedStatuses.contains(student.status)
            } else {
                filter.status == null || filter.status == "ALL" || student.status == filter.status
            }

            val matchesVillage = if (filter.selectedVillages.isNotEmpty()) {
                filter.selectedVillages.contains(student.village)
            } else {
                filter.village == null || filter.village == "ALL" || student.village == filter.village
            }

            val matchesSpecialNeeds = filter.specialNeeds == null || student.isSpecialNeeds == filter.specialNeeds

            val matchesCustom = if (filter.customFilters.isEmpty()) true else {
                val customMap = com.example.data.local.util.FormulaEvaluator.parseCustomValuesJson(student.customValuesJson)
                filter.customFilters.all { (fieldKey, allowedVals) ->
                    if (allowedVals.isEmpty() || allowedVals.contains("ALL")) true
                    else {
                        val studentVal = customMap[fieldKey] ?: ""
                        allowedVals.contains(studentVal)
                    }
                }
            }

            matchesQuery && matchesClass && matchesGender && matchesStatus && matchesVillage && matchesSpecialNeeds && matchesCustom
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

    // Status / Alert message
    val userMessage = MutableStateFlow<String?>(null)

    // Language state
    val appLanguage = MutableStateFlow(com.example.util.AppLanguage.getSavedLanguage(application))

    // Theme & Font state
    val appThemeMode = MutableStateFlow(com.example.ui.theme.ThemePreferences.getSavedThemeMode(application))
    val appColorPalette = MutableStateFlow(com.example.ui.theme.ThemePreferences.getSavedColorPalette(application))
    val bengaliFont = MutableStateFlow(com.example.util.FontPreferences.getSavedFont(application))
    val classPreset = MutableStateFlow(com.example.util.ClassPreset.getSavedPreset(application))

    // Base Date Settings
    val baseDateConfig = MutableStateFlow(com.example.util.BaseDateManager.getConfig(application))

    fun updateBaseDateConfig(config: com.example.util.BaseDateConfig) {
        baseDateConfig.value = config
        com.example.util.BaseDateManager.saveConfig(getApplication(), config)
    }

    fun setSingleBaseDate(baseDate: String, presetType: String = "CUSTOM", targetYear: Int? = null) {
        val current = baseDateConfig.value
        val yr = targetYear ?: current.targetYear
        val updated = current.copy(
            baseDate = baseDate,
            endDate = baseDate,
            presetType = presetType,
            targetYear = yr
        )
        updateBaseDateConfig(updated)
    }

    fun setBasePreset(preset: String, targetYear: Int? = null) {
        val yr = targetYear ?: baseDateConfig.value.targetYear
        val singleDate = com.example.util.BaseDateManager.computePresetDate(preset, yr)
        val (start, end) = com.example.util.BaseDateManager.computePresetDates(preset, yr)
        val updated = com.example.util.BaseDateConfig(
            baseDate = singleDate,
            startDate = start,
            endDate = end,
            presetType = preset,
            targetYear = yr
        )
        updateBaseDateConfig(updated)
    }

    fun getStudentAgeYearsFormatted(birthDateStr: String?): String {
        val baseDate = baseDateConfig.value.baseDate.ifBlank { baseDateConfig.value.endDate }
        return com.example.util.BaseDateManager.getStudentAgeYearsFormatted(birthDateStr, baseDate)
    }

    fun getStudentAgeYearsInt(birthDateStr: String?): Int {
        val baseDate = baseDateConfig.value.baseDate.ifBlank { baseDateConfig.value.endDate }
        return com.example.util.BaseDateManager.calculateAgeYearsInt(birthDateStr, baseDate)
    }

    fun setAppThemeMode(mode: com.example.ui.theme.AppThemeMode) {
        appThemeMode.value = mode
        com.example.ui.theme.ThemePreferences.saveThemeMode(getApplication(), mode)
    }

    fun setAppColorPalette(palette: com.example.ui.theme.AppColorPalette) {
        appColorPalette.value = palette
        com.example.ui.theme.ThemePreferences.saveColorPalette(getApplication(), palette)
    }

    fun setBengaliFont(font: com.example.util.AppBengaliFont) {
        bengaliFont.value = font
        com.example.util.FontPreferences.saveFont(getApplication(), font)
    }

    fun switchClassPreset(targetPreset: com.example.util.ClassPreset, onConverted: ((Int) -> Unit)? = null) {
        val oldPreset = classPreset.value
        if (oldPreset == targetPreset) return
        classPreset.value = targetPreset
        com.example.util.ClassPreset.savePreset(getApplication(), targetPreset)

        viewModelScope.launch {
            val currentList = allStudents.value
            var updatedCount = 0
            currentList.forEach { student ->
                val convertedClass = com.example.util.ClassPreset.convertClassName(student.studentClass, targetPreset)
                if (convertedClass != student.studentClass) {
                    repository.updateStudent(student.copy(studentClass = convertedClass))
                    updatedCount++
                }
            }
            onConverted?.invoke(updatedCount)
            if (updatedCount > 0) {
                userMessage.value = "শ্রেণির নাম স্বয়ংক্রিয়ভাবে পরিবর্তিত হয়েছে ($updatedCount জন শিক্ষার্থী)"
            }
        }
    }

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

    // Google Drive & Gmail Account Setup
    fun handleGoogleAccountSelected(account: GoogleSignInAccount) {
        viewModelScope.launch {
            val currentSchoolName = schoolInfo.value?.schoolName ?: "School"
            driveSetupManager.handleSignInAccount(account, currentSchoolName)
        }
    }

    fun disconnectDriveAccount(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            driveSetupManager.disconnect(onComplete)
        }
    }

    fun clearDriveSetupStatus() {
        driveSetupManager.clearStatusState()
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

    fun insertCustomFieldWithCalculation(
        field: CustomFieldEntity,
        rule: FormulaRuleEntity?,
        calculateForExistingStudents: Boolean
    ) {
        viewModelScope.launch {
            repository.insertCustomField(field)
            if (rule != null) {
                repository.insertFormulaRule(rule)
            }
            if (calculateForExistingStudents) {
                val currentStudents = repository.allStudents.firstOrNull() ?: emptyList()
                val customFields = repository.customFields.firstOrNull() ?: emptyList()
                val formulaRules = repository.formulaRules.firstOrNull() ?: emptyList()
                val allRules = if (rule != null) formulaRules + rule else formulaRules
                val allFields = customFields + field

                val updatedStudents = currentStudents.map { student ->
                    val calculatedValue = if (rule != null) {
                        FormulaEvaluator.evaluateRule(student, rule, allFields)
                    } else {
                        FormulaEvaluator.getFieldValue(student, field.id, allFields, allRules)
                    }
                    val map = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson).toMutableMap()
                    map[field.id] = calculatedValue
                    student.copy(customValuesJson = FormulaEvaluator.buildCustomValuesJson(map))
                }
                if (updatedStudents.isNotEmpty()) {
                    repository.insertAllStudents(updatedStudents)
                }
                userMessage.value = "ফিল্ড সংরক্ষিত এবং ${updatedStudents.size} জন শিক্ষার্থীর তথ্য স্বয়ংক্রিয়ভাবে হিসাব করা হয়েছে"
            } else {
                userMessage.value = "নতুন ফিল্ড সংরক্ষিত হয়েছে"
            }
        }
    }

    fun bulkUpdateStudentsField(
        studentIds: Set<String>,
        fieldKey: String,
        newValue: String,
        isCustomField: Boolean
    ) {
        viewModelScope.launch {
            val currentStudents = repository.allStudents.firstOrNull() ?: return@launch
            val targetStudents = currentStudents.filter { it.id in studentIds }
            val updatedStudents = targetStudents.map { student ->
                if (isCustomField) {
                    val map = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson).toMutableMap()
                    map[fieldKey] = newValue
                    student.copy(customValuesJson = FormulaEvaluator.buildCustomValuesJson(map))
                } else {
                    when (fieldKey.lowercase()) {
                        "studentclass", "class", "শ্রেণি", "শ্রেণী" -> student.copy(studentClass = newValue)
                        "section", "শাখা", "শাখা / বিভাগ" -> student.copy(section = newValue)
                        "academicyear", "year", "শিক্ষাবর্ষ", "বছর" -> student.copy(academicYear = newValue)
                        "status", "স্ট্যাটাস" -> student.copy(status = newValue)
                        "village", "গ্রাম" -> student.copy(village = newValue)
                        "gender", "লিঙ্গ" -> student.copy(gender = newValue)
                        "isspecialneeds", "বিশেষ চাহিদা" -> student.copy(
                            isSpecialNeeds = newValue.equals("true", ignoreCase = true) || newValue == "হ্যাঁ" || newValue == "বিশেষ চাহিদা আছে"
                        )
                        "address", "ঠিকানা" -> student.copy(address = newValue)
                        else -> {
                            val map = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson).toMutableMap()
                            map[fieldKey] = newValue
                            student.copy(customValuesJson = FormulaEvaluator.buildCustomValuesJson(map))
                        }
                    }
                }
            }
            if (updatedStudents.isNotEmpty()) {
                repository.insertAllStudents(updatedStudents)
            }
            userMessage.value = "${updatedStudents.size} জন শিক্ষার্থীর তথ্য একযোগে হালনাগাদ করা হয়েছে"
        }
    }

    fun bulkRecalculateFormulasForStudents(studentIds: Set<String>? = null) {
        viewModelScope.launch {
            val currentStudents = repository.allStudents.firstOrNull() ?: return@launch
            val customFields = repository.customFields.firstOrNull() ?: emptyList()
            val formulaRules = repository.formulaRules.firstOrNull() ?: emptyList()
            val targetStudents = if (studentIds != null) currentStudents.filter { it.id in studentIds } else currentStudents

            val updatedStudents = targetStudents.map { student ->
                val map = FormulaEvaluator.parseCustomValuesJson(student.customValuesJson).toMutableMap()
                customFields.filter { it.isCalculated }.forEach { calcField ->
                    val rule = formulaRules.find { it.id == calcField.formulaRuleId || it.targetFieldName.equals(calcField.name, ignoreCase = true) }
                    val calcVal = if (rule != null) {
                        FormulaEvaluator.evaluateRule(student, rule, customFields)
                    } else {
                        FormulaEvaluator.getFieldValue(student, calcField.id, customFields, formulaRules)
                    }
                    map[calcField.id] = calcVal
                }
                student.copy(customValuesJson = FormulaEvaluator.buildCustomValuesJson(map))
            }
            if (updatedStudents.isNotEmpty()) {
                repository.insertAllStudents(updatedStudents)
            }
            userMessage.value = "${updatedStudents.size} জন শিক্ষার্থীর ক্যালকুলেশন পুনর্গণনা করা হয়েছে"
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

    fun saveDailyAttendanceList(date: String, records: List<AttendanceEntity>) {
        viewModelScope.launch {
            repository.deleteAttendanceForDate(date)
            repository.insertAllAttendance(records)
            userMessage.value = "$date এর হাজিরা সফলভাবে সংরক্ষিত হয়েছে"
        }
    }

    fun deleteAttendanceForDate(date: String) {
        viewModelScope.launch {
            repository.deleteAttendanceForDate(date)
            userMessage.value = "$date এর হাজিরা রেকর্ড মুছে ফেলা হয়েছে"
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
