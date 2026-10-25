# Validação e roteiro de regressão

## Automatizado

Os comandos e seus resultados finais são registrados na entrega. O teste de integridade compara os arquivos centrais com a base original, sem inferir equivalência apenas por testes novos.

- JVM: URLs HTTPS (incluindo portas personalizadas), rejeição de credenciais embutidas e IDs maliciosos; defaults opt-in; destinos oficiais e cabeçalhos de autenticação; janelas de 24 horas; cota AdGuard separada das estatísticas; indisponibilidade explícita da cota NextDNS; rejeição de destino externo antes da rede; round-trip do backup original com rótulos, ordem, habilitação e preferências.
- Android: Room em memória com três hostnames, navegação ignorando desabilitados, persistência dos metadados, isolamento por hostname, cofre Keystore e remoção, formatação de respostas sintéticas.
- Integridade: Room, entidades, alternador, tile, Shizuku, backup e componentes de edição de hostname permanecem idênticos à base.

Nenhuma conta real de NextDNS/AdGuard foi utilizada. Não há chaves reais nos testes. Testes instrumentados e fluxos reais de login/MFA precisam de aparelho ou emulador; a execução depende de um ambiente conectado.

## Roteiro no Android 9 e versão atual

1. No original, cadastre três hostnames, desative o segundo, reordene, exporte JSON. Instale o fork ao lado e importe. Confira rótulos, ordem, modo automático configurado e exigência de desbloqueio. Compare o JSON exportado novamente.
2. Autorize Shizuku. Use o tile com tela desbloqueada/bloqueada e confirme a opção de desbloqueio. Confira os modos desligado, automático e hostname, ciclo que ignora hostnames desabilitados e atalhos estáticos.
3. Em Gerenciamento, selecione cada hostname. Todos começam sem gerenciamento. Configure modos e URLs diferentes; reinicie o app e confira a persistência. Registre `adb shell settings get global private_dns_specifier` e `private_dns_mode` antes/depois de Gerenciar: os valores devem permanecer iguais.
4. Custom Tabs: confirme uso do navegador padrão e sessão já existente, login e MFA no domínio oficial. Troque o navegador padrão e confira que o novo navegador abre. Saia pelo painel e confirme expiração normal. O app não deve receber campos de senha nem callbacks de credenciais.
5. WebView: entre no painel, reinicie o app e confira persistência quando o provedor emite cookie persistente; teste expiração e logout. Use Encerrar sessões WebView e confirme que todos os painéis incorporados saem da sessão, enquanto o navegador mantém sua própria sessão. Teste provedor que bloqueia WebView: volte a Custom Tabs.
6. Rejeite HTTP, URL com usuário:senha, esquema file/javascript, porta inválida e certificado inválido. Confirme HTTPS em porta personalizada com um painel de teste. Links HTTP e erros TLS dentro do WebView não devem carregar.
7. NextDNS: informe chave de teste e ID do perfil; compare as contagens das últimas 24h com o painel (atenção ao período exato). Confira até 50 registros e mensagem de cota indisponível. Teste chave inválida, perfil incorreto, logs desativados, modo avião e limite 429.
8. AdGuard: informe chave e ID de servidor; compare estatísticas filtradas do servidor e cota de conta inteira, incluindo conta com mais de um dispositivo. Confirme used/limit. Teste chave revogada, acesso negado e 429.
9. Confirme que Esquecer chave impede nova consulta. Reabra sem chave, troque provedor e confirme isolamento das credenciais. Verifique que exportação original não contém chaves ou cookies. Captura/recents devem ser bloqueados na tela com resultados e no diálogo da chave.
10. Inspecione tráfego do cliente nativo num ambiente de teste: apenas GET nos dois hosts oficiais; nenhum redirecionamento autenticado nem POST/PATCH/PUT/DELETE. Os painéis web têm seu próprio tráfego, decorrente das ações manuais do usuário.

## Limites conhecidos

- Primeira página dos logs (50), período fixo de 24 horas, sem polling.
- Sem seleção de conta compartilhada AdGuard, OAuth, atualização de token por senha ou cota NextDNS por endpoint interno.
- A limpeza de WebView é global ao app; Custom Tabs depende do logout do site/navegador.
- Metadados vinculados ao hostname exato não acompanham automaticamente uma renomeação e ficam fora do backup original.
- Validação de segurança e interface em dispositivos reais e com contas reais permanece necessária antes de distribuir como release estável.

## Resultado desta entrega — 24/10/2026

- `testDebugUnitTest`: 10 testes executados, 0 falhas (9 regressões novas e 1 teste original).
- `assembleDebug`: APK de teste gerado para `io.github.billysanca.dnsmanager.dev`.
- `assembleDebugAndroidTest`: APK instrumentado compilado.
- `lintDebug`: aprovado; 0 erros e 93 avisos. Inclui 67 traduções ausentes herdadas de locales incompletos do upstream, mantidas como avisos visíveis. Os demais avisos incluem dependências, texto/localização e JavaScript necessário ao WebView opcional. Nenhuma verificação de segurança foi desativada.
- `scripts/check-upstream.ps1`: aprovado, componentes centrais idênticos à base.
- `apksigner verify`: assinatura Android Debug verificada.
- `aapt dump badging`: pacote próprio, minSdk 28, targetSdk 37, Activity inicial preservada.
- ADB: nenhum aparelho conectado. Os testes instrumentados foram compilados, mas NÃO executados. Logins reais, MFA, expiração de cookies, tile e Shizuku em aparelho precisam seguir o roteiro acima.

APK debug destinado à avaliação. Não representa certificação de produção nem validação de credenciais reais dos provedores.

## Alpha 2 — 24/10/2026

- Build: testDebugUnitTest, assembleRelease, assembleDebugAndroidTest e lintRelease concluídos com sucesso.
- JVM: 18 testes, 0 falhas. Inclui 8 novos testes de descoberta e os 10 testes anteriores.
- Lint release: 0 erros e 108 avisos (incluindo traduções incompletas herdadas, textos de interface e dependências). Nenhuma checagem de segurança foi desativada.
- Integridade: scripts/check-upstream.ps1 aprovado; alternador, dados Room, backup, Quick Settings e Shizuku idênticos à base.
- APK release: 5.015.150 bytes, pacote io.github.billysanca.dnsmanager, versionCode 20, versionName 1.11.0-manager-alpha.2. Modo não depurável, alinhamento e assinatura verificados.
- Certificado release SHA-256: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6 (mesmo da Alpha 1 otimizada).
- Instrumentação compilada, sem execução em aparelho. APIs com contas reais e revisão visual no Android 9/atual, claro/escuro, fonte grande e teclado ainda precisam de validação.

### Roteiro adicional

1. Atualize a Alpha 1 release com a Alpha 2 sem desinstalar. Confira hostnames, ordem, configurações e chaves existentes.
2. Configure NextDNS usando hostname com ID e nome de dispositivo. Confira a sugestão do provedor, link Conta e preenchimento após Conectar.
3. Configure AdGuard usando um hostname DoT de dispositivo. Confira que o ID preenchido corresponde ao servidor, não ao dispositivo. Confira o link direto para API keys.
4. Teste conta com vários perfis/servidores e hostname sem correspondência: deve exigir seleção explícita ou preenchimento manual. Nenhum servidor deve ser escolhido apenas por ser o único resultado.
5. Troque o provedor e a chave durante uma consulta: os dados de descoberta anteriores devem ser invalidados e a chave digitada deve ser limpa ao trocar de provedor. Cancelar não deve salvar.
6. Use uma URL web personalizada já salva e confira que ela é preservada. Um hostname desconhecido não deve receber automaticamente um painel NextDNS.
7. Confira menus expostos, campos, botões, cartões, progresso e diálogos Material com tema claro/escuro, fonte grande e leitor de tela.
8. Registre os valores de private_dns_specifier e private_dns_mode antes/depois de configurar/consultar: devem permanecer iguais.

## Nexo DNS Alpha 3 — 25/10/2026

- Build final: testDebugUnitTest, assembleRelease, assembleDebugAndroidTest e lintRelease concluídos com sucesso.
- JVM: 24 testes, 0 falhas. Os seis novos testes cobrem estatísticas, cotas, ausência de dados, respostas inválidas, filtros de bloqueio e cache por hostname/perfil com expiração e descarte.
- Lint release: 0 erros e 107 avisos. Nenhuma regra de segurança foi desativada. A chamada de dimensionamento de texto foi ajustada para a API pública TextViewCompat.
- Integridade do código do alternador, Room, backup, Quick Settings e Shizuku: aprovada por scripts/check-upstream.ps1.
- APK otimizado sem debug: Nexo-DNS-alpha3-release.apk, 5.101.447 bytes, pacote io.github.billysanca.dnsmanager, versionCode 21, versionName 1.12.0-alpha.3, rótulo Nexo DNS.
- Assinatura e alinhamento verificados. Certificado SHA-256 igual ao das versões release anteriores: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6.
- Testes instrumentados compilados, incluindo FourTabsRegressionTest. Não executados: nenhum aparelho estava conectado ao ADB.
- Revisão visual no aparelho, fluxo completo de colar chave → Salvar → Estatísticas e APIs com credenciais reais permanecem pendentes. Não se declara validação visual final sem essa execução.

### Roteiro específico das quatro abas

1. Atualize a Alpha 2 release sem desinstalar. Confira o nome Nexo DNS, os endereços, a ordem, os hostnames desativados e as chaves existentes.
2. Abra DNS e confira a lista original, editor, ordenação, importação/exportação e botão de adicionar. Use Quick Settings e Shizuku para verificar os fluxos originais.
3. Com API configurada, abra Estatísticas e Consultas: o carregamento deve iniciar sem botão Gerenciar. Ao alternar entre as duas, a consulta recente pode ser reutilizada por até 60 segundos. Atualizar dados força a rede.
4. Confira contagens, distribuição, cotas de conta AdGuard e o link de consumo NextDNS. O gráfico de distribuição não deve ser interpretado como série temporal.
5. Em Consultas, busque um domínio e use Todas/Bloqueadas/Outras. Os filtros são locais à primeira página de até 50 registros.
6. Em Configurações, cole uma chave e toque em Salvar: a descoberta deve acontecer automaticamente. Havendo correspondência única, a configuração local é salva e Estatísticas abre; ambiguidades mostram a lista. Cancelar a seleção não deve salvar.
7. Saia e volte ao aplicativo: o snapshot de consultas deve ter sido descartado e a aba ativa deve carregar novamente. Não deve haver polling em segundo plano.
8. Anote private_dns_mode e private_dns_specifier antes/depois de navegar e consultar: não devem mudar. A única troca permanece nos controles originais do alternador.
9. Confira fonte grande, tema claro/escuro, leitores de tela e botões da navegação inferior. O botão Voltar nas abas de gestão deve retornar à aba DNS.
