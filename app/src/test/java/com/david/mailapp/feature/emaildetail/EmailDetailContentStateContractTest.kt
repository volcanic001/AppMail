package com.david.mailapp.feature.emaildetail

import com.david.mailapp.domain.model.EmailContentState
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailDetailContentStateContractTest {

    private enum class Action { RECOVER, DELIVER_READY, DELIVER_EMPTY }

    private fun decide(contentState: EmailContentState): Action = when (contentState) {
        EmailContentState.NOT_FETCHED -> Action.RECOVER
        EmailContentState.READY -> Action.DELIVER_READY
        EmailContentState.EMPTY -> Action.DELIVER_EMPTY
    }

    @Test
    fun notFetched_isTheOnlyStateThatRecovers() {
        assertEquals(Action.RECOVER, decide(EmailContentState.NOT_FETCHED))
        assertEquals(Action.DELIVER_READY, decide(EmailContentState.READY))
        assertEquals(Action.DELIVER_EMPTY, decide(EmailContentState.EMPTY))
    }
}
