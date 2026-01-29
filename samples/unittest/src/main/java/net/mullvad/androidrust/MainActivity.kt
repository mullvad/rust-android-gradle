package net.mullvad.androidrust

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity(), JNACallback, JNICallback {
    interface RustLibrary : com.sun.jna.Library {
        fun invokeCallbackViaJNA(callback: JNACallback?): Int

        companion object {
            val INSTANCE: RustLibrary =
                com.sun.jna.Native.load<RustLibrary?>("rust", RustLibrary::class.java)
                    as RustLibrary
        }
    }

    val text = MutableStateFlow("")

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val text = text.collectAsStateWithLifecycle()
                    Text(modifier = Modifier.padding(innerPadding), text = text.value)
                }
            }
        }

        invokeCallbackViaJNA(this)
        invokeCallbackViaJNI(this)
    }

    override fun invoke(string: String?) {
        text.update { it + "From JNA: " + string + "\n" }
    }

    override fun callback(string: String?) {
        text.update { it + "From JNI: " + string + "\n" }
    }

    companion object {
        private const val TAG = "MainActivity"

        init {
            // On Android, this can be just:
            // System.loadLibrary("rust");
            // But when running as a unit test, we need to fish the libraries from
            // Java resources and configure the classpath.  We use JNA for that.
            val LIBRARY = com.sun.jna.NativeLibrary.getInstance("rust")
            System.load(LIBRARY.getFile().getPath())
        }

        /**
         * A native method that is implemented by the 'rust' native library, which is packaged with
         * this application.
         */
        @JvmStatic external fun invokeCallbackViaJNI(callback: JNICallback?)

        @JvmStatic
        fun invokeCallbackViaJNA(callback: JNACallback?) {
            RustLibrary.INSTANCE.invokeCallbackViaJNA(callback)
        }
    }
}
