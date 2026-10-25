package ru.karasevm.privatednstoggle.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import ru.karasevm.privatednstoggle.PrivateDNSApp
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.data.DnsServerViewModel
import ru.karasevm.privatednstoggle.data.DnsServerViewModelFactory
import ru.karasevm.privatednstoggle.databinding.ActivityMainBinding
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.util.BackupUtils
import ru.karasevm.privatednstoggle.util.PreferenceHelper
import ru.karasevm.privatednstoggle.util.PreferenceHelper.dns_servers
import ru.karasevm.privatednstoggle.util.ShizukuUtil.grantPermissionWithShizuku


class MainActivity : AppCompatActivity(), AddServerDialogFragment.NoticeDialogListener,
    DeleteServerDialogFragment.NoticeDialogListener, Shizuku.OnRequestPermissionResultListener {

    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var adapter: ServerListRecyclerAdapter
    private lateinit var managementPanel: ru.karasevm.privatednstoggle.management.ManagementPanel
    private var managementVisible = false
    private val selectionPrefs by lazy { getSharedPreferences("management_selection", MODE_PRIVATE) }
    private fun chosenManagementHost(servers: List<DnsServer>): String? {
        val chosenId = if(selectionPrefs.contains("id")) selectionPrefs.getInt("id", 0) else null
        val index = ru.karasevm.privatednstoggle.management.ManagementRouting.chosenIndex(
            servers.map { it.id }, servers.map { it.server }, chosenId,
            ru.karasevm.privatednstoggle.util.PrivateDNSUtils.getPrivateMode(contentResolver),
            ru.karasevm.privatednstoggle.util.PrivateDNSUtils.getPrivateProvider(contentResolver))
        return servers.getOrNull(index)?.server
    }
    private fun updateProfileSubtitle() {
        binding.topAppBar.subtitle = chosenManagementHost(dnsServerViewModel.allServers.value.orEmpty())
            ?.let { "Perfil selecionado · $it" } ?: "Escolha um perfil na aba DNS"
    }
    private var selectedMainTab = 0
    private val mainTabIds = intArrayOf(R.id.nav_dns, R.id.nav_stats, R.id.nav_queries, R.id.nav_settings)
    private lateinit var clipboard: ClipboardManager
    private val dnsServerViewModel: DnsServerViewModel by viewModels { DnsServerViewModelFactory((application as PrivateDNSApp).repository) }

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback =
            object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
                override fun isLongPressDragEnabled() = false
                var dragFrom = -1
                var dragTo = -1

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    if (dragFrom == viewHolder.bindingAdapterPosition && dragTo == target.bindingAdapterPosition) {
                        return true
                    }
                    // store the drag position
                    if (dragFrom == -1) dragFrom = viewHolder.bindingAdapterPosition
                    dragTo = target.bindingAdapterPosition
                    adapter.onItemMove(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?, actionState: Int
                ) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.apply {
                            // Example: Elevate the view
                            elevation = 8f
                            alpha = 0.5f
                            setBackgroundColor(Color.GRAY)
                        }
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.apply {
                        // Reset the appearance
                        elevation = 0f
                        alpha = 1.0f
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                    // commit the change to the db
                    dnsServerViewModel.move(
                        dragFrom,
                        dragTo,
                        (viewHolder as ServerListRecyclerAdapter.DnsServerViewHolder).id
                    )
                    dragTo = -1
                    dragFrom = -1
                }
            }
        ItemTouchHelper(simpleItemTouchCallback)
    }

    private fun importSettings(json: String) {
        runCatching {
            val data: BackupUtils.Backup = Json.decodeFromString<BackupUtils.Backup>(json)
            BackupUtils.import(data, dnsServerViewModel, sharedPrefs)
        }.onSuccess {
            Toast.makeText(
                this, getString(R.string.import_success), Toast.LENGTH_SHORT
            ).show()
        }.onFailure { exception ->
            runCatching {
                Log.e("IMPORT", "Malformed json, falling back to legacy", exception)
                val data = Json.decodeFromString<BackupUtils.LegacyBackup>(json)
                BackupUtils.importLegacy(data, dnsServerViewModel, sharedPrefs)
            }.onSuccess {
                Toast.makeText(
                    this, getString(R.string.import_success), Toast.LENGTH_SHORT
                ).show()
            }.onFailure { exception ->
                Log.e("IMPORT", "Import failed", exception)
                Toast.makeText(
                    this, getString(R.string.import_failure), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     *  Migrate the SharedPreferences server list to Room
     */
    private fun migrateServerList() {
        dnsServerViewModel.viewModelScope.launch {
            if (sharedPrefs.dns_servers.isNotEmpty() && sharedPrefs.dns_servers[0] != "") {
                Log.i(
                    "migrate",
                    "existing sharedPrefs list: ${sharedPrefs.dns_servers} ${sharedPrefs.dns_servers.size}"
                )
                sharedPrefs.dns_servers.forEach { server ->
                    val parts = server.split(" : ").toMutableList()
                    if (parts.size != 2) parts.add(0, "")
                    Log.i("migrate", "migrating: $server -> $parts")
                    dnsServerViewModel.insert(DnsServer(0, parts[1], parts[0]))
                }
                sharedPrefs.dns_servers = emptyList<String>().toMutableList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(this::onRequestPermissionResult)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        managementPanel = ru.karasevm.privatednstoggle.management.ManagementPanel(this, chosenHost = { chosenManagementHost(dnsServerViewModel.allServers.value.orEmpty()) }, settingsAction = { id -> binding.topAppBar.menu.performIdentifierAction(id, 0) }) { destination ->
            binding.bottomNavigation.selectedItemId = mainTabIds[destination.coerceIn(0, 3)]
        }
        val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, 0).apply {
            topToBottom = R.id.topAppBarLayout
            bottomToTop = R.id.navigation_card
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        managementPanel.view.visibility = View.GONE
        binding.root.addView(managementPanel.view, params)
        binding.navigationCard.bringToFront()
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showMainTab(mainTabIds.indexOf(item.itemId).coerceAtLeast(0))
            true
        }
        selectedMainTab = savedInstanceState?.getInt("main_tab", 0)?.coerceIn(0, 3) ?: 0
        binding.bottomNavigation.selectedItemId = mainTabIds[selectedMainTab]
        showMainTab(selectedMainTab)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedMainTab != 0) binding.bottomNavigation.selectedItemId = R.id.nav_dns
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        linearLayoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = linearLayoutManager

        sharedPrefs = PreferenceHelper.defaultPreference(this)
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        migrateServerList()

        adapter = ServerListRecyclerAdapter(true)
        binding.recyclerView.adapter = adapter

        dnsServerViewModel.allServers.observe(this) { servers ->
            adapter.submitList(servers)
            updateProfileSubtitle()
            if (managementVisible) managementPanel.update(servers, selectedMainTab)
            if (servers.isEmpty() && !managementVisible) {
                binding.emptyView.visibility = View.VISIBLE
                binding.emptyViewHint.visibility = View.VISIBLE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.emptyViewHint.visibility = View.GONE
            }
        }
        adapter.onItemClick = { id ->
            selectionPrefs.edit().putInt("id", id).apply()
            updateProfileSubtitle()
        }
        adapter.onItemLongClick = { id ->
            dnsServerViewModel.viewModelScope.launch {
                val server = dnsServerViewModel.getById(id)
                if (server != null) AddServerDialogFragment(server).show(supportFragmentManager, "edit_server")
            }
        }
        adapter.onDragStart = { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }
        binding.floatingActionButton.setOnClickListener {
            val newFragment = AddServerDialogFragment(null)
            newFragment.show(supportFragmentManager, "add_server")
        }
        binding.recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.privacy_policy -> {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Privacidade · Nexo DNS")
                        .setMessage("As consultas nativas enviam sua chave somente à API oficial do provedor escolhido, por HTTPS. As chaves ficam cifradas com Android Keystore e não entram no backup. Os resultados não são gravados em disco. Painéis web usam as políticas e sessões do próprio site; o aplicativo não captura senhas. Custom Tabs usa os dados do navegador; WebView mantém cookies locais que podem ser apagados em Gerenciamento. Não há analytics nem troca automática de DNS.")
                        .setPositiveButton(android.R.string.ok, null).show()
                    true
                }
                R.id.export_settings_clipboard -> {
                    dnsServerViewModel.viewModelScope.launch {
                        val data = BackupUtils.export(dnsServerViewModel, sharedPrefs)
                        val jsonData = Json.encodeToString(data)
                        clipboard.setPrimaryClip(ClipData.newPlainText("", jsonData))
                        // Only show a toast for Android 12 and lower.
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast.makeText(
                            applicationContext, getString(R.string.copy_success), Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                R.id.export_settings_share -> {
                    val activityContext = this
                    dnsServerViewModel.viewModelScope.launch {
                        val data = BackupUtils.export(dnsServerViewModel, sharedPrefs)
                        val jsonData = Json.encodeToString(data)
                        ShareCompat.IntentBuilder(activityContext).setText(jsonData)
                            .setType("text/plain")
                            .startChooser()
                    }
                    true
                }

                R.id.export_settings_file -> {

                    dnsServerViewModel.viewModelScope.launch {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, "private-dns-export")
                        }
                        saveResultLauncher.launch(intent)
                    }
                    true
                }

                R.id.import_settings_file -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                    }
                    importResultLauncher.launch(intent)
                    true
                }

                R.id.import_settings_clipboard -> {
                    val clipData = clipboard.primaryClip?.getItemAt(0)
                    val textData = clipData?.text

                    if (textData != null) {
                        importSettings(textData.toString())
                    }
                    true
                }

                R.id.options -> {
                    val newFragment = OptionsDialogFragment()
                    newFragment.show(supportFragmentManager, "options")
                    true
                }

                R.id.open_source_licenses -> {
                    // start license activity
                    val intent = Intent(this, AboutLibsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> true
            }
        }
    }

    private var saveResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.also { uri ->
                    val jsonData =
                        Json.encodeToString(BackupUtils.export(dnsServerViewModel, sharedPrefs))
                    val contentResolver = applicationContext.contentResolver
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonData.toByteArray())
                        }
                    }.onFailure { exception ->
                        Log.e("EXPORT", "Export failed", exception)
                        Toast.makeText(
                            this, getString(R.string.export_failure), Toast.LENGTH_SHORT
                        ).show()
                    }.onSuccess {
                        Toast.makeText(
                            this, getString(R.string.export_success), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    private var importResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.also { uri ->
                    val contentResolver = applicationContext.contentResolver
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonData = inputStream.bufferedReader().use { it.readText() }
                        importSettings(jsonData)
                    }
                }
            }
        }

    private fun showPermissionDialog() {

        var permissionDialog: AlertDialog? = null
        val dialogView = ComposeView(this).apply {
            setContent {
                val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemInDarkTheme())
                        dynamicDarkColorScheme(applicationContext)
                    else
                        dynamicLightColorScheme(applicationContext)
                } else {
                    if (isSystemInDarkTheme()) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    }
                }

                MaterialTheme(colorScheme = colorScheme) {
                    PermissionExplanationDialog(
                        onDismiss = {
                            permissionDialog?.dismiss()
                        },
                        onConfirm = {
                            permissionDialog?.dismiss()
                        }
                    )
                }
            }
        }

        permissionDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()
            .apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        if(menu != null) for(i in 0 until menu.size()) menu.getItem(i).isVisible = false
        return true
    }

    private fun showMainTab(index: Int) {
        updateProfileSubtitle()
        selectedMainTab = index
        managementVisible = index != 0
        if (managementVisible) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); managementPanel.suspendReading() }
        managementPanel.view.visibility = if (managementVisible) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (managementVisible) View.GONE else View.VISIBLE
        binding.floatingActionButton.visibility = if (managementVisible) View.GONE else View.VISIBLE
        binding.emptyView.visibility = if (!managementVisible && dnsServerViewModel.allServers.value.orEmpty().isEmpty()) View.VISIBLE else View.GONE
        binding.emptyViewHint.visibility = binding.emptyView.visibility
        binding.topAppBar.title = when(index) { 1 -> "Estatísticas"; 2 -> "Consultas"; 3 -> "Configurações"; else -> getString(R.string.app_name) }
        if (managementVisible) managementPanel.update(dnsServerViewModel.allServers.value.orEmpty(), index, userNavigation = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("main_tab", selectedMainTab)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        if (::managementPanel.isInitialized) managementPanel.suspendReading()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::managementPanel.isInitialized) updateProfileSubtitle()
        if (::managementPanel.isInitialized && managementVisible) managementPanel.update(dnsServerViewModel.allServers.value.orEmpty(), selectedMainTab)
        if (selectedMainTab != 0) return
        // Check if WRITE_SECURE_SETTINGS is granted
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            // Check if Shizuku is available
            if (Shizuku.pingBinder()) {
                // check if permission is granted already
                val isGranted = if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                    checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
                // request permission if not granted
                if (!isGranted && !Shizuku.shouldShowRequestPermissionRationale()) {
                    if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                        requestPermissions(arrayOf(ShizukuProvider.PERMISSION), 1)
                    } else {
                        Shizuku.requestPermission(1)
                    }
                } else {
                    grantPermission()
                }
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                    showPermissionDialog()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            // Gets the ID of the "paste" menu item.
            val pasteItem = binding.topAppBar.menu.findItem(R.id.import_settings_clipboard)

            // If the clipboard doesn't contain data, disable the paste menu item.
            // If it does contain data, decide whether you can handle the data.
            pasteItem.isEnabled = when {
                !clipboard.hasPrimaryClip() -> false
                !(clipboard.primaryClipDescription?.hasMimeType(MIMETYPE_TEXT_PLAIN))!! -> false
                else -> true

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this::onRequestPermissionResult)
    }

    /**
     *  Show the dialog for deleting the server
     *  @param id The server id
     */
    override fun onDeleteItemClicked(id: Int) {
        val newFragment = DeleteServerDialogFragment(id)
        newFragment.show(supportFragmentManager, "delete_server")
    }

    /**
     *  Callback for adding the server
     *  @param label The label
     *  @param server The server
     */
    override fun onAddDialogPositiveClick(label: String?, server: String) {
        if (server.isEmpty()) {
            Toast.makeText(this, R.string.server_length_error, Toast.LENGTH_SHORT).show()
            return
        }

        if (label.isNullOrEmpty()) {
            dnsServerViewModel.insert(DnsServer(0, server))
        } else {
            dnsServerViewModel.insert(DnsServer(0, server, label))
        }
    }

    /**
     *  Callback for deleting the server
     *  @param id The server id
     */
    override fun onDeleteDialogPositiveClick(id: Int) {
        dnsServerViewModel.delete(id)
    }

    /**
     *  Callback for updating the server
     *  @param label New label
     *  @param server New server address
     *  @param id The server id
     */
    override fun onUpdateDialogPositiveClick(
        id: Int,
        server: String,
        label: String?,
        enabled: Boolean
    ) {
        if (server.isEmpty()) {
            Toast.makeText(this, R.string.server_length_error, Toast.LENGTH_SHORT).show()
            return
        }
        dnsServerViewModel.update(id, server, label, null, enabled)
    }

    private fun grantPermission() {
        if (grantPermissionWithShizuku(this)) {
            Toast.makeText(
                this, R.string.shizuku_success_toast, Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this, R.string.shizuku_failure_toast, Toast.LENGTH_SHORT
            ).show()
            val browserIntent = Intent(
                Intent.ACTION_VIEW, "https://karasevm.github.io/PrivateDNSAndroid/".toUri()
            )
            startActivity(browserIntent)
            finish()
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        val isGranted = grantResult == PackageManager.PERMISSION_GRANTED

        if (!isGranted && checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            val browserIntent = Intent(
                Intent.ACTION_VIEW, "https://karasevm.github.io/PrivateDNSAndroid/".toUri()
            )
            startActivity(browserIntent)
            finish()
        }
    }
}
