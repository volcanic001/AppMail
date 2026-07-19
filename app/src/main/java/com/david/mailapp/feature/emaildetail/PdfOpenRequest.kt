package com.david.mailapp.feature.emaildetail

/**
 * [PdfExternalActionRequest] now replaces this single-purpose data class.
 * Import PdfExternalActionRequest instead — it provides both [Open] and [Save] variants.
 *
 * @deprecated Use [PdfExternalActionRequest] instead.
 */
@Deprecated("Use PdfExternalActionRequest", replaceWith = ReplaceWith("PdfExternalActionRequest"))
typealias PdfOpenRequest = PdfExternalActionRequest.Open
