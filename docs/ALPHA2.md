# Alpha 2 — configuração assistida e Material 3

## Configuração pela API

1. Selecione o hostname em Gerenciamento e toque em Configurar gerenciamento.
2. Escolha API nativa. O aplicativo sugere o provedor pelo hostname, preservando configurações já salvas.
3. Toque em Criar ou gerenciar chave. O navegador abre a página oficial da conta; autenticação/MFA e criação da chave são feitas por você no próprio provedor.
4. Cole a chave e toque em Conectar e preencher automaticamente. Se a chave já estiver salva para esse hostname/provedor, deixe o campo vazio.
5. O aplicativo consulta os perfis/servidores por GET, preenche o ID quando há uma correspondência segura e pede Salvar para confirmar a configuração local.
6. Se houver ambiguidade ou nenhuma correspondência, use Selecionar perfil/servidor encontrado. O campo manual e o botão do painel oficial permanecem disponíveis.

NextDNS: reconhece hostnames como abc123.dns.nextdns.io e nome-do-dispositivo-abc123.dns.nextdns.io. A chave fica no final da página Conta. O perfil detectado só é confirmado automaticamente se estiver na lista acessível com a chave.

AdGuard DNS: consulta os servidores e dispositivos da conta padrão da chave usando a API v2 e compara o hostname exato com dns_addresses.dns_over_tls_url. O ID salvo é dns_server_id, e não o ID do dispositivo. Mais de um servidor correspondente exige seleção manual. Não escolhe automaticamente o único servidor da conta quando ele não corresponde ao hostname.

As chaves pertencem à conta. Não existe criação automática de chave, captura de senha ou alteração remota. Trocar o provedor limpa a chave digitada no formulário para impedir envio ao provedor errado. Alterar a chave cancela/invalida a descoberta anterior. Cancelar o diálogo não salva a chave nem muda a configuração.

## Links oficiais

- NextDNS, chave: https://my.nextdns.io/account
- NextDNS, perfil: https://my.nextdns.io/ID/setup (gerado somente para um ID válido).
- AdGuard, criar/gerenciar chaves: https://adguard-dns.io/en/dashboard/user-settings/api-keys
- AdGuard, painel de servidores: https://adguard-dns.io/en/dashboard/

O link AdGuard da chave é o destino indicado pela documentação oficial. Para o servidor, usa o painel oficial, pois não foi encontrado um deep link documentado e estável para cada objeto. O app preenche esse ID pela API sempre que possível. Os links nunca contêm chaves, senhas ou cookies.

As URLs sugeridas não substituem uma URL personalizada já salva. Para hostname de provedor desconhecido, o painel web continua genérico, sem uma URL inventada.

## Material UI

Material Components 1.14.0 e Compose Material 3 1.4.0, versões estáveis verificadas em 24/10/2026. Não foram adotadas versões beta para o app.

- Gerenciamento usa cartões, botões, indicadores de progresso e campos outlined Material.
- Seletores usam menus expostos Material, com rótulos persistentes.
- Apenas campos relevantes ao modo escolhido aparecem no formulário.
- Diálogos restantes, textos das listas e controles nativos do painel WebView foram padronizados com Material 3.
- Tema DayNight segue a aparência do sistema; cores dinâmicas permanecem disponíveis nos aparelhos compatíveis.
- O conteúdo dos sites dentro de WebView/Custom Tabs mantém o visual do próprio provedor.

Fontes:

- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://nextdns.github.io/api/
- https://adguard-dns.io/kb/private-dns/api/overview/
- https://adguard-dns.io/api/swagger/openapi.json
- eyalm2000/adns como referência de GET /profiles no cliente NextDNS.

## Atualização e dados

A variante release mantém o pacote io.github.billysanca.dnsmanager e a mesma chave de assinatura da Alpha 1 otimizada. versionCode 20, versionName 1.11.0-manager-alpha.2. A atualização preserva o banco de hostnames, preferências, metadados e chaves existentes. Para quem usa o debug .dev, continua sendo uma instalação separada, com transferência por exportar/importar.

A lógica do alternador, Room, backup original, Quick Settings e Shizuku não foi alterada. Nenhuma troca automática ou escrita em API foi adicionada.

## Validação

Os testes de descoberta cobrem paginação, encoding dos cursores, perfil que não é o primeiro da lista, hostname com nome de dispositivo, associação dispositivo → servidor AdGuard, ambiguidades, hostname malicioso, links sem segredos e cursor repetido. Consulte TESTES.md para os resultados finais. A interface Material, o login nos sites e as APIs com credenciais reais ainda exigem validação em aparelho.
