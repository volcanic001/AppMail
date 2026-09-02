package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState

/** Temporary recovery boundary until complete individual persistence is implemented in 3.4. */
internal fun Email.asSummaryOnly(): Email = copy(
    body = "",
    cleanBody = "",
    contentState = EmailContentState.NOT_FETCHED,
    bodyKind = EmailBodyKind.UNKNOWN,
    inlineReferences = emptyList(),
    cachedContentBytes = 0L,
    contentLastAccessEpochMs = 0L
)
