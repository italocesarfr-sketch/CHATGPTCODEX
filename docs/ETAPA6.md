# Etapa 6 — Identidade noturna e navegação integrada

Candidato **Noctua 0.11.0 / código 16**, sobre a **0.10.0 / código 15** aprovada em aparelho físico. Somente experiência de uso, navegação e apresentação. Nenhuma funcionalidade pedagógica nova.

## Impeccable e auditoria

A skill `$impeccable` **não está disponível nesta execução**. Foram consultados os catálogos executor/cloud e pesquisados os diretórios do projeto, `/workspace/.agents`, `/workspace/.codex`, `/home/agent/.codex/skills`, plugins/cache e `/opt/codex/skills`, inclusive arquivos ocultos e links. Não foi encontrada nem instalada outra skill com esse nome; não se atribuem a ela decisões desta entrega. A alternativa foi expressamente autorizada no pedido da Etapa 6. A skill de ambiente de nuvem foi usada apenas para acesso/compilação, não como substituta de design. Se o pacote Impeccable for disponibilizado posteriormente, sua auditoria poderá orientar outro incremento autorizado.

Auditoria da base: tema dependente do sistema com superfícies verdes, cabeçalho repetitivo, seletor com nomes truncados, controles de exportação misturados às matrículas e diário separado da chamada. O diário não tinha acesso por data do calendário. Alguns botões em linha poderiam se apertar em telas menores; cores de notas antes adequadas ao fundo claro precisavam de maior luminância em fundo escuro. As capturas anteriores são as da versão 0.10.0 com fixtures fictícias; não são mockups.

## Identidade e tokens

Implementação exclusivamente Kotlin/Compose Material 3, sem dependências web, fontes remotas ou imagens de terceiros. `ui/theme/NoctuaTheme.kt` centraliza cores, tipografia, formas e espaçamentos. O tema principal é noturno independentemente do modo do Android; a base anterior não possuía preferência manual de tema persistida. Não foi criada uma configuração nova nem alterada a seleção de ano/turma.

| Papel | Cor |
| --- | --- |
| Fundo | `#0B1220` |
| Superfície | `#101B2C` |
| Painel / recipiente | `#172438` |
| Cartão Material elevado | `#293A52` |
| Superfície elevada | `#203149` |
| Texto principal | `#E4EAF5` |
| Texto secundário | `#B1BFD3` |
| Destaque frio | `#9DCADF` |
| Recipiente selecionado | `#203F53` |
| Verde de situação | `#91D6AE` |
| Amarelo de situação | `#E8CC84` |
| Vermelho/erro | `#FFB4AB` |

Sem preto puro repetido. Fundos e superfícies têm profundidades distintas; cores de situação conservam significado e limites existentes, com números/textos além das cores. Fonte do Android, sem downloads: títulos 20–26 sp, corpo 14–16 sp com entrelinha 21–24 sp, informações auxiliares 11–13 sp. A fonte acompanha a escala de acessibilidade do Android. Espaçamentos 4/8/12/16/24 dp, páginas 16 dp e alvos principais de toque 48 dp. Formas 10/16/20 dp. Textos e contornos são verificados por testes de contraste; alvos e rótulos são conferidos no Compose em largura 320 dp e fonte 130%.

Cabeçalho compacto com Noctua; a ênfase discreta em N/o/t/a usa peso/cor e conserva grafia e leitura acessível. A coruja e o ícone instalados **permanecem os mesmos**. A pequena base clara do símbolo conserva sua legibilidade no tema escuro. [Proposta vetorial coruja/N](brand/noctua-coruja-n-proposta.svg) é desenho original, reversível e **não instalado**, pendente de aprovação do proprietário. Não é a arte definitiva.

`enableEdgeToEdge` e os insets do Scaffold/TopAppBar/NavigationBar respeitam as barras de sistema e teclado. O seletor exibe turma/disciplina sem reticências automáticas. Tabelas continuam rolando horizontalmente com nomes fixos; nomes longos na chamada mantêm a consulta integral por toque já existente.

## Quatro áreas e calendário

1. **Cadastrar Turma**: criação/edição, alunos, lista/CSV, situações e histórico. Arquivamento e exclusão continuam na área dedicada Gerenciar turma, com as proteções anteriores. As preferências de exportação saíram da lista escolar.
2. **Chamada/Diário**: título interno **Fazer Chamada / Diário de Classe** e modos **Chamada** / **Diário**, lado a lado. Chamada é o modo inicial. Turma/ano permanecem globais; troca de modo não grava dados. O mês é compartilhado e o estado de leitura da chamada é preservado.
3. **Atividades e Notas**: formulários, notas, recomposição e perfis mantidos sob o tema comum, sem mudança de cálculo.
4. **Relatórios**: consultas bimestrais/anuais, notas/recomposição/frequência/faltosos e entradas **Organizar exportação** e **Exportar planilha**. A geração direta permanece disponível.

O componente nativo `SchoolCalendar` é reutilizado pelos dois modos. Cada data fornece marcação/descrição acessível conforme o contexto. Alvos de 48 dp; em larguras menores, o conjunto de sete dias permite rolagem horizontal, mantendo legibilidade, além da rolagem vertical. O calendário não cria aulas/diários ao navegar.

**Chamada** conserva Calendário/Bimestre abaixo dos modos principais, bimestres editáveis, seleção livre, quantidade de horários, P/F/FJ/NR, presença coletiva e edição/cancelamento. Marcadores continuam frequência preenchida/parcial, NR, cancelada e previsão legada. Nenhum algoritmo de frequência foi reescrito.

**Diário → data** abre apenas os registros daquela turma/dia. Criar preenche turma/data e permite alterá-las. Datas com textos mostram ponto discreto e descrição com a contagem. Há Visualizar, Editar, Duplicar, Excluir com confirmação, Adicionar outro registro, Copiar título/descrição. As ações reutilizam o módulo 5.3 e suas transações. **Consultar todos os registros** abre a lista com filtros anteriores, inclusive para consultar datas fora do ano da turma ao limpar os limites. O acesso redundante em Relatórios foi removido após manter essa consulta completa. A consulta de turmas arquivadas permanece pela gestão administrativa.

O diário continua facultativo, sem ligação com horários ou existência de chamada. Criar texto não cria frequência; fazer chamada não cria texto. Rascunhos de formulário e disposição continuam protegidos contra descarte silencioso; a fila de salvamento e as mensagens de erro anteriores permanecem.

## Organização e geração de planilhas

**Relatórios → Organizar exportação** reúne Disposição e Ocultar alunos. As mesmas linhas por ID persistente e `ExportEditSession` da 5.1 mantêm seleção múltipla, ordem, espaços e exclusão automática de remanejados. Concluir salva em lote e volta a Relatórios; Cancelar descarta a sessão; **Concluir e exportar planilha** salva antes de abrir a geração. Nada disso altera numeração, chamada, status ou notas.

**Relatórios → Exportar planilha** continua um acesso direto opcional, sem exigir organização manual. XLSX/CSV, cópia completa/somente notas, prévia e opções continuam usando as projeções e escritores 5.2. Não há novo algoritmo, formato ou cópia de registros escolares. Os testes anteriores foram atualizados para a nova localização, conservando as asserções de persistência e independência.

## Dados, backup e assinatura

**Room v6 e backup formato 7 inalterados.** Nenhuma tabela, coluna, entidade, migração, regra de negócio ou operação de exclusão foi alterada. `br.com.diariodeclasse`, `diario.db`, funcionamento offline e assinatura permanente permanecem. JSONs Room 1–6 congelados. Backup/restauração e projeções de relatórios/exportação são reutilizados.

O CI compara o APK **real 0.10.0** do [Actions 39137573909](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909), fixado em `docs/baseline/noctua-release-0.10.0.json`, com o candidato. A semente inclui as treze tabelas, inclusive dois registros de aula da versão anterior e turma arquivada. O workflow manual de release e a chave/Secrets não são modificados.

## Validação e resultados

Primeiro ensaio [39145646257](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39145646257): 93 JVM e 152 Android aprovados, quatro falhas de interface. Uma identificou truncamento real da barra em 320 dp/fonte 130%; os itens passaram a usar a largura de sua célula, dentro do NavigationBar Material e com semântica de aba. Três ajustes de teste preservaram as asserções: aguardar o controle na nova área de Relatórios, verificar a turma como parte do rótulo completo e restringir texto repetido ao diálogo de visualização. Nenhum erro de regra ou Room foi encontrado. Aguardando nova execução completa e prova de atualização. Compilação local não substitui teste de interface nem atualização assinada.

## Limitações e aprovação

Impeccable indisponível, conforme descrito. A arte vetorial coruja/N é proposta; o ícone atual segue instalado. O tema noturno é o padrão fixo desta entrega, sem novo seletor de temas. Calendário pode precisar de deslize horizontal em aparelhos estreitos para manter alvos de toque. Não há alegação de teste físico da 0.11.0 ou de ensaio completo com TalkBack; serão necessários no aparelho do proprietário. Testes instrumentados usam API 30 e os cenários compactos documentados; não representam todos os fabricantes. Sem alterações pedagógicas, IA, PDF ou outras funcionalidades escolares.
