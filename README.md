# Accessibility Report

_Auto-generated from `compose-preview/a11y/pr`. 61 preview(s) across 2 module(s) · 36 error(s) · 17 warning(s) · 13 info._

Browse inline; image URLs are pinned to the commit SHA on the baseline branch so links keep resolving after merge.

## app

### `BleDeviceListEmptyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/BleDeviceListEmptyPreview_BleDeviceList_empty.a11y.png" width="400" />

_No findings._

### `BleDeviceListFewPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/BleDeviceListFewPreview_BleDeviceList_2_devices.a11y.png" width="400" />

_No findings._

### `BleDeviceListManyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/BleDeviceListManyPreview_BleDeviceList_10_devices_scrolls.a11y.png" width="400" />

_No findings._

### `BlePermissionPanelDeniedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/BlePermissionPanelDeniedPreview_BlePermissionPanel_denied.a11y.png" width="400" />

_No findings._

### `BlePermissionPanelFirstPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/BlePermissionPanelFirstPreview_BlePermissionPanel_first_request.a11y.png" width="400" />

_No findings._

### `ContactListEmptyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ContactListEmptyPreview_ContactList_empty.a11y.png" width="400" />

_No findings._

### `ContactListFewPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ContactListFewPreview_ContactList_2_items.a11y.png" width="400" />

_No findings._

### `ContactListManyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ContactListManyPreview_ContactList_10_items_scrolls.a11y.png" width="400" />

_No findings._

### `ContactRowVariantsPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ContactRowVariantsPreview_ContactRow_variants.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.view.View | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.view.View | This item may not have a label readable by screen readers. |
| 3 | ERROR | TouchTargetSizeCheck | android.view.View | This item's height is 16dp. Consider making the height of this touch target 48dp or larger. |

### `DeviceBodyDarkPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyDarkPreview_Device_dark.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Joined" is identical to that of 1 other item(s). |

### `DeviceBodyLoadingPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyLoadingPreview_Device_loading.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | INFO | DuplicateSpeakableTextCheck | android.widget.ProgressBar | This non-clickable item's speakable text: "In progress" is identical to that of 1 other item(s). |

### `DeviceBodyLowBatteryPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyLowBatteryPreview_Device_low_battery.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |

### `DeviceBodyManyContactsPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyManyContactsPreview_Device_many_contacts_scrolls.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "base-station" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Joined" is identical to that of 1 other item(s). |

### `DeviceBodyNoContactsPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyNoContactsPreview_Device_contacts_loading.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |

### `DeviceBodyPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceBodyPreview_Device_populated.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Favourited" is identical to that of 1 other item(s). |
| 6 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Joined" is identical to that of 1 other item(s). |

### `DeviceInfoWidgetEmptyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceInfoWidgetEmptyPreview_DeviceInfo_no_data.png" width="400" />

_No findings._

### `DeviceInfoWidgetPopulatedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceInfoWidgetPopulatedPreview_DeviceInfo_populated.png" width="400" />

_No findings._

### `DeviceInfoWidgetStalePreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceInfoWidgetStalePreview_DeviceInfo_stale.png" width="400" />

_No findings._

### `DeviceStatusConnectingPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceStatusConnectingPreview_Device_status_connecting.a11y.png" width="400" />

_No findings._

### `DeviceStatusFailedPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceStatusFailedPreview_Device_status_failed.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "Connection failed" is identical to that of 1 other item(s). |

### `DeviceSummaryCardLoadingPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceSummaryCardLoadingPreview_DeviceSummaryCard_loading.a11y.png" width="400" />

_No findings._

### `DeviceSummaryCardPopulatedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/DeviceSummaryCardPopulatedPreview_DeviceSummaryCard_populated.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |

### `PlayStoreFeature` · `spec:width=1024dp,height=500dp,dpi=160`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStoreFeature_Play_Store_feature_graphic_1024x500.a11y.png" width="400" />

_No findings._

### `PlayStoreIcon` · `spec:width=512dp,height=512dp,dpi=160`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStoreIcon_Play_Store_icon_512x512.png" width="400" />

_No findings._

### `PlayStorePhoneHomeDark` · `pixel_2`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStorePhoneHomeDark_Play_Store_phone_home_dark.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Favourited" is identical to that of 1 other item(s). |

### `PlayStorePhoneHomeLight` · `pixel_2`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStorePhoneHomeLight_Play_Store_phone_home_light.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Favourited" is identical to that of 1 other item(s). |

### `PlayStorePhoneScannerBle` · `pixel_2`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStorePhoneScannerBle_Play_Store_phone_scanner_BLE.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 3 other item(s). |

### `PlayStorePhoneScannerSaved` · `pixel_2`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStorePhoneScannerSaved_Play_Store_phone_scanner_saved.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 1 other item(s). |

### `PlayStoreTabletSevenHome` · `spec:width=600dp,height=960dp,dpi=320`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStoreTabletSevenHome_Play_Store_7_inch_tablet_home.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Favourited" is identical to that of 1 other item(s). |
| 6 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Joined" is identical to that of 1 other item(s). |

### `PlayStoreTabletTenHome` · `spec:width=800dp,height=1280dp,dpi=320`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/PlayStoreTabletTenHome_Play_Store_10_inch_tablet_home.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 2 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 3 | ERROR | SpeakableTextPresentCheck | android.widget.TextView | This item may not have a label readable by screen readers. |
| 4 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "node-peak" is identical to that of 1 other item(s). |
| 5 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Favourited" is identical to that of 1 other item(s). |
| 6 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Selected, Joined" is identical to that of 1 other item(s). |

### `SavedDevicesEmptyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/SavedDevicesEmptyPreview_Saved_devices_empty.png" width="400" />

_No findings._

### `SavedDevicesPopulatedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/SavedDevicesPopulatedPreview_Saved_devices_populated.a11y.png" width="400" />

_No findings._

### `ScannerBleDarkPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerBleDarkPreview_Scanner_Saved_populated_dark.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 1 other item(s). |
| 2 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "192.168.1.10:5000" is identical to that of 1 other item(s). |

### `ScannerBleEmptyPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerBleEmptyPreview_Scanner_BLE_scanning_0_devices.a11y.png" width="400" />

_No findings._

### `ScannerBleFewPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerBleFewPreview_Scanner_BLE_scanning_2_devices.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 1 other item(s). |

### `ScannerBleManyPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerBleManyPreview_Scanner_BLE_scanning_10_devices_scrolls.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 7 other item(s). |

### `ScannerBlePermissionPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerBlePermissionPreview_Scanner_BLE_permission_needed.a11y.png" width="400" />

_No findings._

### `ScannerSavedEmptyPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerSavedEmptyPreview_Scanner_Saved_empty.a11y.png" width="400" />

_No findings._

### `ScannerSavedPopulatedPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerSavedPopulatedPreview_Scanner_Saved_populated.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 1 other item(s). |
| 2 | INFO | DuplicateSpeakableTextCheck | android.widget.TextView | This non-clickable item's speakable text: "192.168.1.10:5000" is identical to that of 1 other item(s). |

### `ScannerTcpPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerTcpPreview_Scanner_TCP_idle.a11y.png" width="400" />

_No findings._

### `ScannerUsbEmptyPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerUsbEmptyPreview_Scanner_USB_no_ports.a11y.png" width="400" />

_No findings._

### `ScannerUsbFewPreview` · `pixel_7`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ScannerUsbFewPreview_Scanner_USB_2_ports.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | WARNING | DuplicateSpeakableTextCheck | android.view.View | This clickable item's speakable text: "Connect" is identical to that of 1 other item(s). |

### `TcpConnectPanelBusyPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/TcpConnectPanelBusyPreview_TcpConnectPanel_busy.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | TouchTargetSizeCheck | android.widget.EditText | This item's height is 10dp. Consider making the height of this touch target 48dp or larger. |

### `TcpConnectPanelIdlePreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/TcpConnectPanelIdlePreview_TcpConnectPanel_idle.a11y.png" width="400" />

| # | Level | Rule | Element | Message |
|--:|---|---|---|---|
| 1 | ERROR | TouchTargetSizeCheck | android.widget.EditText | This item's height is 10dp. Consider making the height of this touch target 48dp or larger. |

### `ThemePickerDynamicDarkPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ThemePickerDynamicDarkPreview_ThemePicker_Dynamic_Dark.png" width="400" />

_No findings._

### `ThemePickerMeshcoreSystemPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/app/ThemePickerMeshcoreSystemPreview_ThemePicker_MeshCore_System.png" width="400" />

_No findings._

## wear

### `ContactsBodyEmptyPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/ContactsBodyEmptyPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `ContactsBodyFewPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/ContactsBodyFewPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `InteractiveToggleChipPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/InteractiveToggleChipPreview_Interactive_Toggle_Chip.a11y.png" width="400" />

_No findings._

### `QuickReplyBodyPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/QuickReplyBodyPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyConnectedLowBatteryPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyConnectedLowBatteryPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyConnectedPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyConnectedPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyErrorPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyErrorPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyLoadingPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyLoadingPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyPhoneDisconnectedPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyPhoneDisconnectedPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusBodyRadioDisconnectedPreview` · `wearos_large_round`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusBodyRadioDisconnectedPreview_Devices_Large_Round.a11y.png" width="400" />

_No findings._

### `StatusWidgetConnectedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusWidgetConnectedPreview_Status_connected.png" width="400" />

_No findings._

### `StatusWidgetDisconnectedPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusWidgetDisconnectedPreview_Status_disconnected.png" width="400" />

_No findings._

### `StatusWidgetLowBatteryPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/StatusWidgetLowBatteryPreview_Status_low_battery.png" width="400" />

_No findings._

### `WearComponentCatalogPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/WearComponentCatalogPreview_Component_Catalog.png" width="400" />

_No findings._

### `WearComponentGridPreview`

<img src="https://raw.githubusercontent.com/yschimke/meshcore-mobile/compose-preview/a11y/pr/renders/wear/WearComponentGridPreview_Component_Grid.png" width="400" />

_No findings._
