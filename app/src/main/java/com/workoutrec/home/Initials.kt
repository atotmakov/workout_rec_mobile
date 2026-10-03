package com.workoutrec.home

/**
 * Avatar initials (data-model.md): first letter of the first two words of [displayName],
 * else the first letter of [email], upper-cased.
 */
fun initials(displayName: String?, email: String): String {
    val words = displayName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    val letters = if (words.isNotEmpty()) words.take(2).map { it.first() } else listOf(email.trim().first())
    return letters.joinToString("").uppercase()
}
