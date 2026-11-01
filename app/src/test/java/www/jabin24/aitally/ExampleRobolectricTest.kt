package www.jabin24.aitally

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import www.jabin24.aitally.data.crypto.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TallyKhata", appName)
  }

  @Test
  fun `crypto manager encrypts and decrypts correctly`() {
    val sampleMemo = "Groceries credit - 5kg Rice, 2L Oil"
    val encrypted = CryptoManager.encrypt(sampleMemo)
    assertTrue(encrypted.startsWith("ENC:"))
    assertNotEquals(sampleMemo, encrypted)

    val decrypted = CryptoManager.decrypt(encrypted)
    assertEquals(sampleMemo, decrypted)
  }

  @Test
  fun `password hashing produces stable sha256 output`() {
    val pass = "secret123"
    val hash1 = CryptoManager.hashPassword(pass)
    val hash2 = CryptoManager.hashPassword(pass)
    assertEquals(hash1, hash2)
    assertEquals(64, hash1.length)
  }
}
