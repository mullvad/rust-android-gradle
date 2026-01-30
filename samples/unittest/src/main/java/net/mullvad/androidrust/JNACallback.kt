package net.mullvad.androidrust

import com.sun.jna.Callback

interface JNACallback : Callback {
    fun invoke(string: String?)
}
