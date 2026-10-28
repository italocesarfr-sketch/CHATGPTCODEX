# NOcTuA Professor 0.19.1 — fluxo direto de FJ

Base: 0.19.0 / 36. Candidata: 0.19.1 / 37. Compilação e 187 testes JVM aprovados localmente; Android e release pendentes.

## Diagnóstico reproduzido antes da correção

O teste `FjPopupInstrumentedTest.firstOpeningSurvivesVisibleColumnWindowShiftWithoutChangingTheOccurrence` abriu o menu no primeiro toque, mudou a janela horizontal de 270 para 320 dp e confirmou que o indicador da mesma ocorrência continuava visível. O popup desapareceu: a asserção de sua existência falhou, sem nenhuma alteração de frequência. [Execução de reprodução](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40623890309), código `1f1524e78e9f0807338cac390ea0ffac0703c59d`.

A grade compunha somente as colunas próximas à janela, mas o laço não tinha chaves por coluna. Quando o primeiro índice mudava, a célula passava a ocupar outro slot; seu `remember` local era descartado. O popup era removido durante a abertura/fade-in, explicando o lampejo transparente e o fechamento. A reprodução prova a perda de estado por virtualização; não houve diagnóstico direto no aparelho físico do proprietário.

A correção acrescenta `key` nas colunas e mantém a abertura no nível da grade, identificada por IDs de aula e matrícula. Recompor dados ou mover uma coluna ainda visível não reinicia o menu. Ao sair da ocorrência/linha ou trocar o contexto, a seleção é descartada sem gravar. O fundo do menu Material 3 é explicitamente sólido, preservando tema, dimensões e controles existentes. Não foi tratada somente a cor.

## Interação

- P e F grandes gravam diretamente P/F, inclusive em toques repetidos, limpando eventual motivo. Não abrem menus. A API anterior `toggleMark` permanece para usos existentes, mas a chamada passa a usar `mark`.
- F pequeno abre os seis motivos sem alterar a falta. Selecionar um converte atomicamente F em FJ e salva o motivo.
- FJ pequeno consulta/altera o motivo; Remover justificativa converte para F e limpa o motivo, inclusive nas FJ antigas sem motivo.
- Não há Sem motivo/Sem justificativa no menu de faltas. Abrir, voltar ou tocar fora sem selecionar conserva o registro.
- P e NR pequenos conservam suas ações anteriores de estado, sem mostrar a lista de motivos ou converter o registro ao abrir. NR continua disponível como ação explícita separada, inclusive para limpar P/F/FJ. NR não equivale a falta ou presença.

Os seis motivos permanecem inalterados. O menu é o único acesso; nenhuma linha, botão permanente ou formulário foi acrescentado. A ajuda contextual da chamada foi ajustada ao comportamento direto.

## Integridade

`justifyAbsence` recebe a ocorrência exibida, revalida a linha completa, matrícula, aula, situação e elegibilidade dentro de uma transação e grava uma única atualização. Uma seleção atrasada não recria uma marcação nem altera outro aluno/horário. Os guardas de remanejamento e histórico permanecem. `setJustification` anterior continua disponível com sua semântica de metadados, preservando testes e dados legados.

Room v10, backup 12, esquema, migrações e modelos permanecem inalterados. Sem reclassificação de FJ antigas, sem limpeza de banco ou mudança de ID. Notas, diário, exportadores e fórmulas de frequência não foram modificados. F/FJ continuam seguindo a configuração acadêmica existente da turma; nenhum motivo cria regra de abono.

## Validação

Quatro testes JVM novos e nove Android focados verificam conversão/remoção, seis motivos, FJ antiga, concorrência, rollback, backup e reinício, auditoria protegida, primeiro toque, atualizações de dados, duas direções de rolagem, vários alunos e horários, superfície opaca e dispensas sem alteração de frequência. A regressão anterior foi adaptada somente onde o novo contrato explícito substitui o segundo toque por P/F diretos e Remover justificativa por F.

Impeccable 4.7.0 consultado: contexto, Android e craft, diagnóstico por capturas reais e preservação da interface existente. Resultados completos, capturas e release serão registrados após execução efetiva. Dados exclusivamente fictícios; aparelho físico e leitor de tela permanecem manuais.

## Arquivos da alteração

- `app/src/main/java/br/com/diariodeclasse/ui/FrequencyGrid.kt`: identidade do popup, fundo sólido e ações do indicador existente.
- `app/src/main/java/br/com/diariodeclasse/ui/DayAttendanceScreen.kt`: P/F diretos e encaminhamento da justificativa.
- `app/src/main/java/br/com/diariodeclasse/data/Classifications.kt`: transição explícita F/FJ e validação dos seis motivos.
- `app/src/main/java/br/com/diariodeclasse/data/DiaryRepository.kt`: atualização transacional da ocorrência esperada.
- `app/build.gradle.kts`: 0.19.1 / 37.
- `AbsenceJustificationsTest.kt`, `FjFlowInstrumentedTest.kt`, `FjPopupInstrumentedTest.kt`: testes novos; `ClassificationsLayoutInstrumentedTest.kt`, `ClassificationsUiInstrumentedTest.kt` e `PromptTwoUiInstrumentedTest.kt`: atualização das expectativas de interação, preservando comparações de dados.
- `scripts/ci/validate-android.sh`, `.github/workflows/android.yml` e `docs/baseline/noctua-release-0.19.0.json`: testes, capturas e referência original de atualização. A configuração da chave permanente não mudou.
- Esta documentação, README e capturas da entrega.

## Retomada da validação

A execução `40627184906` aprovou os nove testes focados de FJ, o grupo de dez classificações e as fontes reais 1,3/1,6. A suíte completa terminou com uma única falha em `ReportsUiInstrumentedTest.searchKeepsViewOrderAndSemesterAcrossRotationWithoutWritingSchoolRows`: `SnapshotStateObserver` foi acessado pelo thread do harness enquanto `performScrollToNode` percorria os filhos semânticos durante a atualização da busca. O stack trace aponta para a construção da mensagem de erro da automação, sem alteração de dados ou código de produto de Relatórios.

A retomada preservou as asserções de conteúdo, seleção, rotação e comparação integral do backup. O teste espera o resultado global, fecha o teclado pela ação de pesquisa e posiciona o primeiro resultado pela estrutura conhecida da fixture; valida explicitamente a matrícula e a falta efetiva exibidas. A tela de Relatórios não foi modificada.

O teste de dispensa externa agora espera a composição/desenho, lê `positionOnScreen` e o tamanho real da grade e da janela, seleciona uma região dentro da grade e fora do popup com margem de segurança, e comprova essas condições antes de enviar o gesto Android. Não usa coordenadas fixas nem um atraso arbitrário para abrir a janela. Captura e geometria são salvas no artefato. O fechamento deve ocorrer com zero gravações e a falta original intacta. As duas interações são executadas novamente duas vezes, ambas obrigatoriamente aprovadas, além dos grupos focados e da suíte completa; não há retry que aceite uma falha anterior nem exclusão de testes.
