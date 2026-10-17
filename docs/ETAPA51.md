# Etapa 5.1 — Organização dos alunos para exportação

Noctua **0.8.0 / versionCode 13**, base 0.7.1 / 12 aprovada pelo proprietário em aparelho físico. Este incremento não autoriza 5.2. Aplicativo offline, mesmo pacote `br.com.diariodeclasse`, banco `diario.db` e configuração de assinatura permanente; nenhuma chave ou Secret criado/modificado.

## Uso e contrato

Em Cadastrar Turma → Gerenciar alunos, **Ocultar**, à esquerda, ativa caixas de seleção. Marcar significa ocultação manual das futuras exportações/cópias. As caixas refletem os estados manuais salvos; **Concluir** salva todas as alterações em uma transação, **Cancelar** descarta a sessão. Alunos permanecem visíveis na gestão com identificação discreta. Fora do modo de seleção, edição e histórico continuam acessíveis.

Trocar turma/ano, página, voltar ou abrir backup com alterações exige confirmação de descarte. Durante a gravação, controles e navegação de exportação aguardam o resultado; erros aparecem sem descartar o rascunho. A rolagem preserva a seleção. O estado da sessão acompanha recriação da Activity pelo Android; o banco só muda em Concluir. Encerramento forçado/limpeza de dados pelo sistema não garante rascunhos não confirmados: conclua antes de encerrar o processo. Preferências confirmadas persistem após reinício.

**Organizar exportação** usa Subir/Descer, Linha abaixo, Adicionar linha vazia ao final e Remover linha vazia. Não altera números nem ordem da chamada. Ver prévia mostra a futura sequência e, separadamente, os alunos excluídos com seus motivos. Ocultos não ocupam linhas na saída; espaços são mantidos apenas pelas linhas vazias explícitas. Linhas ocultas mantêm posição na configuração para reversão posterior.

Remanejado na origem é excluído automaticamente pelo status/vínculo, independentemente da caixa manual. O motivo mostra destino/data quando disponíveis. Desmarcar ocultação manual não desfaz remanejamento. A matrícula ativa criada no destino começa incluída; eventual escolha manual numa matrícula de destino já existente é preservada. Uma transferência administrativa simples não ganha nova regra de exclusão automática. Mostrar remanejados continua permitindo consulta histórica na origem.

## Arquitetura e preservação

`data/ExportLayout.kt` contém a projeção compartilhada `ExportSlot` e a preferência `ExportRow`. IDs são das **matrículas**, não do nome nem da pessoa. A linha de aluno usa `student:<ID>`; uma linha vazia tem UUID próprio e matrícula nula. Renomeação, homônimos e mudança dos números da chamada não mudam as posições salvas. Alunos novos são acrescentados no final. Ao salvar, novas matrículas que surgiram durante a edição também são acrescentadas; personalização existente não se desloca. Um editor com configuração persistida já alterada é recusado, evitando sobrescrita silenciosa.

`ui/ExportLayoutScreen.kt` apresenta o organizador simples e o rascunho serializável, reutilizado pelo modo Ocultar de `RosterScreen`. O guard de navegação vive em `DiaryApp`. `saveExportLayout` no Repository valida referências/posições e altera exclusivamente as preferências da turma em transação Room. A leitura não materializa preferências nem modifica registros escolares. Chamadas, atividades, recálculo proporcional, recomposição e projeções escolares não usam a ocultação.

A futura 5.2 poderá consumir `ExportLayout.rows/project` para obter sequência, matrículas, ocultação e espaços e combinar isso às projeções escolares existentes, sem criar uma segunda lógica de notas/frequência.

## Room e backup

Migração **3→4 aditiva**: cria somente `export_rows` (id, classId, studentId opcional, position, manualHidden). Chaves estrangeiras RESTRICT para turma/matrícula, índices únicos para posição por turma e matrícula. Os dez esquemas de tabelas anteriores permanecem idênticos; JSONs v1/v2/v3 preservados. Migrações 1→2→3→4 registradas, sem fallback destrutivo. Novos bancos também usam v4.

Backup **formato 5 / schema 4** inclui `exportRows`; leitura dos formatos 1–4 preservada. Backups anteriores não contêm a disposição: a prévia usa ordem padrão até salvar. Restaurar um backup antigo é substituição integral **explicitamente confirmada**, portanto remove apenas as preferências que aquele arquivo não contém, além de restaurar seu estado escolar original; cópia privada de segurança anterior continua obrigatória. Validação rejeita referências entre turmas, duplicatas, posições inválidas e linhas vazias malformadas antes de substituir dados. Restore é transacional com conferência integral posterior.

## Testes e compilação

Local: JDK 17 / SDK 35, `testDebugUnitTest assembleDebug assembleDebugAndroidTest` compilados com sucesso; 72 testes JVM aprovados. Testes Android exigem emulador e são executados no GitHub Actions. Evidências finais do CI serão acrescentadas após conclusão.

Cobertura nova: seleção múltipla, desmarcação/cancelamento, guard de navegação, rolagem/recriação, ordem/linhas vazias/prévia, renomeação/homônimos/novos alunos, exclusão automática independente e destino incluído, atomicidade em falha, editor desatualizado, migração 3→4 com comparação das dez tabelas, backup 5 e leitura 4, dados escolares imutáveis e relatórios com zero/recomposição/frequência inalterados. Fixtures exclusivamente fictícias.

O CI preserva a configuração release existente e usa o APK real 0.7.1 do [run 39067364492](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492), hash/certificado públicos fixados em `docs/baseline/noctua-release-0.7.1.json`. As sondas verificam atualização sem desinstalação, mesmas IDs/registros/preferências, migração, backup antigo/novo e reinício real do processo. A sonda de frequência mantém a regressão Transferida → Ativa → reinício → Remanejada e preferências confirmadas.

Primeiro ensaio Android: [run 39072465323](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39072465323), com 116 aprovados e quatro falhas de interface. Corrigidos os seletores (árvore semântica não mesclada e botão Cancelar restrito ao diálogo), a rolagem explícita para consultar o cartão remanejado após os novos controles e a espera de ociosidade antes de rolar o relatório individual. Nenhum teste foi retirado e as regras escolares permaneceram iguais. A suíte completa é repetida antes da entrega.

## Limites

Sem XLSX, CSV de saída, cópia tabulada, PDF, registros de aula ou identidade visual nova. PDF retirado da versão 1.0 pelo proprietário; poderá ser produzido no Excel a partir do futuro XLSX. Importação CSV existente permanece. Sem mudança de política de matrícula, remanejamento, notas ou frequência. Testes em API 30 não substituem validação física da 0.8.0 nem cobrem todos os fabricantes/API 26+. O APK debug não é compatível por presunção com a instalação release; somente distribuir a release após prova de certificado e atualização no CI. Fazer backup antes de atualizar.
