package com.oliver.zylka.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Kapselt den Zugriff auf Firebase Authentication.
 *
 * Firebase merkt sich das eingeloggte Konto auf dem Gerät automatisch
 * (der Sitzungs-Token wird lokal gespeichert). Man muss sich nach dem
 * ersten Login also nicht erneut anmelden, solange man sich nicht aktiv
 * abmeldet - siehe [currentUser] und [SplashActivity][com.oliver.zylka.SplashActivity].
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun login(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Anmeldung fehlgeschlagen.")
    }

    suspend fun register(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Registrierung fehlgeschlagen.")
        saveUserProfile(user)
        return user
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * Legt ein minimales Profil-Dokument in der neuen Collection "users" an.
     * Bewusst getrennt von den alten Daten aus dem Vorgängerprojekt in dieser
     * Firebase-Datenbank - diese bleiben unangetastet.
     */
    private suspend fun saveUserProfile(user: FirebaseUser) {
        val data = hashMapOf(
            "email" to user.email,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }
}
