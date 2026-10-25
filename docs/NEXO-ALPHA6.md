# Nexo DNS — Alpha 6

## Gestos na primeira aba

- Um toque seleciona o perfil compartilhado pelas demais abas e atualiza o cabeçalho; não abre o editor.
- Toque longo abre a edição do hostname. Editar outro registro não muda automaticamente a seleção compartilhada.
- Ordenação permanece pela alça de arrastar; o toque longo da linha foi reservado à edição para evitar conflito entre gestos.
- Escolher o perfil de gerenciamento não altera sozinho o DNS do Android. Quick Settings, Shizuku e os controles originais continuam responsáveis pelo DNS do sistema.

## Consultas compactas e Material

As consultas passam de cartões altos com botões completos a linhas compactas: domínio em destaque, horário/status e dispositivo quando disponível. O domínio pode ser abreviado visualmente em telas pequenas; a confirmação de regra mostra seu nome completo.

O menu ⋮ fica à direita de cada célula, com área de toque de 48 dp e descrição para acessibilidade. Não há botão textual Ações do domínio. Toque longo também abre o mesmo menu, ancorado ao botão. Bloqueio/liberação nativos do NextDNS e a exceção explicada do AdGuard permanecem.

As linhas alternam colorSurfaceContainerLow e colorSurfaceContainer do tema e têm MaterialDivider entre requisições. O estado bloqueado usa a cor semântica de erro, respeitando tema claro/escuro. Botão de ícone, menus, campos, chips, indicadores e gráficos de distribuição usam os componentes/temas Material existentes (Material Views 1.14.0, Compose Material 3 1.4.0). Não foi introduzida uma biblioteca de gráficos com aparência divergente.

## Atualizar automaticamente

Nas páginas nativas de dados:
- Atualizar agora: consulta manual.
- Manual: sem atualização periódica.
- Contínuo: intervalo mínimo de 5 segundos após concluir a consulta anterior.
- Intervalos predefinidos: 15 segundos, 30 segundos ou 1 minuto.

Contínuo usa polling da API, não streaming/push em tempo real. A disponibilidade de novos registros depende do provedor. São lidos os endpoints atuais de estatísticas, consultas e cotas; o recurso aumenta consumo de API/rede. Logs continuam limitados aos últimos 50 registros da janela atual.

A busca e os chips de filtro permanecem durante atualizações periódicas. A posição de rolagem é preservada quando possível. O loop aguarda a consulta terminar antes de agendar a próxima, pausa para interações de menu/confirmação e não dispara escritas automáticas. Qualquer erro de consulta, inclusive HTTP 429, retorna ao modo manual.

Sair do app ou voltar à aba DNS cancela o loop e volta ao modo manual. Nas demais abas sem dados, não há polling. Nenhuma atualização acontece com o app em segundo plano. Painéis web continuam sob o comportamento do próprio site, sem recargas automáticas impostas pelo app.

## Atualização e preservação

versionName 1.12.0-alpha.6, versionCode 24. Mesmo pacote io.github.billysanca.dnsmanager e certificado release: atualizar sem desinstalar. Sem migração ou alteração do banco de hostnames. Chaves, URLs de gestão, sessões e seleção compartilhada permanecem.

A lógica original de alternância, múltiplos hostnames, backups, Quick Settings e Shizuku permanece. O adapter de lista foi alterado apenas para acrescentar o callback de toque longo; scripts/check-upstream.ps1 verifica essa exceção e a integridade restante.

Consulte TESTES.md. Revisão visual em aparelho, gestos físicos, atualização periódica com contas reais e consumo de API continuam sujeitos à validação em dispositivo.
