package com.david.mailapp.feature.compose

import androidx.lifecycle.SavedStateHandle
import com.david.mailapp.core.localization.StringProvider
import com.david.mailapp.data.remote.provider.ReplyContext
import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Subfase 4 — destinatario de Responder según etiquetas reales.
 *
 * - Correo con etiqueta SENT → se responde a Email.to.
 * - Cualquier otro correo → se responde a Email.from.
 * - EmailFolder.Other no se usa para inferir "enviado".
 * - Reenviar no precarga destinatario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeReplyRecipientTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(mainDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun email(
        id: String,
        from: String,
        to: String,
        labels: List<String>,
        folder: EmailFolder
    ): Email = Email(
        id = id, threadId = "t1", from = from, fromInitials = "F",
        to = to, subject = "S", snippet = "", timestamp = 0L,
        isRead = false, isStarred = false, hasAttachments = false,
        labels = labels, folder = folder
    )

    private fun replyViewModel(original: Email): ComposeViewModel {
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = original
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String,
                body: String, replyContext: ReplyContext?
            ) { /* no-op */ }
        }
        return ComposeViewModel(
            args = ComposeArgs.Reply(original.id),
            emailSource = source,
            stringProvider = TestStringProvider(),
            savedStateHandle = SavedStateHandle()
        )
    }

    private fun forwardViewModel(original: Email): ComposeViewModel {
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = original
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String,
                body: String, replyContext: ReplyContext?
            ) { /* no-op */ }
        }
        return ComposeViewModel(
            args = ComposeArgs.Forward(original.id),
            emailSource = source,
            stringProvider = TestStringProvider(),
            savedStateHandle = SavedStateHandle()
        )
    }

    @Test
    fun replyReceivedEmail_usesFrom_asRecipient() = runTest(mainDispatcher) {
        val original = email("r1", "sender@x.com", "me@x.com", listOf("INBOX"), EmailFolder.Inbox)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("sender@x.com", vm.uiState.value.toField)
    }

    @Test
    fun replySentEmail_usesTo_asRecipient() = runTest(mainDispatcher) {
        val original = email("s1", "me@x.com", "recipient@x.com", listOf("SENT"), EmailFolder.Other)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("recipient@x.com", vm.uiState.value.toField)
    }

    @Test
    fun replySentEmail_preservesAllRecipients() = runTest(mainDispatcher) {
        val recipients = "Ana <ana@x.com>, Bob <bob@x.com>"
        val original = email("s2", "me@x.com", recipients, listOf("SENT"), EmailFolder.Other)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals(recipients, vm.uiState.value.toField)
    }

    @Test
    fun replyArchivedEmail_usesFrom_asRecipient() = runTest(mainDispatcher) {
        val original = email("a1", "archiver@x.com", "me@x.com", listOf("ARCHIVE"), EmailFolder.Other)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("archiver@x.com", vm.uiState.value.toField)
    }

    @Test
    fun replyTrashWithoutSent_usesFrom_asRecipient() = runTest(mainDispatcher) {
        val original = email("t1", "trash@x.com", "me@x.com", listOf("TRASH"), EmailFolder.Trash)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("trash@x.com", vm.uiState.value.toField)
    }

    @Test
    fun replyTrashWithSent_usesTo_asRecipient() = runTest(mainDispatcher) {
        val original = email("t2", "me@x.com", "trashed-sent@x.com", listOf("TRASH", "SENT"), EmailFolder.Trash)
        val vm = replyViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("trashed-sent@x.com", vm.uiState.value.toField)
    }

    @Test
    fun forward_doesNotPreloadRecipient() = runTest(mainDispatcher) {
        val original = email("f1", "sender@x.com", "me@x.com", listOf("INBOX"), EmailFolder.Inbox)
        val vm = forwardViewModel(original)
        mainDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Forward keeps recipient empty", "", vm.uiState.value.toField)
    }

    @Test
    fun replyPreservesThreadAndRfcContext() = runTest(mainDispatcher) {
        val original = email("p1", "sender@x.com", "me@x.com", listOf("INBOX"), EmailFolder.Inbox).copy(
            rfcMessageId = "<mid@x.com>", rfcReferences = "<ref@x.com>"
        )
        var captured: ReplyContext? = null
        val source = object : ComposeEmailSource {
            override suspend fun getUserEmail(): String = "me@test.com"
            override suspend fun getEmailById(emailId: String): Email? = original
            override suspend fun sendEmail(
                to: String, cc: String?, bcc: String?, subject: String,
                body: String, replyContext: ReplyContext?
            ) { captured = replyContext }
        }
        val vm = ComposeViewModel(
            args = ComposeArgs.Reply(original.id),
            emailSource = source,
            stringProvider = ReplyStringProvider(),
            savedStateHandle = SavedStateHandle()
        )
        mainDispatcher.scheduler.advanceUntilIdle()

        vm.onBodyChanged("Respuesta")
        vm.onSend()
        mainDispatcher.scheduler.advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(
            "ReplyContext sent (isLoading=${s.isLoadingOriginalEmail} err=${s.originalEmailError} to='${s.toField}' sending=${s.isSending} result=${s.sendResult})",
            captured != null
        )
        assertEquals("t1", captured?.threadId)
        assertEquals("<mid@x.com>", captured?.inReplyTo)
        assertEquals("<ref@x.com> <mid@x.com>", captured?.references)
    }

    /** StringProvider con patrones válidos para SimpleDateFormat. */
    private class ReplyStringProvider : StringProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                com.david.mailapp.R.string.date_pattern_short -> "dd/MM/yyyy, HH:mm"
                com.david.mailapp.R.string.compose_reply_body_format -> "%1\$s %2\$s escribió:\n> %3\$s"
                com.david.mailapp.R.string.subject_prefix_reply -> "Re: %1\$s"
                else -> ""
            }
        }
    }
}
