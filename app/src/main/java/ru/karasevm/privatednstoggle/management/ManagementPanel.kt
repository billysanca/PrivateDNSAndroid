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
import kotlinx.coroutines.flow.collect
import ru.karasevm.privatednstoggle.model.DnsServer
import ru.karasevm.privatednstoggle.util.PrivateDNSUtils

/** Material 3 Views. Web settings remain independent of native provider setup and discovery. */
class ManagementPanel(private val activity: AppCompatActivity, private val chosenHost: () -> String? = { PrivateDNSUtils.getPrivateProvider(activity.contentResolver) }, private val settingsAction: (Int) -> Unit = {}, private val providerFactory: (String) -> DnsProvider = { Providers.get(it) }, private val credentials: ((String, String) -> String?)? = null, private val navigate: (Int) -> Unit = {}) {
    val view = ScrollView(activity).apply { isFillViewport = true }
    private val column = column()
    private val store = ManagementStore(activity)
    private val vault = ApiKeyVault(activity)
    private var job: Job? = null
    private var refreshMode = 0
    private var filterHost: String? = null
    private var querySearch = ""
    private var queryFilter = 0
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
    private fun preference(parent: LinearLayout, title: String, summary: String? = null, action: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(56)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            val ripple = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            isFocusable = true
            setOnClickListener { action() }
        }
        text(row, title).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setPadding(0, 0, 0, 0)
        }
        if(summary != null) text(row, summary).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(2), 0, 0)
        }
        parent.addView(row)
        parent.addView(com.google.android.material.divider.MaterialDivider(activity))
    }
    private fun card(parent: LinearLayout): LinearLayout {
        val content = column()
        parent.addView(MaterialCardView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            radius = dp(20).toFloat()
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
    private fun readKey(host: String, provider: String): String? = if(credentials != null) credentials.invoke(host, provider) else vault.get("$host:$provider")
    private fun providerName(id: String) = if (id == "adguard") "AdGuard DNS" else "NextDNS"
    private fun errorMessage(e: Exception): String = when (e) {
        is ApiFailure -> e.message!!
        is RuleFailure -> e.message!!
        else -> "Não foi possível consultar. Confira a chave, a conexão e os dados no painel oficial. Nenhum DNS foi alterado."
    }
    fun update(servers: List<DnsServer>, destination: Int = page, userNavigation: Boolean = false) {
        if (userNavigation) openedWeb = null
        page = destination.coerceIn(1, 3)
        if(page != 2) refreshMode = 0
        lastServers = servers
        job?.cancel()
        column.removeAllViews()
        text(column, when(page) { 1 -> "Seu DNS em números · últimas 24 horas"; 2 -> "Atividade recente do provedor · últimas 24 horas"; else -> "Contas, painéis e preferências do Nexo DNS" })
        if(page == 3) renderAppearance(column)
        if (servers.isEmpty()) {
            if(page == 3) renderAppPreferences(column)
            val empty = card(column)
            text(empty, "Adicione seu primeiro DNS", true)
            text(empty, "Os endereços continuam na primeira aba, como no aplicativo original.")
            button(empty, "Abrir aba DNS", true) { navigate(0) }
            return
        }
        val current = chosenHost()
        val activeIndex = servers.indexOfFirst { it.server == current }
        if (activeIndex < 0) {
            if(page == 3) renderAppPreferences(column)
            val empty = card(column)
            text(empty, "Escolha o perfil na primeira aba", true)
            text(empty, "Toque em um hostname na lista DNS. As outras abas usarão esse mesmo perfil, sem seletores próprios.")
            button(empty, "Abrir aba DNS", true) { navigate(0) }
            return
        }
        val initial = activeIndex.coerceAtLeast(0)
        val detail = column().apply { setPadding(0, 0, 0, 0) }
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
            text(context, if(config.mode == ManagementMode.API) "Perfil ${config.profile}" else host)
            if (page == 3) {
                button(context, if(config.mode == ManagementMode.NONE) "Conectar conta ou painel" else "Gerenciar conexão", true) { configure(host) }
                if (config.mode == ManagementMode.API) {
                    preference(context, "Chave de API", "Criar ou gerenciar no site oficial") { openLink(ProviderSetup.keyUrl(config.provider)) }
                    preference(context, "Painel oficial", "Abrir o perfil no navegador") { openLink(ProviderSetup.dashboardUrl(config.provider, config.profile)) }
                    preference(context, "Remover chave deste aparelho", "A revogação remota é feita no site oficial") {
                        MaterialAlertDialogBuilder(activity).setTitle("Remover a chave local?")
                            .setMessage("As consultas serão desconectadas neste aparelho. A chave não será revogada na conta do provedor.")
                            .setPositiveButton("Remover") { _, _ ->
                                job?.cancel(); cache.clear(); vault.remove("$host:${config.provider}")
                                update(lastServers, page)
                            }.setNegativeButton("Cancelar", null).show()
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
            if (!ManagementPolicy.validProfile(config.profile) || runCatching { readKey(host, config.provider) }.getOrNull().isNullOrBlank()) {
                text(context, "API selecionada automaticamente. Conecte a chave para consultar este DNS.")
                button(context, "Conectar API", true) { configure(host) }
                return
            }
            val results = column().apply { setPadding(0, 0, 0, 0) }
            val progress = LinearProgressIndicator(activity).apply { isIndeterminate = true }
            detail.addView(progress)
            detail.addView(results)
            var refresh: Choice? = null
            if(page == 2) {
                val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                context.addView(controls)
                val modeBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                controls.addView(modeBox, LinearLayout.LayoutParams(0, -2, 1f))
                refresh = choice(modeBox, "Atualização", RefreshPolicy.labels, refreshMode) {
                    refreshMode = it
                    update(lastServers, page)
                }
                val refreshButton = MaterialButton(activity, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                    icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, ru.karasevm.privatednstoggle.R.drawable.ic_refresh)
                    contentDescription = "Atualizar consultas"
                    setOnClickListener { update(lastServers, page) }
                }
                controls.addView(refreshButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8); topMargin = dp(12) })
                if(refreshMode == 1) text(context, if(config.provider == "nextdns") "Tempo Real transmite consultas pela API oficial. Ativo somente enquanto esta tela estiver aberta."
                    else "Tempo Real consulta este provedor a cada 5 s. Novos registros dependem da API.")
            }
            val requestKey = "$host|${config.provider}|${config.profile}"
            val updateLabel = text(context, "Conectando ao provedor…")
            fun showTime() {
                updateTimes[requestKey]?.let {
                    updateLabel.text = "Atualizado às " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it))
                }
            }
            var updateQueries: ((List<ReadSection>) -> Unit)? = null
            fun render(sections: List<ReadSection>) {
                val scroll = view.scrollY
                if(page == 1) {
                    results.removeAllViews()
                    renderStats(results, config, sections)
                } else if(updateQueries == null) {
                    results.removeAllViews()
                    updateQueries = renderQueries(results, host, config, sections)
                } else updateQueries.invoke(sections)
                view.post { if(selected == host && view.isShown) view.scrollTo(0, scroll) }
            }
            val existing = if(page == 1) cache.get(requestKey, android.os.SystemClock.elapsedRealtime()) else null
            if(existing != null) {
                progress.visibility = View.GONE
                showTime()
                runCatching { render(existing) }.onFailure { text(results, "Resposta incompatível. Confira a conexão.") }
                return
            }
            job = activity.lifecycleScope.launch {
                while(isActive) {
                    while(writing || !activity.hasWindowFocus()) delay(300)
                    progress.visibility = View.VISIBLE
                    try {
                        val token = withContext(Dispatchers.IO) { readKey(host, config.provider) ?: error("Reconecte a conta") }
                        val sections = withContext(Dispatchers.IO) {
                            val provider = providerFactory(config.provider)
                            if(page == 2) listOf(ReadSection("", ""), provider.queries(config.profile, token), ReadSection("", ""))
                            else provider.statistics(config.profile, token)
                        }
                        while(writing || !activity.hasWindowFocus()) delay(300)
                        render(sections)
                        if(page == 1) cache.put(requestKey, sections, android.os.SystemClock.elapsedRealtime())
                        updateTimes[requestKey] = System.currentTimeMillis()
                        showTime()
                        progress.visibility = View.GONE
                        if(page == 2 && refreshMode == 1 && config.provider == "nextdns") {
                            updateLabel.text = "Conectando ao Tempo Real…"
                            NextDnsLive().snapshots(config.profile, token, sections[1].json).collect { snapshot ->
                                while(writing || !activity.hasWindowFocus()) delay(300)
                                render(listOf(ReadSection("", ""), ReadSection("Consultas ao vivo", snapshot), ReadSection("", "")))
                                updateLabel.text = "● Ao vivo · " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date())
                                delay(250)
                            }
                        }
                    } catch(e: CancellationException) { throw e }
                    catch(e: Exception) {
                        refreshMode = 0
                        refresh?.let { it.index = 0; it.input.setText(RefreshPolicy.labels[0], false) }
                        updateLabel.text = "Atualização interrompida"
                        val error = card(results)
                        text(error, errorMessage(e))
                        button(error, "Revisar conexão") { configure(host) }
                    } finally { progress.visibility = View.GONE }
                    val interval = if(page == 2) RefreshPolicy.interval(refreshMode) else 0L
                    if(interval == 0L) break
                    delay(interval)
                }
            }
        }
        column.addView(detail)
        show(initial)
    }
    private fun renderAppearance(parent: LinearLayout) {
        val box = card(parent)
        text(box, "Aparência", true)
        val settings = ru.karasevm.privatednstoggle.ui.ThemeSettings
        val modes = ru.karasevm.privatednstoggle.ui.ThemeMode.entries
        choice(box, "Tema", listOf("Usar tema do celular", "Claro", "Escuro", "Preto OLED"), settings.mode(activity).ordinal) { index ->
            settings.save(activity, modes[index], settings.dynamic(activity))
            settings.apply(activity)
        }
        val dynamic = com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
            text = "Cores do celular · Material You"
            isChecked = settings.dynamic(activity)
            isEnabled = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
            minimumHeight = dp(48)
            setOnCheckedChangeListener { _, checked ->
                settings.save(activity, settings.mode(activity), checked)
                activity.recreate()
            }
        }
        box.addView(dynamic, LinearLayout.LayoutParams(-1, -2))
        text(box, if(dynamic.isEnabled) "As cores de destaque acompanham o papel de parede. O tema define a luminosidade."
            else "Cores dinâmicas exigem Android 12 ou posterior e um aparelho compatível. A paleta Nexo permanece disponível.")
    }
    private fun renderAppPreferences(parent: LinearLayout) {
        val preferences = card(parent)
        text(preferences, "Aplicativo", true)
        preference(preferences, "Alternador de DNS", "Preferências originais, Quick Settings e Shizuku") {
            ru.karasevm.privatednstoggle.ui.OptionsDialogFragment().show(activity.supportFragmentManager, "dns_options")
        }
        preference(preferences, "Importar hostnames", "Arquivo JSON ou área de transferência") {
            MaterialAlertDialogBuilder(activity).setTitle("Importar hostnames")
                .setItems(arrayOf("Arquivo", "Área de transferência")) { _, index ->
                    settingsAction(if(index == 0) ru.karasevm.privatednstoggle.R.id.import_settings_file else ru.karasevm.privatednstoggle.R.id.import_settings_clipboard)
                }.setNegativeButton("Cancelar", null).show()
        }
        preference(preferences, "Exportar hostnames", "Chaves de API e sessões não entram no backup") {
            val ids = intArrayOf(ru.karasevm.privatednstoggle.R.id.export_settings_file, ru.karasevm.privatednstoggle.R.id.export_settings_clipboard, ru.karasevm.privatednstoggle.R.id.export_settings_share)
            MaterialAlertDialogBuilder(activity).setTitle("Exportar hostnames")
                .setItems(arrayOf("Salvar arquivo", "Copiar", "Compartilhar")) { _, index -> settingsAction(ids[index]) }
                .setNegativeButton("Cancelar", null).show()
        }
        preference(preferences, "Encerrar sessões WebView", "Remove cookies de todos os painéis incorporados") {
            MaterialAlertDialogBuilder(activity).setTitle("Encerrar sessões incorporadas?")
                .setMessage("Apagar cookies e armazenamento dos painéis WebView? As sessões do navegador permanecem no navegador.")
                .setPositiveButton("Apagar") { _, _ ->
                    android.webkit.CookieManager.getInstance().removeAllCookies {
                        android.webkit.CookieManager.getInstance().flush()
                        android.webkit.WebStorage.getInstance().deleteAllData()
                        Toast.makeText(activity, "Sessões WebView removidas", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton("Cancelar", null).show()
        }
        preference(preferences, "Privacidade") { settingsAction(ru.karasevm.privatednstoggle.R.id.privacy_policy) }
        preference(preferences, "Licenças de código aberto") { settingsAction(ru.karasevm.privatednstoggle.R.id.open_source_licenses) }
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
    private fun renderQueries(parent: LinearLayout, host: String, config: ManagementConfig, sections: List<ReadSection>): (List<ReadSection>) -> Unit {
        var queries = DashboardData.queries(config.provider, sections[1].json).take(50)
        if(filterHost != host) { filterHost = host; querySearch = ""; queryFilter = 0 }
        val (_, search) = field(parent, "Buscar domínio", querySearch)
        val filters = com.google.android.material.chip.ChipGroup(activity).apply { isSingleSelection = true; isSelectionRequired = true }
        var filter = queryFilter
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var rowCache = mutableMapOf<DashboardQuery, java.util.ArrayDeque<Pair<LinearLayout, com.google.android.material.divider.MaterialDivider>>>()
        fun renderList() {
            val nextCache = mutableMapOf<DashboardQuery, java.util.ArrayDeque<Pair<LinearLayout, com.google.android.material.divider.MaterialDivider>>>()
            list.removeAllViews()
            val needle = search.text.toString().trim()
            val matching = queries.filter { it.domain.contains(needle, true) && (filter == 0 || (filter == 1 && it.blocked) || (filter == 2 && !it.blocked)) }
            if(matching.isEmpty()) text(list, if(queries.isEmpty()) "Nenhuma consulta no período ou registro desativado no provedor." else "Nenhuma consulta corresponde ao filtro.")
            matching.forEachIndexed { index, query ->
                val cached = rowCache[query]?.pollFirst()
                if(cached != null) {
                    cached.first.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(cached.first,
                        if(index % 2 == 0) com.google.android.material.R.attr.colorSurfaceContainerLow else com.google.android.material.R.attr.colorSurfaceContainer))
                    list.addView(cached.first); list.addView(cached.second)
                    nextCache.getOrPut(query) { java.util.ArrayDeque() }.add(cached)
                    return@forEachIndexed
                }
                val entry = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(4), dp(4), dp(4))
                    setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this,
                        if(index % 2 == 0) com.google.android.material.R.attr.colorSurfaceContainerLow
                        else com.google.android.material.R.attr.colorSurfaceContainer))
                }
                list.addView(entry)
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
                entry.addView(row)
                val menu = com.google.android.material.button.MaterialButton(activity, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                    icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, ru.karasevm.privatednstoggle.R.drawable.ic_more_vert)
                    contentDescription = "Menu do domínio " + query.domain
                    minimumWidth = dp(48); minimumHeight = dp(48)
                    insetTop = 0; insetBottom = 0
                }
                row.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
                text(content, query.domain.ifBlank { "Domínio não informado" }, true).apply { textSize = 15f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, dp(2)) }
                val time = runCatching { java.time.OffsetDateTime.parse(query.timestamp).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")) }.getOrDefault(query.timestamp)
                text(content, "${time.ifBlank { "Horário não informado" }} · ${query.status.ifBlank { "Status não informado" }}").apply { textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 0, 0, dp(2)) }
                if(query.device.isNotBlank()) text(content, query.device).apply { textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 0, 0, dp(2)) }
                val statusIcon = if(query.blocked) "⛔ " else if(query.status.isBlank()) "? " else "✓ "
                (content.getChildAt(0) as MaterialTextView).text = statusIcon + query.domain.ifBlank { "Domínio não informado" }
                (content.getChildAt(0) as MaterialTextView).setTextColor(com.google.android.material.color.MaterialColors.getColor(entry,
                    if(query.blocked) androidx.appcompat.R.attr.colorError else com.google.android.material.R.attr.colorOnSurface))
                val canUseDomain = runCatching { DomainPolicy.normalize(query.domain) }.isSuccess
                if(canUseDomain) {
                    fun actions() {
                        val native = config.provider == "nextdns"
                        val labels = if(native) arrayOf("Bloquear domínio", "Liberar domínio", "Abrir painel oficial")
                            else arrayOf("Gerenciar regra no painel oficial")
                        val popup = androidx.appcompat.widget.PopupMenu(activity, menu)
                        labels.forEachIndexed { index, label -> popup.menu.add(0, index, index, label) }
                        popup.setOnMenuItemClickListener { item ->
                            val index = item.itemId
                            if(native && index < 2) confirmRule(host, config, query.domain, index == 1, entry)
                            else if(!native) MaterialAlertDialogBuilder(activity)
                                .setTitle("Gerenciar regra no provedor")
                                .setMessage("A API do AdGuard substitui a lista inteira de regras e não oferece adição individual com proteção contra alterações concorrentes. Para preservar suas regras, esta ação abre o painel oficial.")
                                .setPositiveButton("Abrir painel") { _, _ -> openWeb(config.copy(url = ProviderSetup.dashboardUrl(config.provider, config.profile))) }
                                .setNegativeButton("Cancelar", null).show()
                            else openWeb(config.copy(url = ProviderSetup.dashboardUrl(config.provider, config.profile)))
                            true
                        }
                        popup.show()
                    }
                    menu.setOnClickListener { actions() }
                    entry.setOnLongClickListener { actions(); true }
                } else menu.visibility = View.INVISIBLE
                val divider = com.google.android.material.divider.MaterialDivider(activity).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                list.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
                nextCache.getOrPut(query) { java.util.ArrayDeque() }.add(entry to divider)
            }
            rowCache = nextCache
        }
        listOf("Todas", "Bloqueadas", "Outras").forEachIndexed { index, label ->
            val chip = com.google.android.material.chip.Chip(activity).apply {
                id = View.generateViewId(); text = label; isCheckable = true; isChecked = index == filter
                setOnCheckedChangeListener { _, checked -> if(checked) { filter = index; queryFilter = index; renderList() } }
            }
            filters.addView(chip)
        }
        parent.addView(filters)
        text(parent, "Até 50 consultas recentes. Busca e filtros são locais. Use ⋮ à direita ou toque longo para bloquear/liberar.")
        parent.addView(list)
        search.doAfterTextChanged { querySearch = it.toString(); renderList() }
        renderList()
        return { latest -> queries = DashboardData.queries(config.provider, latest[1].json).take(50); renderList() }
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
    fun suspendReading() { job?.cancel(); refreshMode = 0; querySearch = ""; queryFilter = 0; cache.clear(); column.removeAllViews() }
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
