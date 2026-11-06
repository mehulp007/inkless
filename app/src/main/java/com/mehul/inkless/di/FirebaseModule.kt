package com.mehul.inkless.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.CredentialManagerGoogleIdTokens
import com.mehul.inkless.auth.FirebaseAuthRepository
import com.mehul.inkless.auth.GoogleIdTokens
import com.mehul.inkless.sync.CloudStore
import com.mehul.inkless.sync.FirestoreCloudStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        // The phone's own database is the copy that matters; don't keep a second one on disk.
        firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindings {

    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindGoogleIdTokens(impl: CredentialManagerGoogleIdTokens): GoogleIdTokens

    @Binds
    abstract fun bindCloudStore(impl: FirestoreCloudStore): CloudStore
}
