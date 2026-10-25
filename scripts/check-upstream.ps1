$ErrorActionPreference = 'Stop'
$base = '189afad221b63b06e8f1f9c429aac91ef4f3bb0e'
$paths = @(
 'app/src/main/java/ru/karasevm/privatednstoggle/data',
 'app/src/main/java/ru/karasevm/privatednstoggle/model',
 'app/src/main/java/ru/karasevm/privatednstoggle/service',
 'app/src/main/java/ru/karasevm/privatednstoggle/util',
 'app/src/main/java/ru/karasevm/privatednstoggle/ShortcutHandlerActivity.kt',
 'app/src/main/java/ru/karasevm/privatednstoggle/ui/SettingsDialogActivity.kt',
 'app/src/main/java/ru/karasevm/privatednstoggle/ui/AddServerDialogFragment.kt'
)
& git diff --exit-code $base -- $paths
if ($LASTEXITCODE -ne 0) { throw 'Upstream core changed; review regression impact.' }
'PASS: DNS switching, multiple-hostname database, backup, Quick Settings and Shizuku sources match upstream.'

'UI adapter intentionally revised for selection styling and tap/long-press; covered by instrumentation tests.'