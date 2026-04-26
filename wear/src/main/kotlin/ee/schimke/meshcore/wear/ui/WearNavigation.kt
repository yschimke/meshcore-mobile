package ee.schimke.meshcore.wear.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.SwipeDismissableSceneStrategy
import kotlinx.serialization.Serializable

@Serializable data object StatusRoute : NavKey
@Serializable data object ContactsRoute : NavKey
@Serializable data class ReplyRoute(val pubkeyHex: String) : NavKey

@Composable
fun WearNavigation() {
    val backStack = rememberNavBackStack(StatusRoute)
    val viewModel: WearViewModel = viewModel()

    AppScaffold {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(SwipeDismissableSceneStrategy()),
            entryProvider = entryProvider {
                entry<StatusRoute> {
                    StatusScreen(
                        viewModel = viewModel,
                        onViewContacts = { backStack.add(ContactsRoute) },
                    )
                }

                entry<ContactsRoute> {
                    ContactsScreen(
                        viewModel = viewModel,
                        onContactSelected = { contact ->
                            val hex = contact.publicKey.toByteArray()
                                .joinToString("") { "%02x".format(it) }
                            backStack.add(ReplyRoute(hex))
                        },
                    )
                }

                entry<ReplyRoute> { route ->
                    QuickReplyScreen(
                        pubkeyHex = route.pubkeyHex,
                        viewModel = viewModel,
                        onSent = {
                            // Pop back to status
                            while (backStack.size > 1) backStack.removeLastOrNull()
                        },
                    )
                }
            },
        )
    }
}
