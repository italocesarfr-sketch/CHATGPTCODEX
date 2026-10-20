# Noctua 0.13.0 / 23 — relatórios e correções de horários

Base: 0.12.1 / 22, commit `66bd711`. Escopo restrito aos refinamentos autorizados. Pacote `br.com.diariodeclasse`, banco `diario.db`, Room v7 (14 tabelas), backup formato 8, migrações e assinatura permanente preservados. Nenhuma dependência nova.

## Relatórios e exportação anual

Busca global mantém a mesma pesquisa e recebe lupa no início. Títulos do boletim usam “Boletim 1º Bim” a “Boletim 4º Bim”. Retirado Consultas; o histórico anual individual e as projeções anteriores continuam disponíveis.

O ícone de ordenação abre Chamada, Mais faltas, Menos faltas, Maior nota e Menor nota. Selecionar novamente a opção ativa retorna à Chamada. O menu fecha após a escolha; um ponto e a cor do ícone identificam a ordenação ativa, também anunciada no rótulo acessível. Desempate reaproveita número, nome e ID da chamada. Durante a busca global os filtros permanecem ocultos. Geral, Frequência e Notas preservados.

“Exportar relatório anual”, em Exportar planilha, reutiliza `ExportProjection` no modelo Frequência e `SchoolReports.year`. Inclui chamada, nome, turma/ano, matrícula, indicadores de cada bimestre e consolidação anual: aulas elegíveis, registros válidos, P, F, FJ, faltas efetivas, NR e percentual. O percentual anual é o já calculado pelo aplicativo, não a média dos percentuais. Não há média anual de notas nem nova fórmula acadêmica.

A opção anual usa ordem oficial; respeita ocultação manual e exclusão automática de remanejados na origem. Linhas vazias e ordem personalizada continuam nas exportações existentes, mas não nesta relação anual em ordem de chamada. Históricos consolidados no destino continuam nas projeções existentes, inclusive após exclusão de origens/intermediárias. Valores são células numéricas, percentuais sem dados ficam vazios. Mesmo escritor OOXML, prévia, seletor Android, gravação e compartilhamento da chamada; nenhuma gravação escolar ao exportar. Fluxo também disponível para turmas arquivadas.

## Reativação transacional

`setDayHours` exige confirmação separada para reativar os horários cancelados cujo ordinal é menor ou igual à quantidade solicitada. A confirmação precede qualquer escrita, inclusive quando há também cancelamentos excedentes. Ao confirmar, reutiliza os IDs e todas as marcações/observações; cria somente os ordinais faltantes. Cancelados excedentes continuam cancelados. Reativação não preenche presenças em chamadas antigas vazias. A preferência existente de presença inicial continua aplicada aos demais horários como anteriormente.

A interface pergunta “Reativar N horários cancelados?” e permite Não/Reativar. Cancelar não altera nada. Redução mantém confirmação; reativação individual permanece disponível. Operação única `Room.withTransaction`; erro intermediário reverte todas as alterações.

## Limpar horários do dia

Ação discreta na chamada existente. Conferência somente leitura e confirmação “Remover os horários desta data?”. A confirmação captura turma, data e lista de horários, não a seleção global futura. O Repository repete todas as verificações dentro da transação e recusa se os horários tiverem mudado.

Por segurança, **qualquer** marca persistida (inclusive NR explícito após desmarcar), observação não vazia, registro do diário, atividade ou recomposição naquela turma/data bloqueia a limpeza. Referências no histórico preservado também bloqueiam. Não exclui alguns horários enquanto outros do mesmo dia têm registros. O aviso curto orienta que os horários foram preservados; cancelamento e reativação continuam disponíveis.

Somente quando o dia está realmente vazio remove as linhas `lessons` daquela turma/data/IDs. A consulta também exige ausência de marca e observação; quantidade inesperada de remoções ou erro produz rollback completo. Não usa cascatas nem apaga marcas, diários, notas ou histórico. Datas distintas e outras turmas permanecem intactas. Calendário existente diferencia NR, cancelado e ausência de horários; após remoção não há indicador.

## Validação

- Compilação local `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: aprovada; 112 testes JVM sem falhas na primeira validação completa.
- Planilha anual lida pelo openpyxl, conferindo conteúdo, células numéricas/vazias, dimensões e integridade ZIP. Regressão das demais matrizes mantida no CI.
- Novos testes Room: ciclo 2→0→1→2, IDs e marcações após reinício, combinação com horários novos, cancelamento sem confirmação, limpeza dirigida, proteção de NR/P/F/FJ/observação/diário/atividade/recomposição, confirmação desatualizada, rollback de remoção e reativação, backup/restauração e FK.
- Testes de interface atualizados aos rótulos/controles solicitados sem retirar verificações de integridade; novos fluxos de reativação, cancelamento, limpeza/calendário e exportação anual.
- CI e atualização assinada: resultados finais serão registrados após execução. Referência fixada no APK release real 0.12.1 do run 39357929630, conferindo hash e certificado antes de instalar por cima no emulador descartável. Workflow manual de release e Secrets não alterados.

Limitações: testes usam dados fictícios em emulador. Não representam validação em todos os fabricantes, provedores de documentos ou Microsoft Excel em aparelho físico. Não executar limpeza ou testes destrutivos em aparelhos com registros reais. A compatibilidade de instalação depende da igualdade do certificado com o APK de referência, verificada pelo processo de release.
