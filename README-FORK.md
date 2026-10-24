# DNS Manager — fork de teste

Fork de karasevm/PrivateDNSAndroid, base `189afad221b63b06e8f1f9c429aac91ef4f3bb0e`.
Referência de arquitetura: eyalm2000/adns, `374e5b1193491b5b12ed50fb761115bf2ceff020`.
O adns foi consultado para separação de clientes/autenticação. Não foram copiados seus fluxos de cookies, login por senha, endpoints internos ou funções de escrita.

A Alpha 2 adiciona preenchimento assistido por API e componentes Material 3. Veja [o passo a passo e links oficiais](docs/ALPHA2.md).

## Instalar e preservar dados

O APK debug usa `io.github.billysanca.dnsmanager.dev` (release: `io.github.billysanca.dnsmanager`), Android 9 ou posterior.
Ele pode coexistir com o original. Exporte os hostnames no aplicativo original e importe o JSON pelo menu do fork. Não desinstale o original antes de conferir a importação. O Android impede que outro pacote leia automaticamente os dados privados do original.

O banco Room, seu esquema, a migração antiga de preferências, a ordem, os rótulos e o campo habilitado foram mantidos. Os metadados de gerenciamento são separados, associados ao texto exato do hostname. Duplicatas do mesmo hostname compartilham essa configuração. Ao renomear um hostname, configure o novo endereço; a associação antiga não é apagada. O backup original permanece compatível e não inclui chaves, cookies nem metadados de gerenciamento. Configure o gerenciamento novamente em outro aparelho.

Para alternar DNS, autorize Shizuku como no original ou execute:

```sh
adb install -r DNS-Manager-test.apk
adb shell pm grant io.github.billysanca.dnsmanager.dev android.permission.WRITE_SECURE_SETTINGS
```

Adicione o tile nas configurações rápidas. Gerenciar nunca chama o alternador de DNS. O projeto não contém agendamento nem troca automática.

## Gerenciamento universal

Na aba Gerenciamento selecione o hostname, toque em Editar gerenciamento e escolha:

- Sem gerenciamento: padrão para todos os hostnames existentes.
- Painel web: informe uma URL HTTPS. Custom Tabs é o padrão e usa o navegador padrão, suas sessões e seu gerenciador de senhas. Para usar outro navegador, altere o navegador padrão do Android. Se ele não suportar Custom Tabs, o intent pode abrir uma aba normal.
- API nativa: selecione NextDNS ou AdGuard DNS, informe o identificador e uma chave de API emitida no site oficial. Deixe a chave em branco para manter a chave atual. Use Esquecer chave para removê-la localmente; revogação remota deve ser feita no site do provedor.

Gerenciar abre a interface do hostname selecionado sem alterar o DNS ativo. Ao abrir pela primeira vez, o seletor prioriza o hostname atualmente ativo, se cadastrado.

A URL pode ser alterada para qualquer provedor. HTTP, portas inválidas e credenciais embutidas na URL são rejeitados. Portas HTTPS personalizadas são aceitas. Não inclua segredos nem tokens na URL. Custom Tabs é preferível para login com MFA/passkeys. Alguns provedores rejeitam WebView ou exigem cookies de terceiros; nesse caso use Custom Tabs. Não há tentativa de contornar políticas do provedor.

WebView é opcional: aceita cookies próprios, mantém cookies persistentes de acordo com as regras de expiração do servidor e salva DOM storage. Cookies de sessão podem expirar quando o processo termina. Não há JavaScript bridge, leitura de campos de senha, exportação de cookies ou compartilhamento de chaves de API com o painel. HTTP, conteúdo misto, acesso a arquivos e erros TLS são bloqueados. Não há suporte a pop-ups, downloads, câmera ou upload de arquivos nessa versão.

Encerrar sessões WebView apaga cookies e armazenamento web de TODOS os painéis incorporados do app, após confirmação explícita. Custom Tabs não permite ao app apagar os cookies do navegador: use a saída do próprio site ou as configurações do navegador. Apagar cookies não revoga sessões no servidor.

As ações feitas manualmente pelo usuário dentro de um painel continuam sob controle do próprio site. A restrição somente leitura aplica-se ao cliente nativo: nenhum endpoint de escrita é implementado.

## APIs oficiais e escopo de leitura

Fontes verificadas em 24/10/2026:

- https://nextdns.github.io/api/
- https://adguard-dns.io/kb/private-dns/api/overview/
- https://adguard-dns.io/api/swagger/openapi.json (v1.17.3)

NextDNS: `X-Api-Key`, `GET /profiles/{id}/analytics/status?from=-24h` e `GET /profiles/{id}/logs?from=-24h&limit=50`. Encontre a chave em https://my.nextdns.io/account e o ID na configuração do perfil. A API pública documentada não fornece consumo/limite de conta: a interface mostra indisponibilidade. Não usa endpoint interno de conta e não confunde estatísticas do perfil com cota global.

AdGuard DNS (serviço privado em nuvem, não AdGuard Home): `Authorization: ApiKey ...`, `GET /oapi/v1/stats/time`, `GET /oapi/v1/query_log` (últimas 24h, filtro dns_servers, até 50 registros), `GET /oapi/v1/account/limits`. Use o ID do servidor, não o ID do dispositivo nem o hostname DoT. A cota é da conta inteira e mostra used/limit como retornados pela API. Esta versão consulta a conta padrão da chave; seleção de contas compartilhadas não está implementada.

Os registros mostram apenas a primeira página de até 50 consultas. Não existe polling automático nem paginação automática. Registros desativados, retenção expirada e limites do plano podem reduzir os dados disponíveis.

## Segurança e arquitetura

`management/ManagementConfig` e `ManagementStore` implementam associação genérica, sem depender das APIs. `ManagementWebActivity` cuida do WebView. `DnsProvider` define o contrato de leitura; `NextDnsProvider` e `AdGuardProvider` ficam em arquivos separados. Para adicionar uma API, implemente o contrato, registre em Providers e no seletor nativo. O gerenciamento web não precisa ser alterado.

`OfficialReadTransport` só executa GET por HTTPS nos dois hosts oficiais, com redirecionamentos desativados, timeouts e limite de resposta de 2 MB. Erros 401/403 pedem atualizar a chave; 429 pede aguardar. Não há renovação por senha nem chamada de escrita para autenticação. Dados de consultas são mantidos somente na tela e descartados quando o app sai de primeiro plano. A tela de gerenciamento bloqueia screenshots e recents.

`ApiKeyVault` usa AES-GCM com chave não exportável no Android Keystore. Ciphertext é salvo atomicamente em noBackupFilesDir por hostname/provedor. Backups automáticos estão desativados. Uma chave perdida/inválida exige nova entrada. Não há logs HTTP, analytics ou persistência de resultados. O APK debug é para testes e deve ser substituído por uma versão release assinada antes de uso permanente; o isolamento não protege um aparelho comprometido.

## Compilar e testar

Use JDK 21, SDK Android 37.0 e build-tools 36.0.0. Informe sdk.dir em local.properties ou ANDROID_HOME; o Gradle wrapper baixa a versão fixada do projeto original.

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest
```

O segundo comando exige aparelho/emulador conectado. `scripts/check-upstream.ps1` verifica que alternador, Room, Shizuku e tile continuam idênticos à base. Os testes JVM cobrem política de URLs, injeção de ID, opt-in, hosts/autenticação dos provedores e período/cota. Os testes Android cobrem persistência de metadados, ordem/habilitação de múltiplos hostnames, cofre e apresentação com respostas sintéticas. Consulte docs/TESTES.md para resultados e roteiro de validação real.
