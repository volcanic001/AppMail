package com.david.mailapp.ui.navigation

import com.david.mailapp.feature.compose.ComposeArgs
import com.david.mailapp.feature.compose.ComposeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class NavigationTest {

    @Test
    fun `los seis destinos de MainRoute existen`() {
        assertNotNull(MainRoute.Inbox)
        assertNotNull(MainRoute.Trash)
        assertNotNull(MainRoute.Settings)
        assertNotNull(MainRoute.Search)
        assertNotNull(MainRoute.EmailDetail("test-id"))
        assertNotNull(MainRoute.Compose(ComposeMode.WRITE))
    }

    @Test
    fun `drawer destinations conserva Inbox Trash Settings en ese orden`() {
        val all = DrawerDestination.all
        assertEquals(3, all.size)
        assertTrue(all[0].route is MainRoute.Inbox)
        assertTrue(all[1].route is MainRoute.Trash)
        assertTrue(all[2].route is MainRoute.Settings)
    }

    @Test
    fun `drawer destinations no incluye Search EmailDetail ni Compose`() {
        val all = DrawerDestination.all
        assertTrue(all.none { it.route is MainRoute.Search })
        assertTrue(all.none { it.route is MainRoute.EmailDetail })
        assertTrue(all.none { it.route is MainRoute.Compose })
    }

    @Test
    fun `EmailDetail conserva su argumento emailId`() {
        val emailId = "abc123"
        val screen = MainRoute.EmailDetail(emailId)
        assertEquals(emailId, screen.emailId)
    }

    @Test
    fun `Compose conserva su argumento ComposeArgs minima`() {
        val screen = MainRoute.Compose(ComposeMode.WRITE)
        assertEquals(ComposeMode.WRITE, screen.mode)
        assertEquals(null, screen.originalEmailId)

        val screenReply = MainRoute.Compose(ComposeMode.REPLY, "123")
        assertEquals(ComposeMode.REPLY, screenReply.mode)
        assertEquals("123", screenReply.originalEmailId)
    }

    @Test
    fun `EmailDetail diferentes IDs no son iguales`() {
        val screen1 = MainRoute.EmailDetail("id1")
        val screen2 = MainRoute.EmailDetail("id2")
        assertTrue(screen1 != screen2)
    }

    @Test
    fun `Compose diferentes argumentos no son iguales`() {
        val screen1 = MainRoute.Compose(ComposeMode.WRITE, null)
        val screen2 = MainRoute.Compose(ComposeMode.REPLY, "123")
        assertTrue(screen1 != screen2)
    }

    @Test
    fun `serializacion y reconstruccion de MainRoute`() {
        val json = Json { ignoreUnknownKeys = true }

        val inboxStr = json.encodeToString<MainRoute>(MainRoute.Inbox)
        val inboxDecoded = json.decodeFromString<MainRoute>(inboxStr)
        assertTrue(inboxDecoded is MainRoute.Inbox)

        val trashStr = json.encodeToString<MainRoute>(MainRoute.Trash)
        val trashDecoded = json.decodeFromString<MainRoute>(trashStr)
        assertTrue(trashDecoded is MainRoute.Trash)

        val settingsStr = json.encodeToString<MainRoute>(MainRoute.Settings)
        val settingsDecoded = json.decodeFromString<MainRoute>(settingsStr)
        assertTrue(settingsDecoded is MainRoute.Settings)

        val searchStr = json.encodeToString<MainRoute>(MainRoute.Search)
        val searchDecoded = json.decodeFromString<MainRoute>(searchStr)
        assertTrue(searchDecoded is MainRoute.Search)

        val detail = MainRoute.EmailDetail("msg123")
        val detailStr = json.encodeToString<MainRoute>(detail)
        val detailDecoded = json.decodeFromString<MainRoute>(detailStr) as MainRoute.EmailDetail
        assertEquals("msg123", detailDecoded.emailId)

        val composeWrite = MainRoute.Compose(ComposeMode.WRITE, null)
        val composeWriteStr = json.encodeToString<MainRoute>(composeWrite)
        val composeWriteDecoded = json.decodeFromString<MainRoute>(composeWriteStr) as MainRoute.Compose
        assertEquals(ComposeMode.WRITE, composeWriteDecoded.mode)
        assertEquals(null, composeWriteDecoded.originalEmailId)

        val composeReply = MainRoute.Compose(ComposeMode.REPLY, "msg123")
        val composeReplyStr = json.encodeToString<MainRoute>(composeReply)
        val composeReplyDecoded = json.decodeFromString<MainRoute>(composeReplyStr) as MainRoute.Compose
        assertEquals(ComposeMode.REPLY, composeReplyDecoded.mode)
        assertEquals("msg123", composeReplyDecoded.originalEmailId)

        val composeForward = MainRoute.Compose(ComposeMode.FORWARD, "msg456")
        val composeForwardStr = json.encodeToString<MainRoute>(composeForward)
        val composeForwardDecoded = json.decodeFromString<MainRoute>(composeForwardStr) as MainRoute.Compose
        assertEquals(ComposeMode.FORWARD, composeForwardDecoded.mode)
        assertEquals("msg456", composeForwardDecoded.originalEmailId)
    }

    @Test
    fun `EmailDetail rechaza IDs vacios o en blanco`() {
        try {
            MainRoute.EmailDetail("")
            fail("Should fail on empty emailId")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            MainRoute.EmailDetail("   ")
            fail("Should fail on blank emailId")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `ComposeArgs Reply y Forward rechazan IDs vacios o en blanco`() {
        try {
            ComposeArgs.Reply("")
            fail("Should fail on empty id")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            ComposeArgs.Reply("   ")
            fail("Should fail on blank id")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            ComposeArgs.Forward("")
            fail("Should fail on empty id")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            ComposeArgs.Forward("   ")
            fail("Should fail on blank id")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `compose exige originalEmailId segun el modo`() {
        // WRITE con originalEmailId = null -> OK
        try {
            MainRoute.Compose(ComposeMode.WRITE, null)
        } catch (e: IllegalArgumentException) {
            fail("WRITE mode should allow null originalEmailId")
        }

        // WRITE con originalEmailId no null -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.WRITE, "id")
            fail("WRITE mode should throw exception if originalEmailId is not null")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // WRITE con originalEmailId vacio -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.WRITE, "")
            fail("WRITE mode should throw exception if originalEmailId is empty")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // WRITE con originalEmailId en blanco -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.WRITE, "   ")
            fail("WRITE mode should throw exception if originalEmailId is blank")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // REPLY con originalEmailId = null -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.REPLY, null)
            fail("REPLY mode should throw exception if originalEmailId is null")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // REPLY con originalEmailId vacio -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.REPLY, "")
            fail("REPLY mode should throw exception if originalEmailId is empty")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // REPLY con originalEmailId en blanco -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.REPLY, "   ")
            fail("REPLY mode should throw exception if originalEmailId is blank")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // FORWARD con originalEmailId = null -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.FORWARD, null)
            fail("FORWARD mode should throw exception if originalEmailId is null")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // FORWARD con originalEmailId vacio -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.FORWARD, "")
            fail("FORWARD mode should throw exception if originalEmailId is empty")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // FORWARD con originalEmailId en blanco -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.FORWARD, "   ")
            fail("FORWARD mode should throw exception if originalEmailId is blank")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        // IDs validos con espacios internos o finales se conservan exactamente
        val idWithInternalAndTrailingSpaces = "some id here "
        val replyCompose = MainRoute.Compose(ComposeMode.REPLY, idWithInternalAndTrailingSpaces)
        assertEquals(idWithInternalAndTrailingSpaces, replyCompose.originalEmailId)
    }

    @Test
    fun `mapper produce los ComposeArgs correctos`() {
        val writeRoute = MainRoute.Compose(ComposeMode.WRITE, null)
        val replyRoute = MainRoute.Compose(ComposeMode.REPLY, "reply123")
        val forwardRoute = MainRoute.Compose(ComposeMode.FORWARD, "forward123")

        assertTrue(writeRoute.toComposeArgs() is ComposeArgs.Write)

        val replyArgs = replyRoute.toComposeArgs() as ComposeArgs.Reply
        assertEquals("reply123", replyArgs.originalEmailId)

        val forwardArgs = forwardRoute.toComposeArgs() as ComposeArgs.Forward
        assertEquals("forward123", forwardArgs.originalEmailId)
    }

    @Test
    fun `IDs con caracteres sensibles se conservan exactamente`() {
        val sensitiveId = "id/with/slashes?and=queries&spaces= "
        val detail = MainRoute.EmailDetail(sensitiveId)
        assertEquals(sensitiveId, detail.emailId)

        val reply = MainRoute.Compose(ComposeMode.REPLY, sensitiveId)
        assertEquals(sensitiveId, reply.originalEmailId)
        val replyArgs = reply.toComposeArgs() as ComposeArgs.Reply
        assertEquals(sensitiveId, replyArgs.originalEmailId)
    }

    @Test
    fun `prueba estructural de campos en MainRoute`() {
        val detailFields = MainRoute.EmailDetail::class.java.declaredFields
            .filter { !it.isSynthetic && !it.name.startsWith("$") && !it.name.contains("$") && it.name != "Companion" && it.name != "serialVersionUID" }
        assertEquals(1, detailFields.size)
        assertEquals("emailId", detailFields.first().name)

        val composeFields = MainRoute.Compose::class.java.declaredFields
            .filter { !it.isSynthetic && !it.name.startsWith("$") && !it.name.contains("$") && it.name != "Companion" && it.name != "serialVersionUID" }
        assertEquals(2, composeFields.size)
        val fieldNames = composeFields.map { it.name }
        assertTrue(fieldNames.contains("mode"))
        assertTrue(fieldNames.contains("originalEmailId"))
    }

    @Test
    fun `ningun campo de ruta o ComposeArgs tiene tipo Email`() {
        fun checkClass(clazz: Class<*>) {
            clazz.declaredFields.forEach { field ->
                val typeName = field.type.name
                assertFalse(
                    "Field ${field.name} of ${clazz.simpleName} must not be Email type",
                    typeName.contains("com.david.mailapp.domain.model.Email")
                )
            }
        }

        checkClass(MainRoute.EmailDetail::class.java)
        checkClass(MainRoute.Compose::class.java)
        checkClass(ComposeArgs.Reply::class.java)
        checkClass(ComposeArgs.Forward::class.java)
    }
}
