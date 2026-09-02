package com.david.mailapp.data.remote.provider.gmail

internal object GmailProjections {
    const val LIST_FIELDS = "messages(id,threadId),nextPageToken"
    const val FULL_MESSAGE_FIELDS = "id,threadId,labelIds,snippet,internalDate,payload"
}
