package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SchoolDatabaseModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ANWESHA", appName)
  }

  @Test
  fun `verify master database model serialization and deserialization`() {
    val model = SchoolDatabaseModel.createInitial(
      schoolName = "পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়",
      eiinCode = "123456",
      adminName = "মোঃ রফিকুল ইসলাম",
      adminEmail = "admin@anweshaschool.edu.bd",
      adminPhone = "01711223344",
      pinHash = "1234"
    )

    val jsonString = model.toJson(indent = true)
    assertNotNull(jsonString)

    val parsed = SchoolDatabaseModel.fromJson(jsonString)
    assertNotNull(parsed)
    assertEquals("123456", parsed?.schoolInfo?.eiinCode)
    assertEquals("পশ্চিম রামপুর সরকারি প্রাথমিক বিদ্যালয়", parsed?.schoolInfo?.schoolName)
    assertEquals(1, parsed?.usersList?.size)
    assertEquals("Admin", parsed?.usersList?.first()?.role)
  }
}

