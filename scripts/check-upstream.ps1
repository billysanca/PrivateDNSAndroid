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

# The adapter permits exactly the requested tap/long-press callback additions.
$adapterPath = 'app/src/main/java/ru/karasevm/privatednstoggle/ui/ServerListRecyclerAdapter.kt'
$original = ((& git show "${base}:$adapterPath") -join "`n").TrimEnd()
$current = [IO.File]::ReadAllText((Join-Path (Get-Location) $adapterPath)).Replace("`r`n", "`n")
$current = $current.Replace("    var onItemLongClick: ((Int) -> Unit)? = null`n", '')
$current = $current.Replace("            view.setOnLongClickListener { onItemLongClick?.invoke(id); true }`n", '')
if($current.TrimEnd() -ne $original) { throw 'Unexpected adapter change beyond gesture callbacks.' }
'PASS: adapter differs only by the authorized long-press callback; list/order/drag behavior unchanged.'