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
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "ContactListEmpty",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.DeviceCardsKt",
          "DeviceSummaryCard",
        ),
        TypedAdapterDiscovery.Callable(
          "ee.schimke.meshcore.components.ui.theme.MeshcoreThemeKt",
          "MeshcoreTheme",
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
