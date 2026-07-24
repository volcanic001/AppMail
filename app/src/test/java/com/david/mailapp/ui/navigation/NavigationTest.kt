package com.david.mailapp.ui.navigation

import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM para [Screen].
 *
 * Verifica rutas, orden de [Screen.all], y preservación de argumentos.
 */
class NavigationTest {

    @Test
    fun `los seis destinos existen`() {
        assertNotNull(Screen.Inbox)
        assertNotNull(Screen.Trash)
        assertNotNull(Screen.Settings)
        assertNotNull(Screen.Search)
        assertNotNull(Screen.EmailDetail("test-id"))
        assertNotNull(Screen.Compose(ComposeArgs.Write))
    }

    @Test
    fun `rutas actuales no cambian`() {
        assertEquals("inbox", Screen.Inbox.route)
        assertEquals("trash", Screen.Trash.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("email_detail", Screen.EmailDetail("x").route)
        assertEquals("compose", Screen.Compose(ComposeArgs.Write).route)
    }

    @Test
    fun `Screen_all conserva Inbox Trash Settings en ese orden`() {
        val all = Screen.all
        assertEquals(3, all.size)
        assertTrue(all[0] is Screen.Inbox)
        assertTrue(all[1] is Screen.Trash)
        assertTrue(all[2] is Screen.Settings)
    }

    @Test
    fun `EmailDetail conserva su argumento emailId`() {
        val emailId = "abc123"
        val screen = Screen.EmailDetail(emailId)
        assertEquals(emailId, screen.emailId)
    }

    @Test
    fun `Compose conserva su argumento ComposeArgs Write`() {
        val screen = Screen.Compose(ComposeArgs.Write)
        assertTrue(screen.args is ComposeArgs.Write)
    }

    @Test
    fun `Compose conserva su argumento ComposeArgs Reply`() {
        val email = Email(
            id = "1",
            threadId = "t1",
            from = "from@test.com",
            fromInitials = "F",
            to = "to@test.com",
            subject = "Test",
            snippet = "",
            timestamp = 0L,
            isRead = false,
            isStarred = false,
            hasAttachments = false,
            labels = emptyList(),
            folder = EmailFolder.Inbox
        )
        val screen = Screen.Compose(ComposeArgs.Reply(email))
        assertTrue(screen.args is ComposeArgs.Reply)
        assertEquals("from@test.com", (screen.args as ComposeArgs.Reply).originalEmail.from)
    }

    @Test
    fun `EmailDetail diferentes IDs no son iguales`() {
        val screen1 = Screen.EmailDetail("id1")
        val screen2 = Screen.EmailDetail("id2")
        assertTrue(screen1 != screen2)
    }

    @Test
    fun `Screen all no incluye Search EmailDetail ni Compose`() {
        val all = Screen.all
        assertTrue(all.none { it is Screen.Search })
        assertTrue(all.none { it is Screen.EmailDetail })
        assertTrue(all.none { it is Screen.Compose })
    }
}
