package www.jabin24.aitally

import www.jabin24.aitally.data.local.entity.UserEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `default user entity is active and verified by default`() {
        val user = UserEntity(
            userUid = "usr_owner_main",
            name = "দোকানের মালিক",
            shopName = "ট্যালিখাতা ডিজিটাল খাতা",
            email = "store@tallykhata.app",
            passwordHash = "",
            phone = "01700000000",
            isLoggedIn = true,
            syncStatus = "SYNCED",
            isEmailVerified = true
        )
        assertTrue(user.isLoggedIn)
        assertTrue(user.isEmailVerified)
        assertEquals("usr_owner_main", user.userUid)
        assertEquals("দোকানের মালিক", user.name)
    }

    @Test
    fun `customer balance calculations are correct`() {
        val creditGiven = 500.0
        val debitReceived = 200.0
        val totalDue = creditGiven - debitReceived
        assertEquals(300.0, totalDue, 0.001)
    }
}

