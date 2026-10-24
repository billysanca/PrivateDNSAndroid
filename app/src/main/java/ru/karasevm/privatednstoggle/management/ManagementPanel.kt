package ru.karasevm.privatednstoggle.management

import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.util.PrivateDNSUtils

class ManagementPanel(private val activity: AppCompatActivity) {
    val view = ScrollView(activity)
    private val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24,16,24,24) }
    private val store = ManagementStore(activity)
    private val vault = ApiKeyVault(activity)
    private var job: Job? = null
    private var selected: String? = null
    init { view.addView(column) }
    private fun text(value: String) { column.addView(TextView(activity).apply { text = value; setPadding(0,12,0,12) }) }
    private fun button(value: String, action: () -> Unit) { column.addView(Button(activity).apply { text = value; setOnClickListener { action() } }) }
    fun update(servers: List<DnsServer>) {
        job?.cancel()
        column.removeAllViews()
        text("Gerenciamento · não altera o DNS ativo")
        if (servers.isEmpty()) { text("Cadastre um hostname na aba DNS."); return }
        val spinner = Spinner(activity)
        spinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, servers.map { "${it.label} · ${it.server}" })
        column.addView(spinner)
        val current = selected ?: PrivateDNSUtils.getPrivateProvider(activity.contentResolver)
        spinner.setSelection(servers.indexOfFirst { it.server == current }.coerceAtLeast(0))
        val detail = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(detail)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                job?.cancel()
                val host = servers[position].server
                selected = host
                detail.removeAllViews()
                fun label(s: String) { detail.addView(TextView(activity).apply { text = s; setPadding(0,12,0,12) }) }
                fun action(s: String, f: () -> Unit) { detail.addView(Button(activity).apply { text = s; setOnClickListener { f() } }) }
                val config = store.get(host)
                label("Modo: ${config.mode} · ${config.provider}\n${config.url}")
                action("Editar gerenciamento") { edit(host) { update(servers) } }
                val output = TextView(activity).apply { setPadding(0,16,0,16) }
                action("Gerenciar") {
                    when (config.mode) {
                        ManagementMode.NONE -> label("Selecione um modo em Editar gerenciamento.")
                        ManagementMode.WEB -> openWeb(config)
                        ManagementMode.API -> {
                            if (job?.isActive == true) return@action
                            output.text = "Consultando…"
                            job = activity.lifecycleScope.launch {
                                try {
                                    val sections = withContext(Dispatchers.IO) {
                                        val key = vault.get("$host:${config.provider}") ?: error("Cadastre uma chave de API.")
                                        Providers.get(config.provider).read(config.profile, key)
                                    }
                                    output.text = sections.mapIndexed { index, section -> section.title + "\n" + ReadPresentation.format(config.provider, index, section.json) }.joinToString("\n\n")
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { output.text = if (e is ApiFailure) e.message!! else "Não foi possível consultar. Verifique conexão, perfil e chave de API." }
                            }
                        }
                    }
                }
                detail.addView(output)
                action("Esquecer chave de API") { job?.cancel(); output.text = ""; vault.remove("$host:${config.provider}"); label("Chave local removida. Revogue no provedor se necessário.") }
                action("Encerrar sessões WebView") {
                    AlertDialog.Builder(activity).setMessage("Apagar cookies e armazenamento de todos os painéis WebView deste aplicativo?")
                        .setPositiveButton("Apagar") { _, _ ->
                            android.webkit.CookieManager.getInstance().removeAllCookies {
                                android.webkit.CookieManager.getInstance().flush()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                label("Sessões WebView removidas. Sessões Custom Tabs são gerenciadas pelo navegador.")
                            }
                        }.setNegativeButton("Cancelar", null).show()
                }
            }
        }
    }
    fun suspendReading() { job?.cancel(); column.removeAllViews() }
    private fun openWeb(c: ManagementConfig) {
        if (!ManagementPolicy.validUrl(c.url)) return
        runCatching {
            if (c.webMode == WebMode.CUSTOM_TAB) CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, c.url.toUri())
            else activity.startActivity(Intent(activity, ManagementWebActivity::class.java).putExtra("url", c.url))
        }.onFailure { Toast.makeText(activity, "Nenhum navegador disponível", Toast.LENGTH_LONG).show() }
    }
    private fun edit(host: String, done: () -> Unit) {
        val c = store.get(host)
        val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        fun choices(labels: List<String>, index: Int): Spinner = Spinner(activity).also {
            it.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
            it.setSelection(index); form.addView(it)
        }
        val mode = choices(listOf("Sem gerenciamento", "Painel web", "API nativa (leitura)"), c.mode.ordinal)
        val url = EditText(activity).apply { hint = "URL HTTPS do painel"; setText(c.url); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        form.addView(url)
        val web = choices(listOf("Navegador · Custom Tabs (padrão)", "WebView incorporado"), c.webMode.ordinal)
        val provider = choices(listOf("NextDNS", "AdGuard DNS"), if(c.provider == "adguard") 1 else 0)
        val profile = EditText(activity).apply { hint = "ID do perfil NextDNS ou servidor AdGuard"; setText(c.profile) }
        form.addView(profile)
        form.addView(TextView(activity).apply { text = "Use uma chave criada no site oficial. Deixe vazio para manter a chave atual. Nunca informe a senha do painel." })
        val key = EditText(activity).apply { hint = "Chave de API"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
        form.addView(key)
        val dialog = AlertDialog.Builder(activity).setTitle(host).setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = ManagementConfig(ManagementMode.entries[mode.selectedItemPosition], url.text.toString().trim(),
                    WebMode.entries[web.selectedItemPosition], if(provider.selectedItemPosition == 1) "adguard" else "nextdns", profile.text.toString().trim())
                try {
                    require(updated.url.isEmpty() || ManagementPolicy.validUrl(updated.url))
                    require(updated.mode != ManagementMode.WEB || ManagementPolicy.validUrl(updated.url))
                    require(updated.mode != ManagementMode.API || ManagementPolicy.validProfile(updated.profile))
                    if (key.text.isNotBlank()) vault.put("$host:${updated.provider}", key.text.toString().trim())
                    store.save(host, updated)
                    key.text.clear(); dialog.dismiss(); done()
                } catch (_: Exception) { Toast.makeText(activity, "Verifique a URL HTTPS, o ID e a chave.", Toast.LENGTH_LONG).show() }
            }
        }
        dialog.setOnDismissListener { key.text.clear() }
        dialog.show()
    }
}
