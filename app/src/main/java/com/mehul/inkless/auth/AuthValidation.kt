package com.mehul.inkless.auth

enum class PasswordStrength { WEAK, OKAY, STRONG }

object AuthValidation {

    private val USERNAME = Regex("^[a-z0-9_]{3,20}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Usernames are stored lowercase, so "Mehul_07" and "mehul_07" are the same name. */
    fun normalizeUsername(input: String): String = input.trim().lowercase()

    fun isValidUsername(username: String): Boolean = USERNAME.matches(username)

    fun usernameProblem(username: String): String? = when {
        username.length < 3 -> "At least 3 characters"
        username.length > 20 -> "20 characters at most"
        !USERNAME.matches(username) -> "Only lowercase letters, digits and _"
        else -> null
    }

    /** The login and reset fields take either; anything with an @ is treated as an email. */
    fun isEmail(identifier: String): Boolean = '@' in identifier

    fun emailProblem(email: String): String? =
        if (EMAIL.matches(email.trim())) null else "That doesn't look like an email address"

    fun passwordProblem(password: String): String? = when {
        password.length < 8 -> "At least 8 characters"
        password.none { it.isLetter() } -> "Add at least one letter"
        password.none { it.isDigit() } -> "Add at least one digit"
        else -> null
    }

    fun passwordStrength(password: String): PasswordStrength {
        if (passwordProblem(password) != null) return PasswordStrength.WEAK
        var score = 0
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return if (score >= 2) PasswordStrength.STRONG else PasswordStrength.OKAY
    }
}
