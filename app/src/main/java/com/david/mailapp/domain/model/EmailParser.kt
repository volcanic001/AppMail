package com.david.mailapp.domain.model

data class ParsedSender(
    val displayCollapsed: String,
    val displayFull: String,
    val email: String
)

fun parseEmailSender(from: String): ParsedSender {
    val emailRegex = Regex("<([^>]+)>")
    val matchResult = emailRegex.find(from)
    val parsed = if (matchResult != null) {
        val email = matchResult.groupValues[1].trim()
        var name = from.substring(0, matchResult.range.first).trim()
        if (name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length - 1).trim()
        }
        if (name.isEmpty()) {
            Pair(email, "")
        } else {
            Pair(name, email)
        }
    } else {
        Pair(from.trim(), "")
    }

    val displayName = parsed.first
    val email = parsed.second

    val isEmailAddress = displayName.contains("@")
    val displayCollapsed = if (isEmailAddress && displayName.length > 20) {
        val parts = displayName.split("@")
        val local = parts[0]
        val domain = parts.getOrNull(1) ?: ""
        val truncatedLocal = if (local.length > 8) local.substring(0, 6) + ".." else local
        val truncatedDomain = if (domain.length > 10) domain.substring(0, 8) + ".." else domain
        if (truncatedDomain.isNotEmpty()) "$truncatedLocal@$truncatedDomain" else truncatedLocal
    } else {
        displayName
    }
    return ParsedSender(
        displayCollapsed = displayCollapsed,
        displayFull = displayName,
        email = email
    )
}
