package ru.karasevm.privatednstoggle.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import ru.karasevm.privatednstoggle.R

class AboutLibsActivity : NexoActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = nexoColors(this@AboutLibsActivity)) {
                val libraries by produceLibraries(R.raw.aboutlibraries)
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Licenças") }, navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "Voltar")
                        }
                    })
                }) { padding ->
                    LibrariesContainer(libraries = libraries, contentPadding = padding)
                }
            }
        }
    }
}
