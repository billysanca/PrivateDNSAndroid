package ru.karasevm.privatednstoggle.ui

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import ru.karasevm.privatednstoggle.R
import ru.karasevm.privatednstoggle.databinding.ActivityMainBinding
import ru.karasevm.privatednstoggle.management.*
import ru.karasevm.privatednstoggle.model.DnsServer

/** Synthetic visual fixture, debug source set only. No account credentials or network. */
class DesignPreviewActivity : NexoActivity() {
    private var panel: ManagementPanel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val page = intent.getIntExtra("page", 0)
        val servers = listOf(
            DnsServer(1001, "fixture-abcdef.dns.nextdns.io", "Pessoal · NextDNS"),
            DnsServer(1002, "family.dns.example", "Família"),
            DnsServer(1003, "one.one.one.one", "Cloudflare"))
        binding.topAppBar.title = listOf("Nexo DNS", "Estatísticas", "Consultas", "Configurações")[page]
        binding.topAppBar.subtitle = "Perfil selecionado · Pessoal"
        binding.bottomNavigation.selectedItemId = intArrayOf(R.id.nav_dns, R.id.nav_stats, R.id.nav_queries, R.id.nav_settings)[page]
        binding.emptyView.visibility = View.GONE
        binding.emptyViewHint.visibility = View.GONE
        if(page == 0) {
            val adapter = ServerListRecyclerAdapter(true)
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = adapter
            adapter.submitList(servers); adapter.selectedId = 1001
            adapter.onItemClick = { adapter.selectedId = it }
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.floatingActionButton.visibility = View.GONE
            panel = ManagementPanel(this, chosenHost = { servers[0].server },
                providerFactory = { FixtureProvider() }, credentials = { _, _ -> "synthetic-fixture" })
            val params = ConstraintLayout.LayoutParams(0, 0).apply {
                topToBottom = R.id.topAppBarLayout; bottomToTop = R.id.navigation_card
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID; endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            binding.root.addView(panel!!.view, params)
            panel!!.update(servers, page)
            binding.navigationCard.bringToFront()
        }
    }
    override fun onStop() { panel?.suspendReading(); super.onStop() }
    private class FixtureProvider : DnsProvider {
        override val id = "nextdns"
        override fun discover(hostname: String, key: String) = DiscoveryResult(emptyList(), null)
        override fun queries(profile: String, key: String, now: Long): ReadSection = logs()
        override fun statistics(profile: String, key: String, now: Long) = read(profile, key, now)
        override fun read(profile: String, key: String, now: Long) = listOf(
            ReadSection("Estatísticas", """{"data":[{"status":"default","queries":18420},{"status":"blocked","queries":6480},{"status":"allowed","queries":1200}]}"""),
            logs(), ReadSection("Cota", "Indisponível"))
        private fun logs() = ReadSection("Consultas", """{"data":[
            {"domain":"ads.example.com","timestamp":"2026-10-25T10:32:07Z","status":"blocked","device":{"name":"Meu celular"}},
            {"domain":"video.example.org","timestamp":"2026-10-25T10:32:06Z","status":"default","device":{"name":"Meu celular"}},
            {"domain":"news.example.net","timestamp":"2026-10-25T10:32:05Z","status":"allowed","device":{"name":"Notebook"}},
            {"domain":"metrics.example.com","timestamp":"2026-10-25T10:32:04Z","status":"blocked","device":{"name":"Notebook"}},
            {"domain":"cdn.example.org","timestamp":"2026-10-25T10:32:03Z","status":"default","device":{"name":"Meu celular"}},
            {"domain":"updates.example.net","timestamp":"2026-10-25T10:32:02Z","status":"default","device":{"name":"Meu celular"}}
        ]}""")
    }
}
