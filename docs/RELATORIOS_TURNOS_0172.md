# NOcTuA Professor 0.17.2 — Relatórios e seis períodos por turno

Versão proposta: **0.17.2 / código 31**, sobre a release **0.17.1 / 30**. Resultados finais e capturas serão registrados depois da aprovação do CI.

## Relatórios

A tela mantém o bimestre e apresenta **Visualizar: Geral / Notas / Frequência**, depois **Ordenar alunos**, Buscar com opções secundárias e o boletim. Os três modos reutilizam as projeções existentes, sem reduzir as informações de Geral ou confundir zero, pendência e não aplicável.

A ordenação oferece Nº da chamada e Nome A–Z em todos os modos. Geral acrescenta maior/menor nota e mais/menos faltas; Notas acrescenta apenas os critérios de notas, e Frequência apenas os de faltas. Ao mudar a visualização, uma ordem incompatível volta imediatamente à chamada; uma compatível permanece. Os cálculos e desempates continuam em `SchoolReports.ordered`. A seleção repetida de um critério ativo continua restaurando a chamada, comportamento já existente.

A busca permanece global, em todas as turmas e anos, com os mesmos IDs, critérios de matrícula, acentuação e histórico. Agora ela respeita o modo e a ordem escolhidos. Cada grupo conserva a identificação da turma e suas próprias regras de cálculo. Rotação e mudança de bimestre preservam a consulta e os controles aplicáveis.

O ícone ao lado de Buscar contém somente Mostrar remanejados, quando aplicável, e Informações do relatório. Nenhum modo ou ordenação é repetido nesse menu.

O antigo Organizar passa a **Personalizar planilha** e continua abrindo o mesmo editor persistente de ordem, ocultação e linhas vazias. Exportar continua usando o bimestre e as preferências próprias da exportação. Ordenar o boletim nunca grava essas preferências. Os dois acessos ficam no fluxo de Relatórios, após Buscar e antes do conteúdo. Em fontes ampliadas, as ações passam a uma coluna para preservar a legibilidade.

A seleção comum usa o controle segmentado Material 3. Em larguras muito compactas ou fonte ampliada, as mesmas três opções usam chips de seleção única Material 3 com quebra de linha; não há redução da fonte, corte de palavras ou rolagem horizontal para navegar entre os modos.

## Grade semanal

O cadastro comum mostra somente 1º a 6º períodos, relativos ao turno já cadastrado da turma. O mesmo período pode coexistir em Matutino, Vespertino e Noturno; o bloqueio de conflitos dentro do mesmo turno é preservado.

A leitura histórica e a validação do backup continuam aceitando períodos antigos de 7º a 12º. O editor conserva esses períodos e os identifica em uma área separada, com Retirar e confirmação. A retirada altera apenas o rascunho até Salvar; a revisão anterior continua disponível. Nenhum horário acadêmico, frequência ou conteúdo é apagado ou renumerado.

Além de retirar os botões comuns acima do 6º, as prévias de edição rejeitam novos períodos acima desse limite. Somente períodos históricos identificados pelo mesmo ID, turma, dia e ordinal podem ser conservados. A validação geral dos dados históricos não foi estreitada para seis, evitando invalidar bancos ou backups antigos.

O primeiro cadastro anual e os alcances Hoje/ano inteiro da 0.17.1 permanecem intactos. Chamada e Diário continuam lendo a mesma grade, sem gerar lançamentos automaticamente.

## Integridade

- `br.com.diariodeclasse`, banco `diario.db`, Room v9 e backup formato 11 preservados.
- Sem nova migração, alteração de entidades Room ou formato de backup.
- Sem mudança nas fórmulas, frequência, recomposição, remanejamentos, diários ou exportadores.
- Certificado permanente e workflow de release preservados. O CI verifica atualização sobre o APK original 0.17.1, incluindo período antigo acima do 6º, com dados exclusivamente fictícios.

## Arquivos

Produção: `reports/ReportDisplay.kt`, `ui/ReportsScreen.kt`, `ui/DiaryApp.kt`, `ui/Components.kt`, `ui/TimetableScreen.kt`, `data/WeeklyTimetable.kt` (sob `app/src/main/java/br/com/diariodeclasse/`) e `app/build.gradle.kts`.

Testes de regras, Room e UI foram acrescentados ou adaptados aos novos pontos de acesso, mantendo as verificações de integridade, valores, persistência, layout, exportação e histórico. CI: `.github/workflows/android.yml`, `scripts/ci/validate-android.sh` e `docs/baseline/noctua-release-0.17.1.json`.

## Design e limitações

Impeccable 4.7.0 foi lido e aplicado: contexto, simplificação, diretrizes Android e craft. Implementação nativa Kotlin/Compose, identidade visual preservada, alvos de pelo menos 48 dp e testes de texto/rolagem em 320/360 dp e fontes ampliadas.

A revisão visual final usará capturas reais do emulador. Validação física e abertura na interface do Microsoft Excel permanecem com o proprietário; os arquivos são conferidos por leitores independentes no CI.
