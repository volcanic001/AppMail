package com.david.mailapp.data.repository

import com.david.mailapp.domain.model.Email
import com.david.mailapp.domain.model.EmailBodyKind
import com.david.mailapp.domain.model.EmailContentState

internal fun Email.withoutCachedContent(): Email = copy(
    body = "",
    cleanBody = "",
    contentState = EmailContentState.NOT_FETCHED,
    bodyKind = EmailBodyKind.UNKNOWN,
    inlineReferences = emptyList(),
    cachedContentBytes = 0L,
    contentLastAccessEpochMs = 0L
)
