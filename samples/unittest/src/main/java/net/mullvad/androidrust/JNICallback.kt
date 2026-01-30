package net.mullvad.androidrust

interface JNICallback {
    fun callback(string: String?)
}
