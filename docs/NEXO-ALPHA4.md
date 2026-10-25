# Nexo DNS — Alpha 4

## Abas que acompanham o DNS ativo

DNS continua sendo a lista original. Estatísticas e Consultas usam exclusivamente o hostname atualmente configurado no Android em modo DNS privado, desde que cadastrado. Nenhum endereço é ativado pela navegação. Se não houver DNS cadastrado ativo, as abas mostram orientação para a primeira aba.

Para NextDNS e AdGuard reconhecidos, API nativa é obrigatória: os seletores de modo e provedor desaparecem. Configurações antigas permanecem armazenadas, mas a rota efetiva passa a ser API. Uma chave ou associação ausente pede conexão; não abre um painel silenciosamente. Hostnames personalizados com API configurada continuam usando essa API.

Configurações mantém seu seletor para editar qualquer hostname sem mudar o DNS do Android. A engrenagem e o menu superior foram removidos da apresentação. Preferências originais, importação de arquivo/clipboard, exportação de arquivo/clipboard/compartilhamento, privacidade e licenças ficam na quarta aba, inclusive antes de cadastrar um DNS.

## Provedores web

Há URLs HTTPS independentes para painel geral, Estatísticas e Consultas. O modo Sem gerenciamento não abre páginas. O modo Painel web abre a página da aba automaticamente em Custom Tabs (padrão) ou WebView. Se a página específica estiver vazia, usa o painel geral e avisa. Sem URL válida, pede configuração. O app não pode adivinhar URLs privadas de provedores arbitrários.

Voltar do navegador não reabre o painel em loop. Um botão permite reabrir; entrar novamente na aba abre novamente. Login, MFA, expiração e senhas permanecem com o provedor/navegador. O aplicativo não captura senhas. Encerrar sessões incorporadas continua disponível.

## Bloqueio e liberação no NextDNS

Cada consulta com domínio válido oferece Bloquear domínio e Liberar domínio. A confirmação identifica domínio, hostname e ID do perfil e explica o alcance: todos os dispositivos do perfil, inclusive subdomínios conforme regras do provedor. Cancelar não envia nada.

A operação normaliza IDN/punycode e rejeita URLs, IPs, curingas, sintaxe de regras e injeções. Consulta as listas do perfil com paginação limitada e:
- Se existir regra ativa para o mesmo domínio na lista oposta, interrompe e orienta revisar o painel. Não remove regras.
- Se a regra desejada já estiver ativa, conclui sem escrever.
- Caso contrário, faz um único POST contendo id e active=true para allowlist ou denylist.
- Verifica novamente a lista. HTTP 2xx com errors não é sucesso.
- Mostra envio, sucesso confirmado ou falha com símbolos e mensagem. Não repete automaticamente uma escrita incerta.

O registro histórico conserva seu status original: adicionar regra não muda o passado. Regras, precedência e caches podem influenciar consultas futuras. A operação não troca o DNS do Android. Não há exclusão de listas, substituição em massa, reconciliação automática de regras ou escrita agendada em segundo plano. A chave precisa de permissão de escrita para esta ação.

No AdGuard, a API oficial documenta PUT da lista de regras inteira em settings, sem operação atômica de adição individual nem controle de versão documentado. Esta Alpha oferece Gerenciar regra no AdGuard no painel oficial, evitando substituir regras concorrentes. Estatísticas, logs e cotas AdGuard continuam nativos.

As escritas limitadas no NextDNS atendem à etapa solicitada pelo usuário após o modo inicial somente leitura. Nenhuma outra alteração remota foi habilitada.

## Interface e atualização

DNS ativo visível nas duas abas; atualização indica horário do último carregamento bem-sucedido; consultas têm indicação de bloqueio e resultado da ação. Tema Material 3, quatro abas e componentes anteriores permanecem.

Versão 1.12.0-alpha.4, versionCode 22. Mesmo pacote io.github.billysanca.dnsmanager e certificado release: atualização sobre Alpha 3 preserva dados. Metadados web novos são opcionais e compatíveis com os antigos. Room, múltiplos hostnames, backups originais, Quick Settings e Shizuku não mudaram.

## Referências oficiais

- NextDNS: https://nextdns.github.io/api/ (arrays/nested endpoints e errors em HTTP 200).
- AdGuard DNS: https://adguard-dns.io/api/swagger/openapi.json (DNSServerSettingsUpdate / UserRulesSettingsUpdate).

Leia TESTES.md para resultados e limitações. Não foram usadas credenciais reais de provedores nem feitas alterações em contas de teste durante a construção.
