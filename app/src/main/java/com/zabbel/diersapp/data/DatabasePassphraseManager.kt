package com.zabbel.diersapp.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseManager @Inject constructor() {
    private var passphrase: ByteArray? = null

    fun setPassphrase(key: ByteArray) {
        passphrase = key
    }

    fun getPassphrase(): ByteArray? = passphrase

    fun clear() {
        passphrase = null
    }
}