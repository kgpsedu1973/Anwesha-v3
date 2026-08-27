package com.example.data.local

import com.example.data.local.entity.*
import java.text.SimpleDateFormat
import java.util.*

object SampleData {
    suspend fun seedDatabase(db: AppDatabase) {
        // 1. School Info
        val school = SchoolInfoEntity(
            id = 1,
            schoolName = "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
            address = "ডাকঘর: রামপুর, উপজেলা: সদর, জেলা: কুমিল্লা",
            eiinCode = "134251",
            phone = "01711223344",
            headTeacherName = "মো: রফিকুল ইসলাম",
            tagline = "জ্ঞান, মনন ও স্বপ্নের সোপান",
            internalVillages = "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"
        )
        db.schoolInfoDao().insertOrUpdateSchoolInfo(school)

        // 2. Custom Fields
        val bloodGroupField = CustomFieldEntity(
            id = "cf_blood_group",
            name = "রক্তের গ্রুপ",
            fieldType = "Dropdown",
            optionsJson = "A+,B+,AB+,O+,A-,B-,AB-,O-",
            isCalculated = false
        )
        val parentOccField = CustomFieldEntity(
            id = "cf_parent_occ",
            name = "অভিভাবকের পেশা",
            fieldType = "Text",
            optionsJson = null,
            isCalculated = false
        )
        val categoryField = CustomFieldEntity(
            id = "cf_category",
            name = "শিক্ষার্থীর ধরণ",
            fieldType = "Calculated",
            optionsJson = null,
            isCalculated = true,
            formulaRuleId = "rule_internal_village"
        )
        db.customFieldDao().insertField(bloodGroupField)
        db.customFieldDao().insertField(parentOccField)
        db.customFieldDao().insertField(categoryField)

        // 3. Formula Rule
        val villageRule = FormulaRuleEntity(
            id = "rule_internal_village",
            ruleName = "অভ্যন্তরীণ/বহিরাগত শিক্ষার্থী নির্ধারণ",
            targetFieldName = "শিক্ষার্থীর ধরণ",
            sourceField = "village",
            operator = "IN_LIST",
            conditionValue = "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর",
            resultIfTrue = "অভ্যন্তরীণ",
            resultIfFalse = "বহিরাগত"
        )
        db.formulaRuleDao().insertRule(villageRule)

        // 4. Students
        val students = listOf(
            StudentEntity(
                id = "STU-2026-001",
                studentClass = "১ম শ্রেণি",
                rollNumber = 1,
                name = "তানভীর আহমেদ",
                fatherName = "মো: কামাল হোসেন",
                motherName = "রেহানা পারভীন",
                birthDate = "2019-03-12",
                mobile = "01712345678",
                village = "পশ্চিম রামপুর",
                academicYear = "২০২৬",
                address = "পশ্চিম রামপুর, কুমিল্লা",
                birthRegNumber = "20191912834000101",
                gender = "ছাত্র",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"B+\",\"cf_parent_occ\":\"কৃষক\"}"
            ),
            StudentEntity(
                id = "STU-2026-002",
                studentClass = "১ম শ্রেণি",
                rollNumber = 2,
                name = "রাইসা খাতুন",
                fatherName = "জহিরুল ইসলাম",
                motherName = "শাহিনুর বেগম",
                birthDate = "2019-07-25",
                mobile = "01819876543",
                village = "আমতলী",
                academicYear = "২০২৬",
                address = "আমতলী, কুমিল্লা",
                birthRegNumber = "20191912834000102",
                gender = "ছাত্রী",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"A+\",\"cf_parent_occ\":\"ব্যবসায়ী\"}"
            ),
            StudentEntity(
                id = "STU-2026-003",
                studentClass = "২য় শ্রেণি",
                rollNumber = 1,
                name = "মো: সিয়াম হোসেন",
                fatherName = "মো: আনোয়ার হোসেন",
                motherName = "ফাতেমা জোহরা",
                birthDate = "2018-05-14",
                mobile = "01911223344",
                village = "কৃষ্ণপুর",
                academicYear = "২০২৬",
                address = "কৃষ্ণপুর, কুমিল্লা",
                birthRegNumber = "20181912834000103",
                gender = "ছাত্র",
                isSpecialNeeds = true,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"O+\",\"cf_parent_occ\":\"শিক্ষক\"}"
            ),
            StudentEntity(
                id = "STU-2026-004",
                studentClass = "২য় শ্রেণি",
                rollNumber = 2,
                name = "নুসরাত জাহান",
                fatherName = "মো: শহিদুল ইসলাম",
                motherName = "রোকেয়া সুলতানা",
                birthDate = "2018-11-02",
                mobile = "01677889900",
                village = "চরপার্বতী",
                academicYear = "২০২৬",
                address = "চরপার্বতী, কুমিল্লা",
                birthRegNumber = "20181912834000104",
                gender = "ছাত্রী",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"AB+\",\"cf_parent_occ\":\"চাকরিজীবী\"}"
            ),
            StudentEntity(
                id = "STU-2026-005",
                studentClass = "৩য় শ্রেণি",
                rollNumber = 1,
                name = "সুমাইয়া আক্তার",
                fatherName = "মো: রফিকুল ইসলাম",
                motherName = "পলি আক্তার",
                birthDate = "2017-01-18",
                mobile = "01555667788",
                village = "পশ্চিম রামপুর",
                academicYear = "২০২৬",
                address = "পশ্চিম রামপুর, কুমিল্লা",
                birthRegNumber = "20171912834000105",
                gender = "ছাত্রী",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"O+\",\"cf_parent_occ\":\"কৃষক\"}"
            ),
            StudentEntity(
                id = "STU-2026-006",
                studentClass = "৩য় শ্রেণি",
                rollNumber = 2,
                name = "অর্নব দাস",
                fatherName = "প্রদীপ কুমার দাস",
                motherName = "গীতা রানী দাস",
                birthDate = "2017-09-09",
                mobile = "01300112233",
                village = "আমতলী",
                academicYear = "২০২৬",
                address = "আমতলী, কুমিল্লা",
                birthRegNumber = "20171912834000106",
                gender = "ছাত্র",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"B+\",\"cf_parent_occ\":\"দোকানী\"}"
            ),
            StudentEntity(
                id = "STU-2026-007",
                studentClass = "৪র্থ শ্রেণি",
                rollNumber = 1,
                name = "মেহেদী হাসান",
                fatherName = "মো: দেলোয়ার হোসেন",
                motherName = "আয়েশা বেগম",
                birthDate = "2016-04-30",
                mobile = "01822334455",
                village = "চরপার্বতী",
                academicYear = "২০২৬",
                address = "চরপার্বতী, কুমিল্লা",
                birthRegNumber = "20161912834000107",
                gender = "ছাত্র",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"A+\",\"cf_parent_occ\":\"ড্রাইভার\"}"
            ),
            StudentEntity(
                id = "STU-2026-008",
                studentClass = "৫ম শ্রেণি",
                rollNumber = 1,
                name = "মরিয়ম বেগম",
                fatherName = "মো: সিরাজুল ইসলাম",
                motherName = "খাদিজা আক্তার",
                birthDate = "2015-08-12",
                mobile = "01744556677",
                village = "পশ্চিম রামপুর",
                academicYear = "২০২৬",
                address = "পশ্চিম রামপুর, কুমিল্লা",
                birthRegNumber = "20151912834000108",
                gender = "ছাত্রী",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"O+\",\"cf_parent_occ\":\"প্রবাসী\"}"
            ),
            StudentEntity(
                id = "STU-2026-009",
                studentClass = "প্রাক-প্রাথমিক ৪+",
                rollNumber = 1,
                name = "আরিফ বিল্লাহ",
                fatherName = "আব্দুর রহিম",
                motherName = "তাহমিনা বেগম",
                birthDate = "2021-02-10",
                mobile = "01999887766",
                village = "কৃষ্ণপুর",
                academicYear = "২০২৬",
                address = "কৃষ্ণপুর, কুমিল্লা",
                birthRegNumber = "20211912834000109",
                gender = "ছাত্র",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"B+\",\"cf_parent_occ\":\"কৃষক\"}"
            ),
            StudentEntity(
                id = "STU-2026-010",
                studentClass = "প্রাক-প্রাথমিক ৫+",
                rollNumber = 1,
                name = "সাদিয়া নূর",
                fatherName = "নুরুল ইসলাম",
                motherName = "মাহমুদা আক্তার",
                birthDate = "2020-06-18",
                mobile = "01611223344",
                village = "পশ্চিম রামপুর",
                academicYear = "২০২৬",
                address = "পশ্চিম রামপুর, কুমিল্লা",
                birthRegNumber = "20201912834000110",
                gender = "ছাত্রী",
                isSpecialNeeds = false,
                status = "Current",
                customValuesJson = "{\"cf_blood_group\":\"A+\",\"cf_parent_occ\":\"শিক্ষক\"}"
            )
        )
        db.studentDao().insertAllStudents(students)

        // 5. Attendance Records
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        val attendanceList = listOf(
            AttendanceEntity(
                id = "att_1",
                date = todayStr,
                className = "১ম শ্রেণি",
                presentBoys = 12,
                presentGirls = 14,
                absentBoys = 1,
                absentGirls = 1,
                totalBoys = 13,
                totalGirls = 15,
                notes = "স্বাভাবিক উপস্থিতি"
            ),
            AttendanceEntity(
                id = "att_2",
                date = todayStr,
                className = "২য় শ্রেণি",
                presentBoys = 15,
                presentGirls = 16,
                absentBoys = 2,
                absentGirls = 0,
                totalBoys = 17,
                totalGirls = 16,
                notes = "বৃষ্টির কারণে কিছু শিক্ষার্থী অনুপস্থিত"
            ),
            AttendanceEntity(
                id = "att_3",
                date = todayStr,
                className = "৩য় শ্রেণি",
                presentBoys = 18,
                presentGirls = 20,
                absentBoys = 0,
                absentGirls = 1,
                totalBoys = 18,
                totalGirls = 21,
                notes = null
            ),
            AttendanceEntity(
                id = "att_4",
                date = todayStr,
                className = "৪র্থ শ্রেণি",
                presentBoys = 14,
                presentGirls = 17,
                absentBoys = 1,
                absentGirls = 2,
                totalBoys = 15,
                totalGirls = 19,
                notes = null
            ),
            AttendanceEntity(
                id = "att_5",
                date = todayStr,
                className = "৫ম শ্রেণি",
                presentBoys = 20,
                presentGirls = 22,
                absentBoys = 0,
                absentGirls = 0,
                totalBoys = 20,
                totalGirls = 22,
                notes = "১০০% উপস্থিতি"
            )
        )
        db.attendanceDao().insertAllAttendance(attendanceList)

        // 6. Routine Items
        val routineList = listOf(
            RoutineItemEntity(
                id = "rt_1",
                routineType = "Class Routine",
                className = "১ম শ্রেণি",
                subject = "বাংলা",
                teacher = "সুলতানা মেহেরিন",
                day = "রবিবার",
                startTime = "09:00 AM",
                endTime = "09:45 AM",
                periodName = "১ম পিরিয়ড",
                roomNo = "কক্ষ ১০১"
            ),
            RoutineItemEntity(
                id = "rt_2",
                routineType = "Class Routine",
                className = "১ম শ্রেণি",
                subject = "গণিত",
                teacher = "আব্দুল হাদি",
                day = "রবিবার",
                startTime = "09:45 AM",
                endTime = "10:30 AM",
                periodName = "২য় পিরিয়ড",
                roomNo = "কক্ষ ১০১"
            ),
            RoutineItemEntity(
                id = "rt_3",
                routineType = "Class Routine",
                className = "৫ম শ্রেণি",
                subject = "ইংরেজি",
                teacher = "মোঃ জাহাঙ্গীর আলম",
                day = "সোমবার",
                startTime = "10:30 AM",
                endTime = "11:15 AM",
                periodName = "৩য় পিরিয়ড",
                roomNo = "কক্ষ ১০৫"
            ),
            RoutineItemEntity(
                id = "rt_4",
                routineType = "Exam Routine",
                className = "৫ম শ্রেণি",
                subject = "প্রাথমিক বিজ্ঞান",
                teacher = "তানিয়া আক্তার",
                day = "বৃহস্পতিবার",
                startTime = "10:00 AM",
                endTime = "12:00 PM",
                periodName = "প্রথম অর্ধবার্ষিক পরীক্ষা",
                roomNo = "হল রুম"
            )
        )
        for (item in routineList) {
            db.routineDao().insertRoutineItem(item)
        }

        // 7. Document Templates
        val templates = listOf(
            DocumentTemplateEntity(
                id = "dt_1",
                title = "প্রত্যয়ন পত্র (Testimonial)",
                contentTemplate = """
                    এই মর্মে প্রত্যয়ন করা যাচ্ছে যে, {শিক্ষার্থীর নাম}, পিতা: {পিতার নাম}, মাতা: {মাতার নাম}, গ্রাম: {গ্রাম}, অত্র বিদ্যালয়ের {শ্রেণি}-এর একজন নিয়মিত শিক্ষার্থী। তাহার রোল নম্বর {রোল}।
                    
                    আমাদের জানা মতে তাহার স্বভাব ও চরিত্র উত্তম। আমি তাহার সর্বাঙ্গীন সাফল্য ও উজ্জ্বল ভবিষ্যৎ কামনা করি।
                    
                    পিতার জাতীয় পরিচয়পত্র (NID): {পিতার ভোটার আইডি}
                    মাতার জাতীয় পরিচয়পত্র (NID): {মাতার ভোটার আইডি}
                """.trimIndent(),
                createdDate = todayStr
            ),
            DocumentTemplateEntity(
                id = "dt_2",
                title = "শিক্ষার্থী তথ্য ছক",
                contentTemplate = """
                    ১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়
                    ----------------------------------------------
                    শিক্ষার্থীর নাম: {শিক্ষার্থীর নাম}
                    শ্রেণি: {শ্রেণি} | রোল নম্বর: {রোল}
                    জন্মতারিখ: {জন্মতারিখ} | জন্ম নিবন্ধন নং: {জন্মনিবন্ধন নম্বর}
                    পিতার নাম: {পিতার নাম}
                    মাতার নাম: {মাতার নাম}
                    মোবাইল নম্বর: {মোবাইল নম্বর}
                    গ্রাম/ঠিকানা: {গ্রাম}, {ঠিকানা}
                    বিশেষ চাহিদা সম্পন্ন: {বিশেষ চাহিদা}
                    শিক্ষার্থীর ক্যাটাগরি: {শিক্ষার্থীর ধরণ}
                """.trimIndent(),
                createdDate = todayStr
            )
        )
        for (t in templates) {
            db.documentTemplateDao().insertTemplate(t)
        }

        // 8. Survey
        val survey = SurveyEntity(
            id = "surv_1",
            studentId = "STU-2026-003",
            surveyYear = "২০২৬",
            age = 8,
            educationStatus = "অধ্যায়নরত",
            schoolName = "১৫৪ নং পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
            className = "২য় শ্রেণি",
            gender = "ছাত্র",
            isSpecialNeeds = true,
            notes = "শ্রেণিকক্ষে বিশেষ মনোযোগ প্রয়োজন"
        )
        db.surveyDao().insertSurvey(survey)
    }
}
