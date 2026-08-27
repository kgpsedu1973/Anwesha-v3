package com.example

import com.example.data.local.entity.CustomFieldEntity
import com.example.data.local.entity.FormulaRuleEntity
import com.example.data.local.entity.StudentEntity
import com.example.data.local.util.FormulaEvaluator
import com.example.util.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCsvParsingAndAutoDetection() {
    val sampleCsv = """
      শ্রেণি,রোল,শিক্ষার্থীর নাম,পিতার নাম,মোবাইল
      ১ম শ্রেণি,১,মুহাম্মদ আবদুল্লাহ,মোঃ রফিকুল ইসলাম,01711000111
      ২য় শ্রেণি,২,ফাতেমা তুজ জোহরা,মোঃ জাকির হোসেন,01812345678
    """.trimIndent()

    val (headers, rows) = CsvUtils.parseCsvContent(sampleCsv)
    assertEquals(5, headers.size)
    assertEquals(2, rows.size)

    val fields = CsvUtils.getFieldsForType(CsvDataType.STUDENTS)
    val mapping = CsvUtils.autoDetectColumnMapping(headers, fields)

    assertEquals("studentClass", mapping[0])
    assertEquals("rollNumber", mapping[1])
    assertEquals("name", mapping[2])
    assertEquals("fatherName", mapping[3])
    assertEquals("mobile", mapping[4])

    val students = CsvUtils.buildStudentsFromMappedRows(rows, mapping)
    assertEquals(2, students.size)
    assertEquals("১ম শ্রেণি", students[0].studentClass)
    assertEquals(1, students[0].rollNumber)
    assertEquals("মুহাম্মদ আবদুল্লাহ", students[0].name)
    assertEquals("মোঃ রফিকুল ইসলাম", students[0].fatherName)
    assertEquals("01711000111", students[0].mobile)
  }

  @Test
  fun testFormulaEvaluatorAndCalculatedFields() {
    val student = StudentEntity(
      id = "STD-001",
      studentClass = "৫ম শ্রেণি",
      rollNumber = 5,
      name = "রাকিব হাসান",
      fatherName = "কামাল হোসেন",
      motherName = "ফাতেমা",
      birthDate = "2015-05-10",
      mobile = "01700112233",
      village = "পশ্চিম রামপুর",
      academicYear = "2026",
      address = "পশ্চিম রামপুর",
      birthRegNumber = "123456789",
      gender = "ছাত্র",
      isSpecialNeeds = false,
      status = "Current",
      customValuesJson = "{\"cf_blood\":\"B+\",\"cf_prev_gpa\":\"4.80\"}"
    )

    val customFields = listOf(
      CustomFieldEntity(id = "cf_blood", name = "রক্তের গ্রুপ", fieldType = "Dropdown"),
      CustomFieldEntity(id = "cf_stipend", name = "উপবৃত্তি সুবিধা", fieldType = "Calculated", isCalculated = true)
    )

    val formulaRules = listOf(
      FormulaRuleEntity(
        id = "rule_stipend",
        ruleName = "উপবৃত্তি যোগ্যতা",
        targetFieldName = "উপবৃত্তি সুবিধা",
        sourceField = "village",
        operator = "IN_LIST",
        conditionValue = "পশ্চিম রামপুর,আমতলী",
        resultIfTrue = "যোগ্য",
        resultIfFalse = "অযোগ্য"
      )
    )

    // Standard field extract
    val name = FormulaEvaluator.getFieldValue(student, "name")
    assertEquals("রাকিব হাসান", name)

    // Custom field extract
    val blood = FormulaEvaluator.getFieldValue(student, "cf_blood", customFields)
    assertEquals("B+", blood)

    // Calculated field extract via rule
    val stipend = FormulaEvaluator.getFieldValue(student, "cf_stipend", customFields, formulaRules)
    assertEquals("যোগ্য", stipend)

    // Conditional rule testing
    val ruleMet = ConditionalRule("studentClass", "EQUALS", "৫ম শ্রেণি").isMet(student, customFields)
    assertTrue(ruleMet)

    val ruleNotMet = ConditionalRule("studentClass", "EQUALS", "৪র্থ শ্রেণি").isMet(student, customFields)
    assertFalse(ruleNotMet)
  }

  @Test
  fun testStudentViewConfigPresets() {
    val defaultPreset = StudentViewConfigManager.getDefaultPresetView()
    assertNotNull(defaultPreset)
    assertEquals("default_view", defaultPreset.id)
    assertTrue(defaultPreset.headerArea.fields.isNotEmpty())
    assertTrue(defaultPreset.secondaryArea.fields.isNotEmpty())
    assertTrue(defaultPreset.actions.isNotEmpty())

    val guardianPreset = StudentViewConfigManager.getCompactGuardianPreset()
    assertEquals("guardian_contact_view", guardianPreset.id)
    assertEquals("#00897B", guardianPreset.visual.colorThemeHex)

    val listPreset = StudentViewConfigManager.getListViewPreset()
    assertEquals("LIST", listPreset.visual.viewMode)
  }
}
