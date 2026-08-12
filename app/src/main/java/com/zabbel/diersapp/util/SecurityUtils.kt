package com.zabbel.diersapp.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    /**
     * Erzeugt einen SHA-256 Hash aus einer PIN und einem Salt.
     */
    fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    /**
     * Erzeugt einen neuen, kryptografisch sicheren 16-Byte Salt.
     */
    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }
}
