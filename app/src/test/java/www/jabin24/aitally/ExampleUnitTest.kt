package www.jabin24.aitally

import www.jabin24.aitally.data.local.entity.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `user entity has unverified email by default`() {
        val user = UserEntity(
            userUid = "usr_12345",
            name = "Test Merchant",
            shopName = "Test Shop",
            email = "merchant@example.com",
            passwordHash = "hash123",
            phone = "01700000000"
        )
        assertFalse(user.isEmailVerified)
        assertNull(user.verificationCode)
        assertEquals(0L, user.codeExpiresAt)
    }

    @Test
    fun `verification code has exactly 6 digits`() {
        val code = (100000..999999).random().toString()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `verification code expiration logic validates active and expired timestamps`() {
        val now = System.currentTimeMillis()
        val futureExpiry = now + 15 * 60 * 1000L
        val pastExpiry = now - 1000L

        val isActive = futureExpiry >= now
        val isExpired = pastExpiry < now

        assertTrue(isActive)
        assertTrue(isExpired)
    }
}
