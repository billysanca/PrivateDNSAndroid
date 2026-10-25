# Nexo DNS — Alpha 7 (1.13.0-alpha.7)

Esta versão reúne a revisão de interface e temas. A análise completa, achados, decisões e limites estão em UX-REVIEW-ALPHA7.md.

- Primeira aba com seleção destacada em cartões Material; toque seleciona, toque longo edita, alça ordena.
- Quatro destinos visíveis, sem reticência de opções no cabeçalho. Configurações concentra aparência, conexões, backups, sessões, privacidade e licenças.
- Títulos grandes duplicados removidos; padding reduzido; FAB à direita; listas e ações organizadas por hierarquia.
- Temas: usar sistema, claro, escuro e preto OLED. Cores do celular (Material You / cores dinâmicas) como opção separada em Android 12+ compatível.
- Atualização somente em Consultas. Estatísticas carrega seu snapshot sem controles ou timer de atualização.
- Tempo Real NextDNS usa SSE oficial, com cursor e buffer de até 50 registros. AdGuard usa polling de 5 s identificado na interface. Intervalos 15/30/60 s também disponíveis.
- Filtros preservados na sessão. Erros deixam os últimos dados visíveis e retornam a Manual. Sair do app/DNS ou mudar de aba encerra a leitura anterior. O modo automático não opera em segundo plano.
- Atualização de consultas não busca estatísticas/cotas. Abrir Estatísticas não busca logs.
- Bloqueio/liberação NextDNS mantém confirmação; AdGuard mantém a exceção de painel para evitar substituir listas de regras. Nenhuma escrita automática foi adicionada.
- Clipboard lido somente na importação solicitada.

Pacote io.github.billysanca.dnsmanager, versionCode 25. Mesmo certificado: atualizar sem desinstalar para preservar dados. Banco, múltiplos hostnames, backups, Quick Settings e Shizuku preservados.

A versão permanece Alpha: testes instrumentados/visuais e APIs com contas reais ainda precisam de execução. O emulador oficial não conseguiu iniciar neste ambiente; não se declara revisão visual em Android. Fixtures sintéticas de UI estão só no build debug, não no APK release.

Leia TESTES.md para a evidência final e as instruções de regressão.
