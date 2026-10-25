# Nexo DNS — análise de interface e experiência

## Objetivo e método

Manter o modelo central: DNSs na primeira aba; estatísticas e consultas em destinos próprios; configurações acessíveis sem competir com os dados. A revisão examinou código de navegação, layouts, cores, estados de seleção, fluxos de conta, leitura de API, atualização em primeiro plano, erros, privacidade e compatibilidade.

Foram usadas as recomendações atuais do Material 3 para hierarquia, superfícies, navegação e papéis de cor, além da API Android de Dynamic Color. Não se trata de pesquisa com usuários ou de uma medição de desempenho em aparelhos: essas validações não foram realizadas.

## Achados e decisões

| Problema observado no código | Consequência | Mudança implementada |
| --- | --- | --- |
| O Toolbar ainda inflava menu_main pelo XML; esconder o menu da Activity não removia esse menu independente. | A reticência do cabeçalho continuava aparecendo. | Removida a inflação no Toolbar. Os handlers originais são despachados por um menu sem apresentação, a partir de Configurações. |
| Título grande no conteúdo repetia o título do cabeçalho. | Hierarquia redundante e menos espaço para dados. | Um único título principal na barra superior; conteúdo começa com contexto ou dados. Rótulos da navegação identificam destinos. |
| A seleção da primeira aba só era evidente pelo subtítulo. | Risco de gerenciar um perfil diferente do esperado. | Cartão selecionado com superfície primária, contorno e estado acessível; todas as abas usam o mesmo registro. |
| Navegação flutuante, muitos cartões arredondados e margens internas acumuladas. | Espaço útil reduzido e competição visual. | Navegação inferior Material convencional com quatro rótulos; padding de conteúdo consistente, cartões de 20 dp e FAB à direita. |
| A configuração do app era uma sequência de botões contornados. | Dificuldade para distinguir conta, aparência e utilidades. | Grupos Aparência, conexão do provedor e Aplicativo; utilidades em linhas Material com descrições. Importação/exportação têm escolhas agrupadas. |
| Cores dinâmicas eram incondicionais e não havia preferência de tema. | Pouco controle e inconsistência entre telas nativas e Compose. | Tema do sistema, claro, escuro ou preto OLED; cores do celular como preferência independente. Base comum para Views, WebView nativa e telas Compose. |
| A atualização da aba de consultas lia estatísticas e cotas também. | Tráfego desnecessário e comportamento surpreendente. | Clientes têm leituras separadas. Consultas chama só logs; Estatísticas chama somente estatísticas/cotas, uma vez ao abrir. |
| Contínuo era apenas polling. | “Tempo real” podia ser interpretado como transmissão. | NextDNS usa SSE oficial com cursor inicial. AdGuard identifica o fallback de 5 s. Intervalos adicionais permanecem disponíveis. |
| Erros podiam apagar o contexto de dados; mudanças de cadência perdiam filtros. | Perda de continuidade durante análise. | Últimos resultados permanecem quando a atualização falha; modo volta a Manual; busca e filtro ficam em memória durante a sessão. |
| Clipboard era consultado ao perder foco. | Leitura inesperada e importação dependente de um estado de foco inadequado. | Clipboard lido somente quando o usuário pede Importar da área de transferência. |
| O botão de voltar das licenças continha outro botão aninhado. | Semântica e alvo de toque redundantes. | Um único botão Material de voltar. |

## Navegação e modelo mental

As quatro abas permanecem visíveis: DNS, Estatísticas, Consultas e Configurações. Configurações fica na barra inferior por ser um destino recorrente, evitando esconder aparência e conexões em um menu de cabeçalho.

Tocar em um DNS seleciona o perfil compartilhado. Segurar abre edição; a alça continua ordenando. A seleção para gerenciamento não altera sozinha o DNS do Android: Quick Settings, Shizuku e os mecanismos originais continuam responsáveis pela alternância. O subtítulo descreve “perfil selecionado”, sem afirmar uma conexão de rede que não foi medida.

## Temas e legibilidade

Claro e escuro seguem os papéis Material. Preto OLED mantém fundo preto e superfícies próximas do preto com separação tonal; os destaques conservam cores semânticas. “Cores do celular · Material You” usa Dynamic Color em aparelhos Android 12+ compatíveis e preserva a paleta Nexo nos demais.

O modo de luminosidade e a fonte dos acentos são controles separados. A preferência persiste, aplica-se ao reiniciar a Activity e mantém a aba atual. Não há permissão de leitura de fotos/papel de parede: as cores vêm das APIs do sistema.

As consultas mantêm linhas compactas, divisórias, superfícies alternadas e menu de 48 dp à direita. Cores usam papéis de tema, não uma paleta clara fixa. Domínios longos podem ser abreviados visualmente; menus e confirmação identificam o nome completo. Revisão com TalkBack, fonte ampliada e contraste real do dispositivo continua pendente.

## Tempo Real e limites claros

Somente Consultas possui controles de atualização. Manual, Tempo Real e 15/30/60 s ficam em uma linha compacta com botão de recarga.

NextDNS: leitura inicial de até 50 registros, cursor meta.stream.id e conexão GET /profiles/:profile/logs/stream. Eventos são validados, limitados, deduplicados por ID e apresentados em um buffer dos 50 registros mais recentes. As linhas já presentes são reaproveitadas para reduzir recriação de controles; o efeito de desempenho ainda não foi medido em aparelho. A UI recebe snapshots limitados; nunca se escreve histórico em disco. Cancelar a coleta fecha a conexão; sair da tela/app interrompe o modo.

AdGuard: a consulta de logs é repetida a cada 5 s no modo Tempo Real; a interface informa essa diferença. Não se inventou um endpoint de streaming.

Erros, expiração, HTTP 429 ou encerramento do stream retornam ao modo Manual. Reconectar é ação do usuário. As regras de domínio continuam sendo ações separadas com confirmação; transmissão e atualização nunca bloqueiam/liberam automaticamente.

## Compatibilidade e segurança

Não houve migração de Room, alteração de backup, troca de pacote ou de certificado. Chaves, hostnames, seleção e sessões existentes permanecem. O núcleo de alternância, Quick Settings e Shizuku foi comparado com o upstream.

A arquitetura mantém gerenciamento web separado dos clientes de API. O stream envia a chave só pelo cabeçalho HTTPS ao host oficial, sem redirects. IDs e cursor não podem controlar host ou esquema. Dados malformados ou grandes demais encerram a leitura, sem executar conteúdo remoto.

Fixtures visuais com dados fictícios existem apenas em src/debug e testes instrumentados. DesignPreviewActivity não faz parte do manifesto/APK release. Elas permitem testar temas e telas sem credenciais ou tráfego de contas reais.

## Evidência e pendências

Consulte TESTES.md para resultados da compilação, testes e assinatura. Os testes JVM cobrem contrato de leitura, validação e cancelamento do stream, deduplicação, limite do buffer e regressões existentes.

O emulador oficial e uma imagem Android 35 foram preparados no workspace. O diagnóstico de aceleração retornou código 6; as tentativas sem aceleração encerraram sem disponibilizar um dispositivo ADB. Nenhuma configuração de virtualização/segurança do Windows foi alterada. Testes instrumentados foram compilados, mas não executados, e não há captura de UI validada em Android nesta entrega.

Antes de chamar a versão de estável: executar a bateria em aparelho/emulador compatível, revisar claro/escuro/OLED/Material You, fonte grande, gestos, importação/exportação e contas reais. A autenticação, SSE e limites de tráfego precisam de teste ponta a ponta com permissões reais do provedor. O limite de 50 registros e a ausência de seleção de contas compartilhadas AdGuard continuam explícitos.

## Referências primárias

- Material 3 — app bars: https://m3.material.io/components/app-bars/
- Material 3 — papéis de cor: https://m3.material.io/m3/pages/color-roles?s=m3
- Android — Dynamic Color: https://developer.android.com/develop/ui/views/theming/dynamic-colors
- NextDNS — streaming e cursor: https://nextdns.github.io/api/
