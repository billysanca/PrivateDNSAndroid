# Nexo DNS — Alpha 5

## Um único perfil, escolhido na primeira aba

Toque em um hostname na lista DNS: esse registro passa a ser o perfil compartilhado por Estatísticas, Consultas e Configurações. O editor original continua abrindo no mesmo toque. Fechá-lo sem alterações mantém o perfil escolhido. O subtítulo do cabeçalho identifica o perfil; nenhuma outra aba apresenta seletor de DNS.

A seleção fica salva por ID do registro, separada do banco original. Assim acompanha renomeações e reinício do app. Se o registro escolhido for removido, pede nova escolha e não usa silenciosamente outra conta. Antes da primeira escolha explícita, o DNS privado ativo cadastrado é a referência inicial.

Escolher um perfil para consultar/gerenciar NÃO ativa esse DNS no Android. As alterações do DNS do sistema continuam exclusivamente nos controles originais, Quick Settings e Shizuku. Isso permite gerenciar um perfil mesmo com DNS do sistema desligado ou diferente. O perfil exibido no cabeçalho é a referência compartilhada, e não uma comprovação de conexão do Android.

## Ações no próprio aplicativo

Nas consultas, toque em Ações do domínio ⋮ ou mantenha pressionado o cartão. Para NextDNS, o menu oferece Bloquear domínio e Liberar domínio, realizados inteiramente pela interface nativa e API oficial. Nenhum navegador é aberto nessas ações. O usuário confirma domínio e perfil antes de enviar; permanecem a proteção contra conflito, escrita individual, verificação do resultado e indicadores de envio/sucesso/erro.

As alterações atingem as regras do provedor, não uma filtragem local do Android, e não reescrevem consultas históricas. O app não executa VPN/proxy DNS local.

No AdGuard, o menu apresenta a exceção: a API documentada só oferece substituição da lista inteira, sem adição individual ou proteção de concorrência documentada. Uma explicação e um botão permitem abrir o painel, usando Custom Tabs ou WebView conforme a preferência salva. Não se faz PUT de listas existentes. Referência: https://adguard-dns.io/api/swagger/openapi.json.

Provedores sem API mantêm os painéis web específicos das abas. Senhas, cookies e MFA continuam sob as regras do provedor e do navegador. Nenhuma interface web é necessária para bloquear/liberar no NextDNS.

## Compatibilidade

Pacote e certificado mantidos. versionName 1.12.0-alpha.5, versionCode 23. Atualize sem desinstalar. Banco de hostnames, ordenação/habilitação, backups originais, Quick Settings e Shizuku preservados. A nova seleção local não entra no backup original; depois de importar em outro aparelho, escolha o perfil na primeira aba.

Consulte TESTES.md. Validação em aparelho e com credenciais reais continua necessária.
