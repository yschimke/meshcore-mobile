package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ContactListEmpty

/** Catalog-owned authoring model; real component calls below check the library API. */
data class ChatInputProps(
  val value: String,
  val enabled: Boolean,
  val placeholder: String,
  val onValueChange: (String) -> Unit,
  val onSend: () -> Unit,
)

class ChatInputAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChatInputProps>("meshcore-mobile/chat-input", record) {
  val value = property(ChatInputProps::value, AdapterValueCodecs.String, "Hello mesh")
  val enabled = property(ChatInputProps::enabled, AdapterValueCodecs.Boolean, true)
  val placeholder = property(ChatInputProps::placeholder, AdapterValueCodecs.String, "Message")
  val change = stateChange(ChatInputProps::onValueChange, value)
  val send = event(ChatInputProps::onSend)
}

class MeshcoreCatalog(records: ComponentRecordFile) {
  val input = ChatInputAdapter(records.components.single { it.symbol.name == "ChatInput" })
  val emptyContacts =
    TypedComponentAdapter<Unit>(
      "meshcore-mobile/empty-contacts",
      records.components.single { it.symbol.name == "ContactListEmpty" },
    )
  val definitions = listOf(input, emptyContacts)

  fun generate(records: ComponentRecordFile): GeneratedAdapterCatalog =
    TypedAdapterCatalog.generate(
      records,
      UiBuilderCatalogs.CoverSheet("meshcore-mobile", "MeshCore live components"),
      UiBuilderPolicyFile.Builder(UI_BUILDER_POLICY_SCHEMA, "mobile").build(),
      definitions,
    )

  fun registry(): CanvasAdapterRegistry = canvasAdapterRegistry {
    register(input) {
      ChatInput(
        value = value(input.value),
        onValueChange = callback(input.change),
        onSend = callback(input.send),
        enabled = value(input.enabled),
        placeholder = value(input.placeholder),
        modifier = modifier,
      )
    }
    register(emptyContacts) { ContactListEmpty(modifier = modifier) }
  }
}
