package com.mehul.inkless.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mehul.inkless.auth.AuthRepository
import com.mehul.inkless.auth.CredentialManagerGoogleIdTokens
import com.mehul.inkless.auth.FirebaseAuthRepository
import com.mehul.inkless.auth.GoogleIdTokens
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
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindings {

    @Binds
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindGoogleIdTokens(impl: CredentialManagerGoogleIdTokens): GoogleIdTokens
}
