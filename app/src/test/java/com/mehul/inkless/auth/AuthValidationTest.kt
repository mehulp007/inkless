package com.mehul.inkless.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthValidationTest {

    @Test
    fun `usernames are 3 to 20 lowercase letters, digits or underscores`() {
        assertThat(AuthValidation.isValidUsername("mehul_07")).isTrue()
        assertThat(AuthValidation.isValidUsername("abc")).isTrue()
        assertThat(AuthValidation.isValidUsername("a".repeat(20))).isTrue()

        assertThat(AuthValidation.isValidUsername("ab")).isFalse()
        assertThat(AuthValidation.isValidUsername("a".repeat(21))).isFalse()
        assertThat(AuthValidation.isValidUsername("Mehul")).isFalse()
        assertThat(AuthValidation.isValidUsername("me hul")).isFalse()
        assertThat(AuthValidation.isValidUsername("me.hul")).isFalse()
        assertThat(AuthValidation.isValidUsername("../users")).isFalse()
    }

    @Test
    fun `username problems explain what is wrong`() {
        assertThat(AuthValidation.usernameProblem("ab")).isEqualTo("At least 3 characters")
        assertThat(AuthValidation.usernameProblem("a".repeat(21))).isEqualTo("20 characters at most")
        assertThat(AuthValidation.usernameProblem("chai-wala")).isEqualTo("Only lowercase letters, digits and _")
        assertThat(AuthValidation.usernameProblem("chai_wala")).isNull()
    }

    @Test
    fun `normalising trims and lowercases`() {
        assertThat(AuthValidation.normalizeUsername("  Mehul_07 ")).isEqualTo("mehul_07")
    }

    @Test
    fun `anything with an at sign counts as an email for login`() {
        assertThat(AuthValidation.isEmail("me@example.com")).isTrue()
        assertThat(AuthValidation.isEmail("mehul_07")).isFalse()
    }

    @Test
    fun `email format check`() {
        assertThat(AuthValidation.emailProblem("me@example.com")).isNull()
        assertThat(AuthValidation.emailProblem(" me@example.co.in ")).isNull()
        assertThat(AuthValidation.emailProblem("me@example")).isNotNull()
        assertThat(AuthValidation.emailProblem("me example@x.com")).isNotNull()
        assertThat(AuthValidation.emailProblem("")).isNotNull()
    }

    @Test
    fun `passwords need 8 characters with a letter and a digit`() {
        assertThat(AuthValidation.passwordProblem("abc123")).isEqualTo("At least 8 characters")
        assertThat(AuthValidation.passwordProblem("12345678")).isEqualTo("Add at least one letter")
        assertThat(AuthValidation.passwordProblem("abcdefgh")).isEqualTo("Add at least one digit")
        assertThat(AuthValidation.passwordProblem("chai2026")).isNull()
    }

    @Test
    fun `strength goes up with length, mixed case and symbols`() {
        assertThat(AuthValidation.passwordStrength("abcdefgh")).isEqualTo(PasswordStrength.WEAK)
        assertThat(AuthValidation.passwordStrength("chai2026")).isEqualTo(PasswordStrength.OKAY)
        assertThat(AuthValidation.passwordStrength("Chai2026")).isEqualTo(PasswordStrength.OKAY)
        assertThat(AuthValidation.passwordStrength("Chai2026!")).isEqualTo(PasswordStrength.STRONG)
        assertThat(AuthValidation.passwordStrength("Chaiwala2026hostel")).isEqualTo(PasswordStrength.STRONG)
    }
}
