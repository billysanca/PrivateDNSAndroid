# Nexo DNS

Endereços DNS do PrivateDNSAndroid e gestão nativa inspirada no ADNS, no mesmo aplicativo Android.

## Quatro abas

- **DNS**: a lista de hostnames do PrivateDNS original, com edição, ordenação e habilitação de endereços.
- **Estatísticas**: consultas e bloqueios carregados automaticamente, distribuição e consumo de cotas quando informado pelo provedor.
- **Consultas**: registros nativos com busca e filtros locais.
- **Configurações**: contas API, painéis web e preferências do alternador.

A navegação inferior flutuante e os cartões seguem o princípio do ADNS. A primeira aba mantém os endereços do PrivateDNS original.

## Instalar e atualizar

[Baixe o APK otimizado no Releases](https://github.com/billysanca/PrivateDNSAndroid/releases/tag/v1.12.0-alpha.4).

- Android 9 ou posterior; APK release sem debug.
- Nome: **Nexo DNS**. Pacote preservado: `io.github.billysanca.dnsmanager`.
- Atualiza sobre as versões release anteriores sem desinstalar, preservando os dados e a chave de assinatura.
- O debug `.dev` e o aplicativo original são instalações separadas; use exportar/importar para transferir hostnames.

Quick Settings e Shizuku permanecem disponíveis. Navegar e consultar o provedor não altera o DNS ativo. Para usar o alternador, autorize Shizuku ou a permissão ADB descrita na documentação.

## Conectar o provedor

Use Configurações, cole a chave de API e toque em Salvar. A descoberta do perfil/servidor acontece automaticamente; ambiguidades exigem escolha na lista. Há links oficiais para obter a chave e preencher o que não puder ser descoberto. Depois da conexão, Estatísticas e Consultas carregam ao abrir.

APIs oficiais NextDNS e AdGuard DNS para estatísticas e consultas. NextDNS também permite adicionar um domínio à lista de bloqueio/liberação, após confirmação individual. No AdGuard, regras são gerenciadas no painel oficial. Qualquer provedor pode ter um painel HTTPS associado ao hostname, aberto por Custom Tabs ou WebView opcional. Chaves são cifradas com Android Keystore; senhas dos painéis não são capturadas. Não há troca automática. As únicas escritas nativas são as ações individuais de domínio no NextDNS.

## Documentação e testes

- [Alpha 4: DNS ativo, páginas web e regras confirmadas](docs/NEXO-ALPHA4.md)
- [Nexo DNS Alpha 3: navegação, automação e limites](docs/NEXO-ALPHA3.md)
- [Segurança, instalação e arquitetura](README-FORK.md)
- [Regressões e roteiro em aparelho](docs/TESTES.md)
- [Histórico da configuração assistida Alpha 2](docs/ALPHA2.md)

A versão continua Alpha. Testes instrumentados, revisão visual em aparelho e APIs com contas reais precisam de validação antes de uma distribuição estável.

## Origem e licença

Fork de [karasevm/PrivateDNSAndroid](https://github.com/karasevm/PrivateDNSAndroid), com [eyalm2000/adns](https://github.com/eyalm2000/adns) como referência de integração e navegação. A licença MIT e a autoria original foram preservadas. Consulte o [README histórico do upstream](docs/UPSTREAM-README.md).
