package com.mehul.inkless.auth

/**
 * Everything the auth layer can fail with, already worded for the user. Login and reset share one
 * message for "no such account" and "wrong password" so the screens never reveal who has an account.
 */
enum class AuthFailure(val message: String) {
    WRONG_CREDENTIALS("Wrong username/email or password."),
    WRONG_PASSWORD("That password isn't right."),
    WRONG_GOOGLE_ACCOUNT("That's a different Google account from the one on this Inkless account."),
    INVALID_EMAIL("That doesn't look like an email address."),
    WEAK_PASSWORD("That password is too easy to guess. Try a longer one."),
    EMAIL_UNAVAILABLE(
        "Couldn't create an account with that email. If you already have one, log in or reset your password.",
    ),
    USERNAME_TAKEN("That username was just taken. Try another one."),
    ACCOUNT_EXISTS_WITH_PASSWORD(
        "This email already has an Inkless account. Log in with your password, then link Google in Settings.",
    ),
    GOOGLE_ALREADY_USED("That Google account is already linked to a different Inkless account."),
    GOOGLE_NO_ACCOUNT("There's no Google account on this phone. Add one in system settings first."),
    GOOGLE_UNAVAILABLE("Google sign-in isn't available right now."),
    CANCELLED(""),
    RECENT_LOGIN_REQUIRED("For your security, log out and log back in, then try again."),
    TOO_MANY_REQUESTS("Too many attempts. Wait a few minutes and try again."),
    NETWORK("No internet connection. Check it and try again."),
    UNKNOWN("Something went wrong. Try again."),
}

class AuthException(val failure: AuthFailure, cause: Throwable? = null) : Exception(failure.name, cause)
