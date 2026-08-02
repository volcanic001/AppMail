package com.david.mailapp.ui.navigation

import com.david.mailapp.feature.compose.ComposeMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

        val compose = MainRoute.Compose(ComposeMode.REPLY, "msg123")
        val composeStr = json.encodeToString<MainRoute>(compose)
        val composeDecoded = json.decodeFromString<MainRoute>(composeStr) as MainRoute.Compose
        assertEquals(ComposeMode.REPLY, composeDecoded.mode)
        assertEquals("msg123", composeDecoded.originalEmailId)
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

        // REPLY con originalEmailId = null -> Excepcion
        try {
            MainRoute.Compose(ComposeMode.REPLY, null)
            fail("REPLY mode should throw exception if originalEmailId is null")
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
    }
}
