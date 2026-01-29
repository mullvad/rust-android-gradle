package net.mullvad.androidrust

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity(), JNICallback {
    val state = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LaunchedEffect(Unit) { invokeCallbackViaJNI(this@MainActivity) }
                    val stringValue = state.collectAsStateWithLifecycle()
                    Greeting(
                        name = stringValue.value.toString(),
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    // Used to load the 'rust' library on application startup.
    companion object {
        init {
            System.loadLibrary("rust")
        }
    }

    /**
     * A native method that is implemented by the 'rust' native library, which is packaged with this
     * application.
     */
    external fun invokeCallbackViaJNI(callback: JNICallback?)

    override fun callback(string: String?) {
        state.value = string
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "$name!", modifier = modifier)
}
