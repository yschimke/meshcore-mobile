package ee.schimke.meshcore.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.wear.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

class LicenseActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MeshcoreWearTheme {
        // Parse the bundled licence list synchronously: `produceLibraries()` loads it on a
        // background dispatcher and draws nothing until it lands, so the activity's preview
        // capture raced the load and flipped between an empty and a filled screen across PRs.
        val context = LocalContext.current
        val libraries = remember(context) { Libs.Builder().withContext(context).build() }
        LibrariesContainer(libraries)
      }
    }
  }
}
