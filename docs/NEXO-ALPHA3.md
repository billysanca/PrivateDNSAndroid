# Nexo DNS — Alpha 3

O projeto passa a se chamar Nexo DNS: o alternador e a lista de hostnames de karasevm/PrivateDNSAndroid unidos ao princípio de navegação nativa de eyalm2000/adns, com suporte adicional a AdGuard DNS e painéis web universais.

## As quatro abas

1. **DNS**: a primeira aba mantém a lista de endereços do PrivateDNS original. Adicionar, editar, habilitar/desabilitar, ordenar, importar/exportar, Quick Settings e Shizuku continuam usando os componentes existentes. Ela não foi substituída pelo Home do ADNS.
2. **Estatísticas**: carrega automaticamente a API do hostname/perfil selecionado, com contagens, distribuição entre consultas bloqueadas e não bloqueadas, e consumo da conta quando disponível. O gráfico representa distribuição, não uma série temporal inventada.
3. **Consultas**: carrega os registros automaticamente, apresenta domínio, horário, status e dispositivo quando disponível. Inclui busca local e filtros Todas/Bloqueadas/Outras sobre até 50 registros retornados.
4. **Configurações**: reúne conexão das contas, chave de API, associação do perfil, painel web, saída de sessões incorporadas e preferências do alternador original.

A barra inferior flutuante com quatro destinos, seleção destacada, cartões arredondados e hierarquia tipográfica seguem o princípio visual do ADNS. A tela inicial de hostnames permanece a do PrivateDNS. Nome e ícone do aplicativo foram atualizados. O código não importa regras Wi-Fi, troca automática de DNS nem operações de escrita remota do ADNS.

## Conectar uma conta

Em Estatísticas/Consultas, toque em Configurar conexão, ou use Configurações → Conectar conta ou painel. Para hostnames reconhecidos, o formulário sugere API nativa e o provedor. Use o link oficial para obter sua chave, cole-a e toque em **Salvar**.

Salvar inicia a descoberta automaticamente. Uma correspondência segura preenche e salva o perfil; se houver mais opções sem correspondência inequívoca, o app mostra a lista para você escolher. Depois de salvar uma conexão API, Estatísticas abre e carrega os dados. Não é mais necessário tocar em Gerenciar para buscar estatísticas ou consultas.

Quando não for possível descobrir, permanecem os links oficiais e a opção explícita Usar ID manual. Cancelar a escolha de perfil cancela o salvamento pendente. Criação/revogação da chave e login/MFA continuam sob seu controle no site oficial; o aplicativo não captura senhas nem cria credenciais remotas automaticamente.

## Automação e privacidade

- Ler dados ao abrir Estatísticas/Consultas é automático; alternar o DNS não é.
- As duas abas compartilham um snapshot em memória por até 60 segundos enquanto o aplicativo está em primeiro plano. Atualizar dados força nova consulta. Não há polling em segundo plano.
- O snapshot é descartado ao sair do aplicativo ou voltar à aba DNS. Consultas e resultados não são salvos em disco.
- Selecionar um DNS/perfil nas páginas de gestão não muda o DNS ativo do Android.
- As chaves e o gerenciamento web mantêm o armazenamento seguro e as regras de sessão já existentes.
- Um provedor sem API oferece seu painel web em Configurações; o aplicativo não inventa estatísticas nativas desse provedor.

## Limites e atualização

A janela continua em 24 horas e os logs mostram a primeira página de até 50 registros. A API pública documentada do NextDNS não fornece cota de conta: a aba oferece acesso à página oficial da conta. AdGuard mostra cotas da conta padrão da chave.

Nome visível: Nexo DNS. Pacote release preservado: io.github.billysanca.dnsmanager. versionCode 21, versionName 1.12.0-alpha.3. O mesmo certificado release permite atualizar sobre as versões otimizadas Alpha 1/2 sem desinstalar. Hostnames, banco Room, preferências, associações de gestão, chaves e sessões permanecem no mesmo espaço de dados. O debug .dev continua separado.

Os projetos de origem continuam creditados e a licença MIT original está preservada. Não houve renomeação do repositório GitHub, para manter os links anteriores e a origem do fork.

## Validação

Os novos testes JVM cobrem interpretação de estatísticas, cotas separadas de tráfego, respostas inválidas, estados de bloqueio e cache por hostname/perfil com expiração e descarte. Um teste instrumentado adicional verifica os quatro destinos, a lista original na primeira aba e a ausência de alteração do DNS ao navegar.

Consulte TESTES.md para os resultados finais. Os testes instrumentados foram preparados para execução em aparelho; a inspeção visual em Android real e a conexão com contas reais ainda precisam ser realizadas. Esta versão permanece Alpha.
