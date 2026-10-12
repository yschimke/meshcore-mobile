package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*

/** Current compiled signatures, with explicit native-only platform claims. */
fun discoverCatalogComponents(): ComponentRecordFile =
  TypedAdapterDiscovery.discover(
      ":meshcore-components",
      "desktop",
      listOf(
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.ChatComponentsKt",
          "ChatInput",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.ChatComponentsKt",
          "ChatBubble",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.ChatComponentsKt",
          "ChatMessageList",
        ),
        TypedAdapterDiscovery.Callable("ee.schimke.meshcore.components.ui.ChatBodyKt", "ChatBody"),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ContactListEmpty",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "DeviceSummaryCard",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "RadioLine",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "BatterySection",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ContactRow",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ContactList",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ChannelRow",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ChannelList",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.theme.MeshcoreThemeKt",
          "MeshcoreTheme",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.TcpConnectPanelKt",
          "TcpConnectPanel",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceSettingsBodyKt",
          "DeviceSettingsBody",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceBodyKt",
          "DeviceBody",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.app.ui.theme.ThemePickerKt",
          "ThemePickerDialog",
        ),
        TypedAdapterDiscovery.Callable("ee.schimke.meshcore.app.ui.LoginDialogKt", "LoginDialog"),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.app.ui.DeviceStatusViewsKt",
          "DeviceStatusView",
        ),
      ),
    )
    .let { records ->
      records
        .newBuilder()
        .also { b ->
          b.components =
            records.components.map { c ->
              c.newBuilder()
                .also {
                  it.builder =
                    BuilderPolicy.Builder().also { policy -> policy.nativeOnly = true }.build()
                }
                .build()
            }
        }
        .build()
    }
