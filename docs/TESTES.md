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

## Alpha 4 — roteiro adicional

- Com DNS privado desligado/automático ou hostname ativo não cadastrado, Estatísticas e Consultas não podem usar o primeiro perfil da lista.
- Ativar NextDNS/AdGuard cadastrado, abrir ambas as abas e confirmar API automática, ausência de seletor de modo e DNS ativo visível. Remover a chave e confirmar pedido de conexão.
- Selecionar outro hostname em Configurações: as abas de dados devem continuar usando o DNS do Android.
- Sem hostnames cadastrados, verificar acesso às preferências e importação na quarta aba.
- Confirmar ausência da engrenagem e do menu no cabeçalho. Testar importação/exportação por arquivo, clipboard e compartilhar; privacidade/licenças.
- Provedor web: salvar URLs HTTPS diferentes para Estatísticas/Consultas; verificar cada destino, login/MFA e cookies conforme política do site. Retornar não pode abrir loop. URL específica vazia usa painel geral; modo Sem gerenciamento não abre páginas.
- NextDNS em perfil de teste: cancelar confirmação não envia regra; confirmar bloqueio/liberação afeta somente o domínio/perfil exibido; regra oposta impede escrita; regra já ativa não duplica; erros de permissão/rede/resposta não aparecem como sucesso. Conferir listas no painel, histórico inalterado e efeito em consultas futuras considerando cache.
- AdGuard: ação de regras abre painel e não faz PUT de listas.
- Atualizar Alpha 3 sem desinstalar e verificar hostnames, ordem/habilitados, chave, associações, sessões, Quick Settings e Shizuku.
- Repetir com TalkBack, fonte 200%, modo claro/escuro e telas pequenas.

Sem contas reais e sem aparelho conectado, os itens de UI/rede acima continuam pendentes. As regressões JVM usam transportes falsos: não alteram contas nem substituem um teste ponta a ponta.

### Resultado final Alpha 4 — 25/10/2026

- testDebugUnitTest, assembleRelease, assembleDebugAndroidTest e lintRelease: BUILD SUCCESSFUL.
- JVM: 33 testes, 0 falhas/erros. Domínios e roteamento: 9; descoberta: 8; gestão/backups: 9; dashboard: 6; exemplo original: 1.
- Novas regressões: IDN e rejeição de URLs/IPs/regras; URLs específicas com fallback; DNS desligado/automático e hostname não cadastrado; API obrigatória sem sobrescrever metadados; conflito sem escrita; idempotência; confirmação posterior ao POST; ausência de retry após resultado incerto; erro de leitura/ID inválido sem escrita.
- Lint release: 0 erros, 117 avisos (incluindo níveis warning padrão do SARIF). Avisos não foram suprimidos para aprovar a versão.
- Integridade do núcleo original: PASS em scripts/check-upstream.ps1; git diff --check aprovado.
- APK: Nexo-DNS-alpha4-release.apk, 5.138.647 bytes, versionCode 22, versionName 1.12.0-alpha.4. BuildConfig.DEBUG=false; aapt sem application-debuggable. Android mínimo 28 (9).
- Assinatura v3 e alinhamento confirmados. Certificado SHA-256: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6.
- SHA-256 APK: dd5b0f5af313c7be2dc4d63faef15f9ba9884ea7404185cf57360bed106f111f.
- ADB sem aparelhos. Testes instrumentados compilados, não executados. UI e APIs com contas reais continuam pendentes. Nenhuma conta recebeu regras durante os testes automatizados.

## Alpha 5 — seleção compartilhada e menu de consultas

- JVM: 35 testes, 0 falhas/erros. Os dois novos testes verificam prioridade da escolha na primeira aba sobre o DNS do Android, uso com Android DNS desligado, renomeação pelo mesmo ID, exclusão sem escolher outra conta silenciosamente e fallback inicial para o DNS ativo.
- Roteiro em aparelho: tocar no hostname na primeira aba, fechar o editor e conferir o mesmo perfil no cabeçalho e em Estatísticas, Consultas e Configurações. Nenhuma dessas abas pode oferecer seletor de DNS.
- Selecionar A na primeira aba com Android configurado para B: as abas devem mostrar A sem mudar private_dns_mode/private_dns_specifier.
- Fechar/reabrir o app: seleção deve persistir. Renomear o registro deve manter a escolha; excluir deve pedir nova seleção.
- Em Consultas, botão Ações do domínio e toque longo no cartão devem apresentar o mesmo menu. NextDNS bloquear/liberar deve executar na interface nativa, com confirmação, sem abrir navegador. Cancelar não envia regra. Conferir indicadores e lista remota em perfil de teste.
- No AdGuard, o menu deve explicar a limitação antes de abrir o painel, respeitando Custom Tabs/WebView salvo.
- Os testes de UI e com contas reais continuam pendentes por ausência de aparelho/credenciais; não se declara validação visual sem execução.

### Resultado final Alpha 5 — 25/10/2026

- testDebugUnitTest, assembleRelease, assembleDebugAndroidTest e lintRelease: BUILD SUCCESSFUL.
- JVM: 35 testes, 0 falhas/erros. Lint: 0 erros, 118 avisos; sem suprimir regras para aprovar o build.
- Núcleo original de DNS, banco/hostnames, backups, Quick Settings e Shizuku: PASS em scripts/check-upstream.ps1.
- APK release otimizado: 5.138.647 bytes, pacote io.github.billysanca.dnsmanager, versionCode 23, versionName 1.12.0-alpha.5; sem application-debuggable.
- Assinatura v3 e alinhamento verificados; certificado SHA-256 preservado: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6.
- SHA-256 APK: 2c711f5e2dd82fdad74b8b5d80ef594743e0ee11ec8565808bbd15b6a50cd242.
- Testes instrumentados compilados, não executados em aparelho. UI, login/MFA web e APIs com contas reais continuam pendentes.

## Alpha 6 — gestos, linhas compactas e atualização

- Verificar toque na primeira aba: seleciona o perfil, sem abrir editor. Toque longo: abre editor e não muda silenciosamente a seleção. Arrastar continua funcionando pela alça.
- FourTabsRegressionTest inclui teste instrumentado de dispatch separado de clique e toque longo. Compilar não equivale a executar em aparelho.
- RefreshPolicyRegressionTest cobre limites das cadências: modos desconhecidos/manual não criam polling; modos ativos nunca usam menos de 5 s nem mais de 60 s.
- Consultas: conferir linhas mais baixas, menu ⋮ à direita com alvo de 48 dp, ausência do botão textual, mesma ação por toque longo, divisórias Material e fundos alternados em tema claro/escuro.
- Testar TalkBack, fontes 200%, domínios longos e dispositivos pequenos. Menu e confirmação precisam identificar o domínio completo.
- Na API de teste, habilitar cada intervalo e verificar novas leituras somente com a página nativa aberta. Buscar e selecionar chips: atualização periódica mantém texto/filtro e rolagem quando possível.
- Abrir menu/confirmação durante atualização: interação não deve ser interrompida pela reconstrução das linhas.
- Sair do app ou voltar à aba DNS: parar consultas e retornar ao modo Manual. Configurações e provedores web não devem gerar polling nativo.
- Simular 401/403/429, falha de rede e resposta inválida: atualização automática deve parar, voltar ao modo Manual e mostrar erro sem laço de retry.
- Confirmar que polling nunca envia regras ou altera DNS do Android. Bloqueio/liberação continua exigindo confirmação individual.
- Validação visual, gestos físicos, continuidade real e cotas do provedor permanecem pendentes sem aparelho/credenciais reais.

### Resultado final Alpha 6 — 25/10/2026

- testDebugUnitTest, assembleRelease, assembleDebugAndroidTest e lintRelease: BUILD SUCCESSFUL.
- JVM: 36 testes, 0 falhas/erros. Lint: 0 erros, 119 avisos; sem supressões para aprovar a versão.
- Teste instrumentado de toque/toque longo compilado junto à bateria existente; não executado por ausência de aparelho.
- Integridade do núcleo original: PASS. O adapter difere apenas nas duas adições autorizadas do callback de toque longo; ordenação por alça permanece.
- APK otimizado sem debug: 5.138.703 bytes; pacote io.github.billysanca.dnsmanager; versionCode 24; versionName 1.12.0-alpha.6.
- Assinatura v3 e alinhamento verificados; certificado SHA-256: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6.
- SHA-256 APK: ea9c43b166592d842ed38edc9a33e12120ddedf6b74c3f8563cd34bd45a997e2.
- Revisão visual, gestos físicos e atualização periódica com contas reais permanecem pendentes.

## Alpha 7 — revisão de UX, temas e Tempo Real

### Resultado final — 25/10/2026

- testDebugUnitTest, assembleRelease, assembleDebug, assembleDebugAndroidTest e lintRelease: BUILD SUCCESSFUL.
- JVM: 45 testes, 0 falhas/erros. A bateria adiciona decoder SSE, limites/deduplicação, cursor, buffer de 50 registros, leituras separadas de consultas/estatísticas, fallback de tema e cancelamento real da coleta com uma conexão HTTPS falsa.
- LiveCancellationRegressionTest verifica fechamento da conexão ao cancelar, autenticação em cabeçalho e rejeição de perfil inválido antes de abrir conexão. Nenhuma chave ou conta real é usada.
- Lint release: 0 erros, 127 avisos; sem supressões para aprovar o build.
- Integridade de alternância, dados/hostnames, backups, Quick Settings e Shizuku: PASS em scripts/check-upstream.ps1. O adapter de UI é uma alteração deliberada para seleção/gestos/cores; o núcleo protegido permanece igual ao upstream.
- APK release otimizado, não debuggable: 5.159.239 bytes. Pacote io.github.billysanca.dnsmanager; versionCode 25; versionName 1.13.0-alpha.7.
- Assinatura v3 e alinhamento verificados. Certificado SHA-256 preservado: 6cc020643edead921513f6869a15f31f504baba19e7056491a7620af58e256a6.
- SHA-256 APK: 36607faecc543f312057a818cc89f6d7228744f37fdaacf8d0d6eb541ad0f433.
- Manifesto e arquivos DEX foram conferidos: DesignPreviewActivity e a credencial sintética não estão no release. Fixtures com dados sintéticos ficam somente no source set debug.
- Testes instrumentados e DesignVisualTest compilados, NÃO executados. Android Emulator 37.2.12 e imagem Android 35 AOSP ATD foram preparados no workspace; o diagnóstico de aceleração retornou 6. Tentativas sem aceleração encerraram com código 1 e não disponibilizaram dispositivo ADB. Configurações de virtualização/segurança do Windows não foram alteradas.
- Revisão visual em Android, TalkBack, gestos físicos e contas reais permanecem pendentes. Nenhuma captura de Android validada foi produzida nesta entrega.

### Executar em aparelho/emulador compatível

1. Usar uma instalação debug separada (.dev), sem remover ou limpar a instalação release.
2. Executar ./gradlew testDebugUnitTest connectedDebugAndroidTest com Android SDK/JDK configurados. Os testes usam fixtures; não fornecer chaves reais para capturar UI.
3. DesignVisualTest abre DNS em claro/escuro/OLED, Estatísticas em claro, Consultas em escuro e Configurações em OLED/cores dinâmicas. Verifica menu ausente, resultados sintéticos e superfície preta no modo OLED. Capturas ficam no diretório externo screenshots do aplicativo debug.
4. Repetir com fonte ampliada e TalkBack. Verificar rótulos da barra inferior, perfil selecionado, alvo de menu de 48 dp e texto completo em confirmação.
5. Testar o app principal: um toque seleciona, toque longo edita, alça ordena. Alterar tema deve manter aba/hostnames/chaves/sessões. Preferências do alternador, importação/exportação, privacidade e licenças devem ser alcançáveis apenas pela aba Configurações, sem reticência no Toolbar.
6. Estatísticas não pode mostrar controles de atualização nem criar timer. Consultas deve ler somente logs ao atualizar. Abrir Estatísticas não pode ler logs.
7. Em um perfil NextDNS de teste, entrar em Tempo Real: ler snapshot e continuar pelo cursor SSE, sem duplicar ID. Parar ao trocar de aba/sair. Interrupção, MIME incorreto, HTTP 401/403/429 e payload inválido devem retornar a Manual preservando contexto.
8. No AdGuard, Tempo Real deve identificar o polling de 5 s, sem chamar estatísticas/cotas no loop. Conferir 15/30/60 s.
9. Bloquear/liberar continua exigindo confirmação individual. Tema, navegação e atualização nunca alteram DNS nem regras automaticamente.
10. Atualizar sobre Alpha 6 sem desinstalar e conferir dados e certificado.

Veja UX-REVIEW-ALPHA7.md para a análise e as referências primárias.
