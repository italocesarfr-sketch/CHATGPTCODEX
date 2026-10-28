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
