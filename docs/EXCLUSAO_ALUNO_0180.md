# NOcTuA Professor 0.18.0 — exclusão individual de matrícula

Base distribuída: 0.17.3 / código 32. Nova versão: 0.18.0 / código 33. Resultados finais de validação ainda pendentes.

## Uso

Editar aluno apresenta Salvar aluno à esquerda e Excluir aluno à direita. Em telas estreitas ou fonte ampliada, as ações passam a uma coluna sem diminuir a fonte. Excluir tem contorno, lixeira e cor de erro. Não aparece no cadastro novo nem como situação de matrícula.

Excluir abre uma única confirmação com nome persistido, turma, disciplina e ano. O diagnóstico conta apenas categorias existentes: atividades com notas (incluindo zero), lançamentos pendentes, frequências persistidas P/F/FJ/NR, recomposições com/sem nota, observações e vínculos/auditorias relevantes. Não confunde ausência de linha com NR. Não relaciona atividades ou horários compartilhados como se fossem apagados.

Não, Voltar do Android ou fechar o popup não gravam alterações. Sim, excluir só é habilitado depois da conferência, sem impedimentos. Não exige digitação ou segunda confirmação. Sucesso é informado somente após o commit; erros permanecem visíveis no popup. Se o aluno estava aberto no perfil, o aplicativo retorna à lista da mesma turma.

## O que é removido e o que é preservado

Somente o ID da matrícula selecionada e suas linhas exclusivas de notas, frequência e recomposição são removidos. O nome e o personId nunca são chaves de exclusão. Um homônimo — ou uma matrícula independente da mesma pessoa — em outra turma permanece intacto.

Atividades, pontuações máximas, horários de Chamada, anotações das aulas, Diário, turmas, rascunhos de importação, grade semanal, outras matrículas e seus lançamentos permanecem. Nenhuma presença ou falta é criada.

Quando existe posição personalizada para a matrícula, ela vira linha vazia e isso é informado antes de confirmar. IDs, posições, visibilidade e linhas vazias dos outros alunos não mudam. Sem posição salva, não se cria linha adicional.

## Impedimentos de integridade

A implementação bloqueia a exclusão individual de origens/destinos de remanejamentos confirmados, inclusive externos e vínculos contidos nos históricos de turmas excluídas. Também confere referências dentro das correções administrativas e frequências retiradas com auditoria. A matrícula não é removida se isso apagar/romper a história legítima de movimentação. Nenhum vínculo é dissolvido automaticamente.

Transferido administrativo, sem vínculo de remanejamento ou histórico compartilhado, pode ser excluído mediante a mesma confirmação. Matrícula marcada Remanejado sem vínculo verificável, referências indevidas de outra turma ou posições de exportação em conflito são bloqueadas com motivo específico. Não há fusão, reativação temporária ou exclusão por nome.

## Transação e concorrência

A prévia usa snapshot consistente das quinze tabelas. Confirmação relê o banco na transação Room e exige exatamente a mesma prévia/fingerprint. Inclusive alteração de valor sem mudança de contagem invalida a autorização. Conservadoramente, qualquer alteração do conjunto escolar depois da prévia pede nova conferência.

O resultado esperado é validado pelo codec existente de backup antes das escritas. A exclusão usa comandos restritos ao studentId e, ao remover a matrícula, também ao classId. Não há cascata indiscriminada. Foreign keys são conferidas e todo o conjunto restante é comparado com o esperado antes do commit. Falha intermediária ou divergência desfaz tudo.

Room v9, quinze tabelas, migrações anteriores e backup formato 11 permanecem inalterados. Não há nova migração. Pacote br.com.diariodeclasse, banco diario.db, funcionamento offline e configuração da assinatura permanente são preservados.

## Testes e entrega

Foram acrescentados testes JVM, Room, interface, telas compactas e fontes ampliadas. O teste de atualização usa o APK original assinado 0.17.3: semeia homônimos e registros antes de atualizar, preserva toda a base e só depois testa a exclusão direcionada, restauração e novo reinício.

O Impeccable 4.7.0 local foi consultado: contexto, hardening, craft e acessibilidade Android. A implementação usa componentes Compose/Material 3 existentes, sem dependências novas. Capturas reais e resultados finais serão registrados depois do CI.

Arquivos principais: data/StudentDeletion.kt, data/DiaryRepository.kt, ui/StudentDeletionDialog.kt, ui/Forms.kt, ui/RosterScreen.kt, ui/DiaryApp.kt, drawable/ic_delete.xml e app/build.gradle.kts. Testes: StudentDeletionTest, StudentDeletionInstrumentedTest, StudentDeletionUiInstrumentedTest, StudentDeletionLayoutInstrumentedTest e StableUpgradeProbeTest. CI: validate-android.sh, test-stable-upgrade.sh, android.yml e baseline/noctua-release-0.17.3.json. Caminhos Kotlin relativos a app/src/main/java/br/com/diariodeclasse ou aos diretórios de teste correspondentes.
