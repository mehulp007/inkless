package com.mehul.inkless.auth

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usernames live in `usernames/{username}` → `{ uid, email }`. The rules let anyone *get* one doc
 * (that's how "log in with username" finds the email) but never list them.
 */
@Singleton
class UsernameService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val usernames get() = firestore.collection("usernames")
    private val users get() = firestore.collection("users")

    suspend fun isAvailable(username: String): Boolean =
        !usernames.document(username).get(Source.SERVER).await().exists()

    suspend fun emailFor(username: String): String? =
        usernames.document(username).get(Source.SERVER).await().getString("email")

    /** Null when the profile doesn't exist yet. Falls back to the offline cache when there's no network. */
    suspend fun usernameOf(uid: String): String? =
        users.document(uid).get().await().getString("username")

    /**
     * Claims the username and creates the profile in one transaction, so two people racing for the
     * same name can't both get it.
     */
    suspend fun claim(uid: String, email: String, username: String) {
        val claimRef = usernames.document(username)
        val profileRef = users.document(uid)
        firestore.runTransaction { tx ->
            if (tx.get(claimRef).exists()) {
                throw FirebaseFirestoreException("Username taken", FirebaseFirestoreException.Code.ALREADY_EXISTS)
            }
            tx.set(claimRef, mapOf("uid" to uid, "email" to email))
            tx.set(
                profileRef,
                mapOf("username" to username, "email" to email, "createdAt" to FieldValue.serverTimestamp()),
            )
        }.await()
    }

    /** Frees the username and removes the profile. Used when deleting the account. */
    suspend fun release(uid: String) {
        val username = users.document(uid).get(Source.SERVER).await().getString("username")
        firestore.batch().apply {
            if (username != null) delete(usernames.document(username))
            delete(users.document(uid))
        }.commit().await()
    }
}
