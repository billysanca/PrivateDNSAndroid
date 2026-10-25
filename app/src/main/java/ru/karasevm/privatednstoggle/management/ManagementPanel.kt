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
class ManagementPanel(private val activity: AppCompatActivity, private val settingsAction: (Int) -> Unit = {}, private val navigate: (Int) -> Unit = {}) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val column = column()
    private val store = ManagementStore(activity)
    private val vault = ApiKeyVault(activity)
    private var job: Job? = null
    private var selected: String? = null
    private var page = 3
    private var lastServers = emptyList<DnsServer>()
    private val cache = DashboardCache()
    private var openedWeb: String? = null
    private val updateTimes = mutableMapOf<String, Long>()
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
            radius = dp(28).toFloat()
            cardElevation = 0f
            strokeWidth = 0
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
        is RuleFailure -> e.message!!
        else -> "Não foi possível consultar. Confira a chave, a conexão e os dados no painel oficial. Nenhum DNS foi alterado."
    }
    fun update(servers: List<DnsServer>, destination: Int = page, userNavigation: Boolean = false) {
        if (userNavigation) openedWeb = null
        page = destination.coerceIn(1, 3)
        lastServers = servers
        job?.cancel()
        column.removeAllViews()
        text(column, when(page) { 1 -> "Estatísticas"; 2 -> "Consultas"; else -> "Configurações" }, true).textSize = 32f
        text(column, when(page) { 1 -> "Seu DNS em números · últimas 24 horas"; 2 -> "Atividade recente do provedor · últimas 24 horas"; else -> "Contas, painéis e preferências do Nexo DNS" })
        if (servers.isEmpty()) {
            if(page == 3) renderAppPreferences(column)
            val empty = card(column)
            text(empty, "Adicione seu primeiro DNS", true)
            text(empty, "Os endereços continuam na primeira aba, como no aplicativo original.")
            button(empty, "Abrir aba DNS", true) { navigate(0) }
            return
        }
        val active = PrivateDNSUtils.getPrivateProvider(activity.contentResolver)
        val current = if (page == 3) selected ?: active else active
        val activeIndex = if(page == 3) servers.indexOfFirst { it.server == current } else ManagementRouting.activeIndex(servers.map { it.server }, PrivateDNSUtils.getPrivateMode(activity.contentResolver), active)
        if (page != 3 && (activeIndex < 0 || PrivateDNSUtils.getPrivateMode(activity.contentResolver) != "hostname")) {
            val empty = card(column)
            text(empty, "Nenhum DNS privado cadastrado está ativo", true)
            text(empty, "Ative um endereço na primeira aba para consultar o provedor correspondente.")
            button(empty, "Abrir aba DNS", true) { navigate(0) }
            return
        }
        val initial = activeIndex.coerceAtLeast(0)
        val detail = column()
        fun configure(host: String) { edit(host) {
            cache.clear()
            val destination = if(store.get(host).mode == ManagementMode.API) 1 else 3
            if (page == destination) update(lastServers, page) else navigate(destination)
        } }
        fun show(position: Int) {
            job?.cancel()
            val host = servers[position].server
            selected = host
            detail.removeAllViews()
            val config = ManagementRouting.effective(host, store.get(host))
            val context = card(detail)
            text(context, if(config.mode == ManagementMode.API) providerName(config.provider) else if(config.mode == ManagementMode.WEB) "Painel web" else "Conta não conectada", true)
            text(context, if(config.mode == ManagementMode.API) "${config.profile} · $host" else host)
            if (page == 3) {
                button(context, if(config.mode == ManagementMode.NONE) "Conectar conta ou painel" else "Gerenciar conexão", true) { configure(host) }
                if (config.mode == ManagementMode.API) {
                    button(context, "Estatísticas do DNS ativo") { navigate(1) }
                    button(context, "Criar ou gerenciar chave no site") { openLink(ProviderSetup.keyUrl(config.provider)) }
                    button(context, "Abrir perfil no painel oficial") { openLink(ProviderSetup.dashboardUrl(config.provider, config.profile)) }
                    button(context, "Desconectar chave neste aparelho") {
                        job?.cancel(); cache.clear(); vault.remove("$host:${config.provider}")
                        text(context, "Chave local removida. A revogação remota é feita no site oficial.")
                    }
                } else if(config.mode == ManagementMode.WEB) {
                    text(context, config.url)
                    button(context, "Gerenciar painel web") { openWeb(config) }
                }
                renderAppPreferences(detail)
                return
            }
            if (config.mode != ManagementMode.API) {
                val url = config.pageUrl(page)
                text(context, "Painel web · login e autenticação ficam com o provedor.")
                button(context, "Configurar endereços das páginas") { configure(host) }
                if (ManagementPolicy.validUrl(url)) {
                    if ((if(page == 1) config.statisticsUrl else config.queriesUrl).isBlank())
                        text(context, "Página específica não configurada; abrindo o painel geral.")
                    button(context, if(page == 1) "Abrir estatísticas no painel" else "Abrir consultas no painel", true) { openWeb(config.copy(url = url)) }
                    val route = "$host|$page|$url"
                    if (openedWeb != route) {
                        openedWeb = route
                        view.post { if(view.isShown && page == destination && selected == host) openWeb(config.copy(url = url)) }
                    }
                } else text(context, "Informe as URLs HTTPS das páginas em Configurações para abri-las automaticamente.")
                return
            }
            if (!ManagementPolicy.validProfile(config.profile) || runCatching { vault.get("$host:${config.provider}") }.getOrNull().isNullOrBlank()) {
                text(context, "API selecionada automaticamente. Conecte a chave para consultar este DNS.")
                button(context, "Conectar API", true) { configure(host) }
                return
            }
            val results = column()
            val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true }
            detail.addView(progress)
            detail.addView(results)
            button(context, "Atualizar dados") { cache.clear(); update(lastServers, page) }
            val requestKey = "$host|${config.provider}|${config.profile}"
            val updateLabel = text(context, "Aguardando atualização…")
            fun showTime() {
                updateTimes[requestKey]?.let {
                    updateLabel.text = "Atualizado às " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))
                }
            }
            fun render(sections: List<ReadSection>) {
                results.removeAllViews()
                if(page == 1) renderStats(results, config, sections) else renderQueries(results, host, config, sections)
            }
            val existing = cache.get(requestKey, android.os.SystemClock.elapsedRealtime())
            if (existing != null) {
                progress.visibility = View.GONE
                showTime()
                runCatching { render(existing) }.onFailure { text(results, "Resposta incompatível. Atualize os dados ou confira a conexão.") }
                return
            }
            job = activity.lifecycleScope.launch {
                try {
                    val sections = withContext(Dispatchers.IO) {
                        val token = vault.get("$host:${config.provider}") ?: error("Conecte a conta em Configurações")
                        Providers.get(config.provider).read(config.profile, token)
                    }
                    render(sections)
                    cache.put(requestKey, sections, android.os.SystemClock.elapsedRealtime())
                    updateTimes[requestKey] = System.currentTimeMillis(); showTime()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    results.removeAllViews()
                    val error = card(results)
                    text(error, "Não foi possível atualizar", true)
                    text(error, errorMessage(e))
                    button(error, "Revisar conexão") { configure(host) }
                } finally { progress.visibility = View.GONE }
            }
        }
        if(page == 3) choice(column, "DNS / perfil selecionado", servers.map { if(it.label.isBlank()) it.server else "${it.label} · ${it.server}" }, initial, ::show)
        if(page != 3) text(column, "DNS ativo · ${servers[initial].server}")
        column.addView(detail)
        show(initial)
    }
    private fun renderAppPreferences(parent: LinearLayout) {
                val preferences = card(parent)
                text(preferences, "Aplicativo", true)
                button(preferences, "Preferências do alternador DNS") {
                    ru.karasevm.privatednstoggle.ui.OptionsDialogFragment().show(activity.supportFragmentManager, "dns_options")
                }
                listOf(
                    "Importar de arquivo" to ru.karasevm.privatednstoggle.R.id.import_settings_file,
                    "Importar da área de transferência" to ru.karasevm.privatednstoggle.R.id.import_settings_clipboard,
                    "Exportar para arquivo" to ru.karasevm.privatednstoggle.R.id.export_settings_file,
                    "Copiar backup" to ru.karasevm.privatednstoggle.R.id.export_settings_clipboard,
                    "Compartilhar backup" to ru.karasevm.privatednstoggle.R.id.export_settings_share,
                    "Privacidade" to ru.karasevm.privatednstoggle.R.id.privacy_policy,
                    "Licenças" to ru.karasevm.privatednstoggle.R.id.open_source_licenses
                ).forEach { (label, id) -> button(preferences, label) { settingsAction(id) } }
                text(preferences, "O seletor de Configurações edita conexões; Estatísticas e Consultas sempre seguem o DNS ativo.")
                button(preferences, "Encerrar sessões WebView") {
                    MaterialAlertDialogBuilder(activity).setTitle("Encerrar sessões incorporadas?")
                        .setMessage("Apagar cookies e armazenamento de todos os painéis WebView deste aplicativo? As sessões do navegador permanecem no navegador.")
                        .setPositiveButton("Apagar") { _, _ ->
                            android.webkit.CookieManager.getInstance().removeAllCookies {
                                android.webkit.CookieManager.getInstance().flush()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                Toast.makeText(activity, "Sessões WebView removidas", Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton("Cancelar", null).show()
                }
    }
    private fun number(value: Long) = java.text.NumberFormat.getIntegerInstance().format(value)
    private fun metric(parent: LinearLayout, title: String, value: Long) {
        val box = card(parent)
        text(box, title)
        text(box, number(value), true).apply {
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 18, 42, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
            maxLines = 1
        }
        if(parent.orientation == LinearLayout.HORIZONTAL) {
            (box.parent as View).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(8), dp(4), dp(8)) }
        }
    }
    private fun renderStats(parent: LinearLayout, config: ManagementConfig, sections: List<ReadSection>) {
        val stats = DashboardData.stats(config.provider, sections[0].json)
        metric(parent, "Consultas nas últimas 24 horas", stats.total)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(row)
        metric(row, "Bloqueadas", stats.blocked)
        metric(row, "Não bloqueadas", stats.other)
        val distribution = card(parent)
        text(distribution, "Distribuição das consultas", true)
        for ((name, count) in listOf("Bloqueadas" to stats.blocked, "Não bloqueadas" to stats.other)) {
            text(distribution, "$name · ${number(count)}")
            distribution.addView(LinearProgressIndicator(activity).apply {
                isIndeterminate = false; max = 100
                progress = if(stats.total > 0) ((count.toDouble() / stats.total) * 100).toInt() else 0
                trackThickness = dp(8); trackCornerRadius = dp(4)
                contentDescription = "$name: ${number(count)} de ${number(stats.total)}"
            })
        }
        text(distribution, if(stats.total > 0) "${String.format(java.util.Locale.getDefault(), "%.1f", stats.blocked.toDouble() * 100 / stats.total)}% das consultas foram bloqueadas" else "Sem consultas registradas no período")
        val quota = card(parent)
        text(quota, "Consumo da conta", true)
        if(config.provider == "nextdns") {
            text(quota, "A API pública não informa a cota da conta. Os números acima pertencem apenas ao perfil selecionado.")
            button(quota, "Ver consumo na conta NextDNS") { openLink(ProviderSetup.keyUrl(config.provider)) }
        } else {
            val labels = mapOf("requests" to "Consultas", "devices" to "Dispositivos", "dns_servers" to "Servidores", "user_rules" to "Regras", "access_rules" to "Regras de acesso", "dedicated_ipv4" to "IPv4 dedicados")
            val quotas = DashboardData.quotas(sections[2].json)
            if(quotas.isEmpty()) text(quota, "Cotas não informadas pelo provedor.")
            quotas.forEach { limit ->
                text(quota, "${labels[limit.name] ?: limit.name}: ${number(limit.used)} / ${number(limit.limit)}")
                if(limit.limit > 0) quota.addView(LinearProgressIndicator(activity).apply {
                    isIndeterminate = false; max = 100
                    progress = ((limit.used.toDouble() / limit.limit) * 100).coerceIn(0.0, 100.0).toInt()
                })
            }
            text(quota, "Cotas da conta inteira; estatísticas acima filtradas pelo servidor.")
        }
    }
    private fun renderQueries(parent: LinearLayout, host: String, config: ManagementConfig, sections: List<ReadSection>) {
        val queries = DashboardData.queries(config.provider, sections[1].json)
        val (_, search) = field(parent, "Buscar domínio nestas consultas")
        val filters = com.google.android.material.chip.ChipGroup(activity).apply { isSingleSelection = true; isSelectionRequired = true }
        var filter = 0
        val list = column()
        fun renderList() {
            list.removeAllViews()
            val needle = search.text.toString().trim()
            val matching = queries.filter { it.domain.contains(needle, true) && (filter == 0 || (filter == 1 && it.blocked) || (filter == 2 && !it.blocked)) }
            if(matching.isEmpty()) text(list, if(queries.isEmpty()) "Nenhuma consulta no período ou registro desativado no provedor." else "Nenhuma consulta corresponde ao filtro.")
            matching.forEach { query ->
                val entry = card(list)
                text(entry, query.domain.ifBlank { "Domínio não informado" }, true).textSize = 18f
                val time = runCatching { java.time.OffsetDateTime.parse(query.timestamp).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")) }.getOrDefault(query.timestamp)
                text(entry, "${time.ifBlank { "Horário não informado" }} · ${query.status.ifBlank { "Status não informado" }}")
                if(query.device.isNotBlank()) text(entry, "Dispositivo: ${query.device}")
                text(entry, if(query.status.isBlank()) "? Status não informado" else if(query.blocked) "⛔ Bloqueada no registro" else "✓ Não bloqueada no registro")
                if (config.provider == "nextdns" && runCatching { DomainPolicy.normalize(query.domain) }.isSuccess) {
                    button(entry, "Bloquear domínio") { confirmRule(host, config, query.domain, false, entry) }
                    button(entry, "Liberar domínio") { confirmRule(host, config, query.domain, true, entry) }
                } else if (config.provider == "adguard") {
                    button(entry, "Gerenciar regra no AdGuard") { openLink(ProviderSetup.dashboardUrl(config.provider, config.profile)) }
                }
            }
        }
        listOf("Todas", "Bloqueadas", "Outras").forEachIndexed { index, label ->
            val chip = com.google.android.material.chip.Chip(activity).apply {
                id = View.generateViewId(); text = label; isCheckable = true; isChecked = index == 0
                setOnCheckedChangeListener { _, checked -> if(checked) { filter = index; renderList() } }
            }
            filters.addView(chip)
        }
        parent.addView(filters)
        text(parent, "Até 50 consultas recentes. A busca e os filtros se aplicam somente a esta primeira página.")
        parent.addView(list)
        search.doAfterTextChanged { renderList() }
        renderList()
    }

    private var writing = false
    private fun confirmRule(host: String, config: ManagementConfig, raw: String, allow: Boolean, parent: LinearLayout) {
        if(writing) return
        val domain = DomainPolicy.normalize(raw)
        val action = if(allow) "Liberar" else "Bloquear"
        MaterialAlertDialogBuilder(activity).setTitle("$action $domain?")
            .setMessage("""NextDNS · perfil ${config.profile}
$host

Adicionar à ${if(allow) "Allowlist" else "Denylist"}. A regra afeta todos os dispositivos deste perfil e pode abranger subdomínios. Outras regras e caches podem influenciar a resolução. Nenhuma lista será substituída.""")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar $action") { _, _ ->
                if(writing) return@setPositiveButton
                writing = true
                val result = text(parent, "◷ Enviando regra…")
                activity.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val key = vault.get("$host:${config.provider}") ?: throw RuleFailure("Reconecte a chave em Configurações.")
                            NextDnsDomainRules().apply(config.profile, domain, allow, key)
                        }
                        cache.clear()
                        result.text = "✓ Regra confirmada na ${if(allow) "Allowlist" else "Denylist"}. O registro histórico permanece igual."
                        com.google.android.material.snackbar.Snackbar.make(view, "Regra confirmada: $domain", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                    } catch(e: CancellationException) { throw e }
                    catch(e: Exception) {
                        result.text = "⚠ " + errorMessage(e) + " Confira o painel antes de repetir."
                        com.google.android.material.snackbar.Snackbar.make(view, result.text, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                    } finally { writing = false }
                }
            }.show()
    }
    fun suspendReading() { job?.cancel(); cache.clear(); column.removeAllViews() }
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
        var providerId = ProviderSetup.detect(host) ?: saved.provider
        var editorJob: Job? = null
        var targets = emptyList<ProviderTarget>()
        var discoveryComplete = saved.mode == ManagementMode.API && ManagementPolicy.validProfile(saved.profile)
        var saveAfterDiscovery = false
        var manualId = false
        lateinit var dialog: AlertDialog
        val form = column()
        text(form, "Configurar $host", true)
        val nativeProvider = ProviderSetup.detect(host)
        val mode = choice(form, "Modo de gerenciamento", listOf("Sem gerenciamento", "Painel web", "API nativa"), if(nativeProvider != null) ManagementMode.API.ordinal else saved.mode.ordinal)
        if(nativeProvider != null) { mode.layout.visibility = View.GONE; text(form, "API nativa selecionada automaticamente · ${providerName(nativeProvider)}") }
        val webSection = column()
        val nativeSection = column()
        form.addView(webSection)
        form.addView(nativeSection)
        val inferredProfile = if(providerId == "nextdns") ProviderSetup.nextDnsProfile(host).orEmpty() else ""
        val (urlLayout, url) = field(webSection, "URL HTTPS do painel", saved.url.ifEmpty { if (ProviderSetup.detect(host) != null || saved.mode == ManagementMode.API) ProviderSetup.dashboardUrl(providerId, saved.profile.ifEmpty { inferredProfile }) else "" }, "Você pode editar o endereço e usar qualquer provedor.")
        val (_, statisticsUrl) = field(webSection, "URL HTTPS de Estatísticas", saved.statisticsUrl, "Vazio: usar o painel geral.")
        val (_, queriesUrl) = field(webSection, "URL HTTPS de Consultas", saved.queriesUrl, "Vazio: usar o painel geral.")
        val web = choice(webSection, "Abrir painel em", listOf("Navegador · Custom Tabs", "WebView incorporado"), saved.webMode.ordinal)
        val provider = choice(nativeSection, "Provedor da API", listOf("NextDNS", "AdGuard DNS"), if(providerId == "adguard") 1 else 0)
        if(nativeProvider != null) provider.layout.visibility = View.GONE
        val (keyLayout, key) = field(nativeSection, "Chave de API", helper = "Cole somente a chave. Deixe vazio para usar a chave já salva neste aparelho.")
        key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        key.isSaveEnabled = false
        keyLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        val keyLink = button(nativeSection, "Criar ou gerenciar chave · ${providerName(providerId)}") { openLink(ProviderSetup.keyUrl(providerId)) }
        val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true; visibility = View.GONE }
        nativeSection.addView(progress)
        val status = text(nativeSection, "Cole a chave e toque em Salvar. A conta e o perfil serão conectados automaticamente quando houver correspondência segura.")
        val (profileLayout, profile) = field(nativeSection, "ID do perfil/servidor", saved.profile.ifEmpty { inferredProfile }, "Preenchido automaticamente quando houver correspondência segura; você também pode editar.")
        lateinit var selectTarget: MaterialButton
        fun displayTargets(result: DiscoveryResult) {
            targets = result.targets
            selectTarget.isEnabled = targets.isNotEmpty()
            if (result.matchedId != null) {
                discoveryComplete = true
                profile.setText(result.matchedId)
                profileLayout.error = null
                status.text = "Correspondência encontrada para $host. Perfil/servidor preenchido; toque em Salvar para confirmar."
            } else {
                status.text = if(targets.isEmpty()) "Nenhum perfil/servidor encontrado. Abra o painel oficial ou confira as permissões da chave."
                    else "Conectado. Não houve correspondência inequívoca com este hostname. Selecione o perfil/servidor correto na lista."
            }
            if (saveAfterDiscovery) {
                if (result.matchedId != null) {
                    saveAfterDiscovery = false
                    profile.post { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
                } else if(targets.isNotEmpty()) {
                    selectTarget.post { selectTarget.performClick() }
                } else saveAfterDiscovery = false
            }
        }
        val connect = button(nativeSection, "Reconectar e identificar perfil", true) {
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
                        catch (e: Exception) { saveAfterDiscovery = false; status.text = errorMessage(e) + " Use os links ou ative o preenchimento manual." }
                        finally { progress.visibility = View.GONE }
                    }
                }
            }
        }
        selectTarget = button(nativeSection, "Selecionar perfil/servidor encontrado") {
            MaterialAlertDialogBuilder(activity).setTitle("${providerName(providerId)} · perfis/servidores")
                .setItems(targets.map { "${it.name} · ${it.id}" }.toTypedArray()) { _, index ->
                    discoveryComplete = true
                    profile.setText(targets[index].id)
                    profileLayout.error = null
                    status.text = "Selecionado: ${targets[index].name}."
                    if (saveAfterDiscovery) { saveAfterDiscovery = false; profile.post { dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick() } }
                }.setNegativeButton("Cancelar") { _, _ -> saveAfterDiscovery = false }
                .setOnCancelListener { saveAfterDiscovery = false }.show()
        }.apply { isEnabled = false }
        button(nativeSection, "Usar ID manual") {
            manualId = true; saveAfterDiscovery = false
            status.text = "Preenchimento manual ativado. Confira o ID no painel oficial e salve."
        }
        key.doAfterTextChanged {
            editorJob?.cancel()
            discoveryComplete = false; manualId = false; saveAfterDiscovery = false
            progress.visibility = View.GONE
            targets = emptyList()
            selectTarget.isEnabled = false
            keyLayout.error = null
            status.text = "Chave alterada. Toque em Salvar para conectar automaticamente."
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
                discoveryComplete = false; manualId = false; saveAfterDiscovery = false
                key.text?.clear(); targets = emptyList(); selectTarget.isEnabled = false
                profile.setText(if(next == "nextdns") ProviderSetup.nextDnsProfile(host).orEmpty() else "")
                if (url.text.isNullOrBlank() || url.text.toString() == previousDefault) url.setText(ProviderSetup.dashboardUrl(next, profile.text.toString()))
                keyLink.text = "Criar ou gerenciar chave · ${providerName(next)}"
                status.text = "Provedor alterado. Conecte para preencher os dados."
            }
        }
        visibility()
        dialog = MaterialAlertDialogBuilder(activity).setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(editorJob?.isActive == true) { status.text = "Aguarde a consulta terminar antes de salvar."; return@setOnClickListener }
                if (mode.index == ManagementMode.API.ordinal && !manualId && !discoveryComplete) {
                    saveAfterDiscovery = true
                    connect.performClick()
                    return@setOnClickListener
                }
                val updated = ManagementConfig(ManagementMode.entries[mode.index], url.text.toString().trim(), WebMode.entries[web.index], providerId, profile.text.toString().trim(), statisticsUrl.text.toString().trim(), queriesUrl.text.toString().trim())
                urlLayout.error = null; profileLayout.error = null
                when {
                    listOf(updated.statisticsUrl, updated.queriesUrl).any { it.isNotEmpty() && !ManagementPolicy.validUrl(it) } -> urlLayout.error = "As páginas devem usar HTTPS, sem usuário ou senha."
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
