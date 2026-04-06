package ee.schimke.meshcore.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ee.schimke.meshcore.wear.ui.WearNavigation
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeshcoreWearTheme {
                WearNavigation()
            }
        }
    }
}
