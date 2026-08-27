package com.example

import com.example.util.CsvDataType
import com.example.util.CsvUtils
import com.example.util.PdfExportStyleOptions
import com.example.util.PdfExportUtils
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
  fun testPdfHtmlGeneration() {
    val sampleStudents = CsvUtils.buildStudentsFromMappedRows(
      listOf(listOf("১ম শ্রেণি", "1", "করিম উদ্দিন", "রহিম মিয়া")),
      mapOf(0 to "studentClass", 1 to "rollNumber", 2 to "name", 3 to "fatherName")
    )
    val html = PdfExportUtils.generateStudentListPdfHtml(
      schoolInfo = null,
      students = sampleStudents,
      options = PdfExportStyleOptions(
        title = "পরীক্ষামূলক তালিকা",
        includeImages = true,
        isLandscape = true
      )
    )
    assertTrue(html.contains("করিম উদ্দিন"))
    assertTrue(html.contains("পরীক্ষামূলক তালিকা"))
    assertTrue(html.contains("A4 landscape"))
  }
}
