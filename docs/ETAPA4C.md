# Etapa 4C — Remanejamento seguro de alunos

Incremento **Noctua 0.7.0 / código 11**, base **0.6.1 / código 10**. Escopo: remanejamento confirmado, histórico vinculado, consultas consolidadas e segurança de atualização. Resultados do CI serão preenchidos após a validação. A Etapa 5 não foi iniciada.

## Uso e confirmação

Abra **Cadastrar Turma → Gerenciar alunos → Editar** (ou o perfil → Remanejar). Selecione **Remanejado**, informe a data efetiva e escolha uma turma compatível ou **Outra turma**, com o nome externo. **Conferir remanejamento** apresenta matrícula e encerramento da origem, destino, ingresso, contagem de notas/chamadas preservadas e efeito nos resultados. Só **Confirmar remanejamento** grava os dados. Voltar/cancelar a prévia não transfere ninguém; sair do formulário alterado exige confirmação de descarte. Durante a gravação, os controles são bloqueados. Erros mantêm o formulário e os dados anteriores.

A origem termina no dia anterior à data efetiva; o destino começa nessa data, sem sobreposição. Exemplo sintético: ingresso em 01/09 → origem até 31/08. Se existirem notas lançadas, chamadas P/F/FJ ou recomposições da origem na data efetiva ou depois, a operação é bloqueada para revisão, sem apagar ou deslocar os registros.

Na origem, o aluno fica **Remanejado**, oculto por padrão no cadastro, chamadas, novas notas, recomposição e relatórios correntes. **Mostrar remanejados (histórico)** permite consultá-lo, com destino e data. Essa opção não reativa matrícula nem habilita lançamentos individuais na origem. Presença coletiva, cópia de chamada e presença inicial também ignoram remanejados. Os registros de alunos Transferidos legados continuam seguindo seus períodos de matrícula; não são convertidos automaticamente em remanejamentos.

## Arquitetura: pessoa, matrícula e vínculo

Room **v3** acrescenta somente `relocations`:

- `sourceStudentId`: PK e FK restritiva para a matrícula de origem; uma saída por matrícula.
- `targetStudentId`: FK restritiva anulável e índice único; no máximo uma entrada por matrícula de destino.
- `effectiveDate`: data ISO da mudança.
- `externalTarget`: nome externo, usado exclusivamente quando não há matrícula de destino.

As nove tabelas v2 permanecem estruturalmente idênticas. `Student.id` continua sendo a matrícula; `personId` identifica a mesma pessoa nas matrículas vinculadas. Cria-se uma nova matrícula ativa ou vincula-se uma já existente **com o mesmo personId**, ativa, ingresso exatamente na data efetiva e sem outro vínculo. Nome semelhante com outra identidade não causa fusão automática. Número novo é o próximo disponível; notas e chamadas conservam os IDs antigos. Nome/observações de cada matrícula permanecem seus registros próprios; a identidade compartilhada e o vínculo tornam a continuidade explícita.

`previewRelocation` lê um snapshot consistente; `confirmRelocation` confere que nada mudou, revalida as condições e encerra origem, insere destino quando necessário e grava o vínculo em uma única transação. Qualquer falha reverte integralmente. Uma prévia obsoleta precisa ser refeita. Não há desfazer, exclusão ou redirecionamento silencioso de vínculo nesta versão. O histórico permanece disponível após reinício.

`DiarySnapshot.lineage` percorre somente antecessores explícitos, sem inferir vínculos pelo nome ou por personId legado. A → B → C consolida cada matrícula uma vez; retorno a uma turma já presente, ciclos, entradas duplicadas e saída repetida são recusados. A consulta histórica de A conserva A; a de B contém A+B; a de C contém A+B+C. Nunca inclui sucessores ao consultar a origem.

## Notas, frequência e recomposição

`DiarySnapshot.result`, `DiarySnapshot.frequency` e `SchoolReports` compartilham os vínculos e as regras aprovadas, sem uma segunda fórmula de relatório.

- Cada atividade mantém turma, data, máximo e notas originais. Só entra para a matrícula cuja turma e intervalo inclusivo de ingresso/saída cobrem a data. Os pontos das atividades elegíveis do mesmo bimestre são somados com os do destino, usando `SchoolRules.grades` e o limite da escala. Não há normalização implícita, limite 7,5 ou recuperação aditiva.
- A pontuação disponível é a soma dos máximos das **atividades únicas aplicáveis**, não de todas as atividades de duas turmas. Uma coluna de outra turma sem vínculo/aplicabilidade não gera nota, máximo ou pendência. Zero, pendente e não aplicável permanecem distintos. Atividades antigas elegíveis ainda sem nota continuam pendentes; não se presume que foram realizadas ou que valeram zero.
- Uma recomposição permanece no aluno/bimestre de origem, separada das atividades. Aplica-se o maior entre o original consolidado e essa única recomposição, limitado a 10, com a regra anterior para original indeterminado. O editor no destino permite corrigir/excluir explicitamente o mesmo registro herdado e informa sua origem. Não cria uma segunda recomposição no destino para aquele bimestre. Duas recomposições existentes, mesmo que uma seja não realizada, impedem a consolidação até revisão explícita.
- Frequência considera cada aula original, por matrícula e horário, com suas datas. Mantêm-se P/F/FJ/NR, cancelamentos e limites de matrícula. Duas aulas na origem e três no destino são cinco oportunidades reais, sem converter ordinais nem inventar presença. NR não vira falta. Percentual e faltas efetivas usam as funções existentes e a mesma política compatível de FJ.
- Relatórios do destino mostram as atividades herdadas com a turma de origem; o perfil e o individual listam matrículas e horários originais, inclusive NR/cancelados, identificando registros fora da matrícula sem contá-los. Original, recomposição e vigente continuam separados, com quatro bimestres e frequência anual, sem decisão anual de aprovação.

As projeções usadas pelos relatórios são reutilizáveis por uma futura exportação. Nenhum arquivo Excel/CSV/PDF, cópia ou disposição de exportação foi implementado. A seleção corrente de matrículas exclui Remanejados por padrão; o histórico não é apagado.

## Bloqueios deliberados

O destino interno deve ter **mesmo ano, disciplina normalizada, etapa, escala, quatro intervalos bimestrais e política de FJ**. Turno, duração e grade semanal podem diferir. Calendários, escalas ou políticas incompatíveis exigiriam decisões pedagógicas ainda não aprovadas: a operação é recusada, sem conversão silenciosa. Nome e cores de faixas podem diferir, sem mudar a fórmula.

Após vincular, alterações individuais nas datas/situação da matrícula ou nos critérios compartilhados das turmas são bloqueadas se invalidarem o histórico. Nome, número, observações e preferências não relacionadas continuam editáveis. Mover uma atividade com nota para fora da matrícula vinculada é bloqueado; recálculo proporcional e exclusão confirmada de atividade continuam usando as regras aprovadas e atualizando os resultados.

Não se faz fusão por semelhança de nomes. Para vincular uma matrícula preexistente, ela precisa já compartilhar a identidade, ingresso exato e condições seguras. Uma matrícula incompatível não é ajustada nem tem registros apagados automaticamente. O remanejamento no mesmo dia do ingresso é recusado, pois não produziria um intervalo válido de origem. Cadastros/transferências legados são preservados sem inventar um vínculo retroativo.

O destino externo registra apenas nome e data, encerra a origem e mantém seu histórico consultável. Não cria turma, matrícula, aula ou atividade fictícia. O nome externo deve identificar outra turma/escola, não a própria origem.

A origem vinculada é somente leitura para notas/chamadas individuais. Este incremento não acrescenta um editor específico de correções dessas notas/chamadas históricas, nem permite reativar uma matrícula vinculada. A edição explícita da recomposição herdada e alterações confirmadas de atividades permanecem disponíveis conforme descrito acima.

## Migração, backup e assinatura

`MIGRATION_2_3` cria `relocations` e seu índice único, sem reescrever as nove tabelas anteriores. A migração 1→2 anterior continua intacta e 1→3 usa os dois passos aditivos. JSONs v1 e v2 não mudam. Mantêm-se `br.com.diariodeclasse`, `diario.db`, preferências e funcionamento offline.

Backup **formato 4 / esquema 3** inclui as dez tabelas e a seleção. O leitor preserva os formatos **1/2 / esquema 1** e **3 / esquema 2**, atribuindo somente lista vazia de vínculos aos backups antigos. Valida checksum, identidades, chaves, cronologia, compatibilidade, ciclos e recomposições conflitantes antes de restaurar. A substituição integral continua exigindo prévia, confirmação `RESTAURAR`, cópia de segurança válida antes da escrita, transação única e verificação posterior. Restaurar um backup antigo remove os vínculos atuais como parte da substituição confirmada; eles permanecem na cópia anterior à restauração. Não há fusão silenciosa.

Não fazer downgrade para 0.6.1 sobre o banco v3. A 0.6.1 também não lê o novo backup formato 4. Guarde backup externo válido antes de atualizar; não desinstale para contornar assinatura incompatível.

O workflow manual `release.yml`, a configuração de assinatura e os Secrets permanecem intactos. O CI fixa a release **0.6.1 real**, execução `39049512375`, artefato `12106789781`, com metadados públicos em [baseline/noctua-release-0.6.1.json](baseline/noctua-release-0.6.1.json). O ensaio usa a mesma chave existente, preserva as nove tabelas antigas e recomposições, testa remanejamento, backup/restauração e reabertura no emulador descartável. Nenhuma chave permanente é criada/substituída. Nenhum dado escolar real é usado.

## Cobertura e entrega

Testes novos abrangem pontos antes/depois, zero/pendente/não aplicável, quatro períodos, recomposição única, destino externo, identidade compartilhada, matrícula existente e cadeia, origem oculta/consulta, horários diferentes, FJ/cancelamentos, incompatibilidades, ciclos, repetição, cancelamento, falha no meio da transação, prévia obsoleta, reinício, backup e migração v2→v3. A regressão anterior de cadastro, frequência, recálculo proporcional, recomposição e relatórios permanece exigida no CI.

Resultados, links e limitações de execução serão registrados após conclusão do GitHub Actions. A conferência física da versão nova cabe ao proprietário após os testes do emulador. A Etapa 5 continua aguardando autorização.
