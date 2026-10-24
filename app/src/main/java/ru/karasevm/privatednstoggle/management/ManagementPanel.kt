package ru.karasevm.privatednstoggle.management

import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.*
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.util.PrivateDNSUtils

/** Material 3 Views. Web settings remain independent of native provider setup and discovery. */
class ManagementPanel(private val activity: AppCompatActivity) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val column = column()
    private val store = ManagementStore(activity)
    private val vault = ApiKeyVault(activity)
    private var job: Job? = null
    private var selected: String? = null
    init { view.addView(column) }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }
    private fun text(parent: LinearLayout, value: String, title: Boolean = false) = MaterialTextView(activity).also {
        it.text = value
        it.setTextAppearance(if (title) com.google.android.material.R.style.TextAppearance_Material3_TitleLarge else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        it.setPadding(0, dp(8), 0, dp(8))
        parent.addView(it)
    }
    private fun button(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit) =
        MaterialButton(activity, null, if (primary) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.materialButtonOutlinedStyle).also {
            it.text = value
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            it.setOnClickListener { action() }
            parent.addView(it)
        }
    private fun card(parent: LinearLayout): LinearLayout {
        val content = column()
        parent.addView(MaterialCardView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            addView(content)
        })
        return content
    }
    private class Choice(layoutValue: TextInputLayout, inputValue: MaterialAutoCompleteTextView, initial: Int) {
        val layout = layoutValue
        val input = inputValue
        var index = initial
    }
    private fun choice(parent: LinearLayout, label: String, labels: List<String>, initial: Int, changed: (Int) -> Unit = {}): Choice {
        val layout = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = label
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        val input = MaterialAutoCompleteTextView(layout.context).apply {
            inputType = InputType.TYPE_NULL
            setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, labels))
            setText(labels[initial], false)
        }
        layout.addView(input)
        parent.addView(layout)
        return Choice(layout, input, initial).also { result ->
            input.setOnItemClickListener { _, _, position, _ -> result.index = position; changed(position) }
        }
    }
    private fun field(parent: LinearLayout, hint: String, value: String = "", helper: String? = null): Pair<TextInputLayout, TextInputEditText> {
        val layout = TextInputLayout(activity).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            this.hint = hint
            helperText = helper
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        val input = TextInputEditText(layout.context).apply {
            setSingleLine(true)
            setText(value)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        layout.addView(input)
        parent.addView(layout)
        return layout to input
    }
    private fun providerName(id: String) = if (id == "adguard") "AdGuard DNS" else "NextDNS"
    private fun errorMessage(e: Exception): String = when (e) {
        is ApiFailure -> e.message!!
        else -> "Não foi possível consultar. Confira a chave, a conexão e os dados no painel oficial. Nenhum DNS foi alterado."
    }
    fun update(servers: List<DnsServer>) {
        job?.cancel()
        column.removeAllViews()
        text(column, "Gerenciamento", true)
        text(column, "Escolha um hostname para consultar a API ou abrir seu painel. O DNS ativo permanece igual.")
        if (servers.isEmpty()) { text(column, "Cadastre um hostname na aba DNS."); return }
        val current = selected ?: PrivateDNSUtils.getPrivateProvider(activity.contentResolver)
        val initial = servers.indexOfFirst { it.server == current }.coerceAtLeast(0)
        val detail = column()
        fun show(position: Int) {
            job?.cancel()
            val host = servers[position].server
            selected = host
            detail.removeAllViews()
            val config = store.get(host)
            val info = card(detail)
            text(info, when (config.mode) {
                ManagementMode.NONE -> "Sem gerenciamento"
                ManagementMode.WEB -> "Painel web"
                ManagementMode.API -> providerName(config.provider)
            }, true)
            text(info, host)
            if (config.mode == ManagementMode.API) text(info, "Perfil/servidor: ${config.profile} · somente leitura")
            if (config.mode == ManagementMode.WEB) text(info, config.url)
            button(info, "Configurar gerenciamento") { edit(host) { update(servers) } }
            val outputBox = card(detail).apply { visibility = View.GONE }
            val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true; visibility = View.GONE }
            outputBox.addView(progress)
            val output = text(outputBox, "")
            button(info, "Gerenciar", true) {
                when (config.mode) {
                    ManagementMode.NONE -> edit(host) { update(servers) }
                    ManagementMode.WEB -> openWeb(config)
                    ManagementMode.API -> {
                        if (job?.isActive != true) {
                            outputBox.visibility = View.VISIBLE
                            output.text = "Consultando…"
                            progress.visibility = View.VISIBLE
                            job = activity.lifecycleScope.launch {
                                try {
                                    val sections = withContext(Dispatchers.IO) {
                                        val key = vault.get("$host:${config.provider}") ?: error("Cadastre uma chave de API")
                                        Providers.get(config.provider).read(config.profile, key)
                                    }
                                    output.text = sections.mapIndexed { index, section -> section.title + "\n" + ReadPresentation.format(config.provider, index, section.json) }.joinToString("\n\n")
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { output.text = errorMessage(e) }
                                finally { progress.visibility = View.GONE }
                            }
                        }
                    }
                }
            }
            if (config.mode == ManagementMode.API) {
                button(info, "Gerenciar chave no site oficial") { openLink(ProviderSetup.keyUrl(config.provider)) }
                button(info, "Esquecer chave neste aparelho") {
                    job?.cancel(); outputBox.visibility = View.VISIBLE; output.text = ""; vault.remove("$host:${config.provider}")
                    output.text = "Chave local removida. Para revogar, abra o gerenciamento de chaves no site oficial."
                }
            }
            button(detail, "Encerrar sessões WebView") {
                MaterialAlertDialogBuilder(activity).setTitle("Encerrar sessões incorporadas?")
                    .setMessage("Apagar cookies e armazenamento de todos os painéis WebView deste aplicativo? As sessões do navegador são gerenciadas pelo próprio navegador.")
                    .setPositiveButton("Apagar") { _, _ ->
                        android.webkit.CookieManager.getInstance().removeAllCookies {
                            android.webkit.CookieManager.getInstance().flush()
                            android.webkit.WebStorage.getInstance().deleteAllData()
                            outputBox.visibility = View.VISIBLE
                            output.text = "Sessões WebView removidas."
                        }
                    }.setNegativeButton("Cancelar", null).show()
            }
        }
        choice(column, "Hostname", servers.map { if(it.label.isBlank()) it.server else "${it.label} · ${it.server}" }, initial, ::show)
        column.addView(detail)
        show(initial)
    }
    fun suspendReading() { job?.cancel(); column.removeAllViews() }
    private fun openLink(url: String) {
        if (!ManagementPolicy.validUrl(url)) return
        runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, url.toUri()) }
            .onFailure { Toast.makeText(activity, "Nenhum navegador disponível", Toast.LENGTH_LONG).show() }
    }
    private fun openWeb(config: ManagementConfig) {
        if (!ManagementPolicy.validUrl(config.url)) return
        if (config.webMode == WebMode.CUSTOM_TAB) openLink(config.url)
        else activity.startActivity(Intent(activity, ManagementWebActivity::class.java).putExtra("url", config.url))
    }
    private fun edit(host: String, done: () -> Unit) {
        val saved = store.get(host)
        var providerId = if (store.contains(host)) saved.provider else ProviderSetup.detect(host) ?: saved.provider
        var editorJob: Job? = null
        var targets = emptyList<ProviderTarget>()
        val form = column()
        text(form, "Configurar $host", true)
        val mode = choice(form, "Modo de gerenciamento", listOf("Sem gerenciamento", "Painel web", "API nativa · leitura"), saved.mode.ordinal)
        val webSection = column()
        val nativeSection = column()
        form.addView(webSection)
        form.addView(nativeSection)
        val inferredProfile = if(providerId == "nextdns") ProviderSetup.nextDnsProfile(host).orEmpty() else ""
        val (urlLayout, url) = field(webSection, "URL HTTPS do painel", saved.url.ifEmpty { if (ProviderSetup.detect(host) != null || saved.mode == ManagementMode.API) ProviderSetup.dashboardUrl(providerId, saved.profile.ifEmpty { inferredProfile }) else "" }, "Você pode editar o endereço e usar qualquer provedor.")
        val web = choice(webSection, "Abrir painel em", listOf("Navegador · Custom Tabs", "WebView incorporado"), saved.webMode.ordinal)
        val provider = choice(nativeSection, "Provedor da API", listOf("NextDNS", "AdGuard DNS"), if(providerId == "adguard") 1 else 0)
        val (keyLayout, key) = field(nativeSection, "Chave de API", helper = "Cole somente a chave. Deixe vazio para usar a chave já salva neste aparelho.")
        key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        key.isSaveEnabled = false
        keyLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val keyLink = button(nativeSection, "Criar ou gerenciar chave · ${providerName(providerId)}") { openLink(ProviderSetup.keyUrl(providerId)) }
        val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true; visibility = View.GONE }
        nativeSection.addView(progress)
        val status = text(nativeSection, "A chave é criada no site oficial. Após colar, conecte para identificar o perfil/servidor deste hostname.")
        val (profileLayout, profile) = field(nativeSection, "ID do perfil/servidor", saved.profile.ifEmpty { inferredProfile }, "Preenchido automaticamente quando houver correspondência segura; você também pode editar.")
        lateinit var selectTarget: MaterialButton
        fun displayTargets(result: DiscoveryResult) {
            targets = result.targets
            selectTarget.isEnabled = targets.isNotEmpty()
            if (result.matchedId != null) {
                profile.setText(result.matchedId)
                profileLayout.error = null
                status.text = "Correspondência encontrada para $host. Perfil/servidor preenchido; toque em Salvar para confirmar."
            } else {
                status.text = if(targets.isEmpty()) "Nenhum perfil/servidor encontrado. Abra o painel oficial ou confira as permissões da chave."
                    else "Conectado. Não houve correspondência inequívoca com este hostname. Selecione o perfil/servidor correto na lista."
            }
        }
        button(nativeSection, "Conectar e preencher automaticamente", true) {
            if (editorJob?.isActive != true) {
                val requestedProvider = providerId
                val enteredKey = key.text.toString().trim()
                if (enteredKey.isNotEmpty() && enteredKey.any { it.isISOControl() }) {
                    keyLayout.error = "A chave contém caracteres inválidos."
                } else {
                    keyLayout.error = null
                    progress.visibility = View.VISIBLE
                    status.text = "Consultando perfis/servidores no provedor…"
                    editorJob = activity.lifecycleScope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val token = enteredKey.ifEmpty { vault.get("$host:$requestedProvider").orEmpty() }
                                require(token.isNotBlank())
                                Providers.get(requestedProvider).discover(host, token)
                            }
                            if (requestedProvider == providerId) displayTargets(result)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { status.text = errorMessage(e) + " Use os links abaixo se precisar preencher manualmente." }
                        finally { progress.visibility = View.GONE }
                    }
                }
            }
        }
        selectTarget = button(nativeSection, "Selecionar perfil/servidor encontrado") {
            MaterialAlertDialogBuilder(activity).setTitle("${providerName(providerId)} · perfis/servidores")
                .setItems(targets.map { "${it.name} · ${it.id}" }.toTypedArray()) { _, index ->
                    profile.setText(targets[index].id)
                    profileLayout.error = null
                    status.text = "Selecionado: ${targets[index].name}. Toque em Salvar para confirmar."
                }.setNegativeButton("Cancelar", null).show()
        }.apply { isEnabled = false }
        key.doAfterTextChanged {
            editorJob?.cancel()
            progress.visibility = View.GONE
            targets = emptyList()
            selectTarget.isEnabled = false
            keyLayout.error = null
            status.text = "Chave alterada. Conecte para identificar o perfil/servidor."
        }
        button(nativeSection, "Abrir perfil/servidor no painel oficial") {
            openLink(ProviderSetup.dashboardUrl(providerId, profile.text.toString().trim()))
        }
        text(nativeSection, "As chaves pertencem à conta, não a um hostname. NextDNS: a chave fica no final da página Conta. AdGuard: o botão da chave abre diretamente a seção API keys. Chaves nunca são criadas automaticamente pelo aplicativo.")
        fun visibility() {
            webSection.visibility = if(mode.index == ManagementMode.WEB.ordinal) View.VISIBLE else View.GONE
            nativeSection.visibility = if(mode.index == ManagementMode.API.ordinal) View.VISIBLE else View.GONE
            if (mode.index != ManagementMode.API.ordinal) { editorJob?.cancel(); progress.visibility = View.GONE }
        }
        mode.input.setOnItemClickListener { _, _, position, _ -> mode.index = position; visibility() }
        provider.input.setOnItemClickListener { _, _, position, _ ->
            val next = if(position == 1) "adguard" else "nextdns"
            if (next != providerId) {
                editorJob?.cancel(); progress.visibility = View.GONE
                val previousDefault = ProviderSetup.dashboardUrl(providerId, profile.text.toString().trim())
                providerId = next; provider.index = position
                key.text?.clear(); targets = emptyList(); selectTarget.isEnabled = false
                profile.setText(if(next == "nextdns") ProviderSetup.nextDnsProfile(host).orEmpty() else "")
                if (url.text.isNullOrBlank() || url.text.toString() == previousDefault) url.setText(ProviderSetup.dashboardUrl(next, profile.text.toString()))
                keyLink.text = "Criar ou gerenciar chave · ${providerName(next)}"
                status.text = "Provedor alterado. Conecte para preencher os dados."
            }
        }
        visibility()
        val dialog = MaterialAlertDialogBuilder(activity).setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(editorJob?.isActive == true) { status.text = "Aguarde a consulta terminar antes de salvar."; return@setOnClickListener }
                val updated = ManagementConfig(ManagementMode.entries[mode.index], url.text.toString().trim(), WebMode.entries[web.index], providerId, profile.text.toString().trim())
                urlLayout.error = null; profileLayout.error = null
                when {
                    updated.url.isNotEmpty() && !ManagementPolicy.validUrl(updated.url) -> urlLayout.error = "Informe uma URL HTTPS válida, sem usuário ou senha."
                    updated.mode == ManagementMode.WEB && !ManagementPolicy.validUrl(updated.url) -> urlLayout.error = "Informe a URL HTTPS do painel."
                    updated.mode == ManagementMode.API && !ManagementPolicy.validProfile(updated.profile) -> profileLayout.error = "Conecte para preencher automaticamente, selecione na lista ou use o link do painel."
                    else -> try {
                        if(updated.mode == ManagementMode.API && key.text?.isNotBlank() == true) vault.put("$host:$providerId", key.text.toString().trim())
                        store.save(host, updated)
                        key.text?.clear(); dialog.dismiss(); done()
                    } catch (_: Exception) { status.text = "Não foi possível salvar. Confira a chave e o armazenamento seguro do aparelho." }
                }
            }
        }
        dialog.setOnDismissListener { editorJob?.cancel(); key.text?.clear() }
        dialog.show()
    }
}
