package ee.schimke.meshcore.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.wear.LibrariesContainer
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

class LicenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeshcoreWearTheme {
                val libraries by produceLibraries()
                libraries?.let { LibrariesContainer(it) }
            }
        }
    }
}
