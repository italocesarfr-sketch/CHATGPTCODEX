# Sincronização acadêmica 0.22.0 — plano de execução

Base: código `36f58a239318`, documentação `340f6194259b`, 0.21.0/c40.
Branch: `sync/academic-complete-0220`. Não promover main nem publicar release estável.

## Evidência de homologação que motiva a evolução

O proprietário relatou recuperação de turma entre celular e emulador, edição offline
e envio após reinício. O retorno de uma edição do emulador foi bloqueado no celular
com `Turmas: conflito preservado`. O código confirma que a presença de qualquer
matrícula, nota, aula, diário ou grade bloqueia inclusive uma mudança de nome.
Isso não comprova defeito nos registros protegidos: o protocolo v1 só conhece
turma/bimestres. O conflito existente deve permanecer registrado até revisão
explícita; esta branch não acessa os aparelhos do proprietário.

## Fases e dependências

1. **A — protocolo acadêmico verificável.** Mapear as 15 entidades Room, preservar
   suas chaves e representar cada linha como registro tipado, com revisão própria.
   Alterações dependentes formam um lote atômico, identificado por UUID. Criar
   migração SQL aditiva, RLS desde a criação, titularidade exclusiva derivada da
   sessão, recibos idempotentes, versões imutáveis, tombstones e cursor por lote
   confirmado. Testar RPCs e tabelas com papéis comuns no PostgreSQL descartável.
   Implementar codec/diff/revisão no cliente, sem ativar transporte acadêmico.
2. **B — captura Room e matrículas.** Migração aditiva para outbox/metadados,
   gatilhos transacionais, snapshot consistente e envio incremental. Validar
   recuperação de turmas, quatro bimestres e matrículas em instalação independente.
3. **C — avaliações/frequência.** Incorporar atividades, notas, recomposições,
   aulas, P/F/FJ/NR e motivos. Manter zero, null e ausência de linha distintos.
4. **D — cadeia acadêmica completa.** Diários, remanejamentos e suas auditorias,
   históricos de origens excluídas, arquivos de turma, disposição de exportação,
   rascunhos e revisões de horários. Validar operação inteira antes de aplicá-la.
5. **E — consolidação.** Inicialização consentida, recuperação paginada completa,
   resolução auditável de conflitos, conectividade/foreground/retry com singleflight,
   indicadores verdadeiros, regressão Android, atualização assinada e homologação
   hospedada com duas contas autorizadas. Só então avaliar automação por padrão.

Uma fase não está pronta apenas porque possui tabelas. A requer protocolo executável
e testes; B–E requerem integração efetiva no Room e recuperação entre instalações.

## Mapeamento e ordem de dependência

| Entidade Room | Chave preservada | Dependências / tratamento |
|---|---|---|
| classes | id | Configuração, ano letivo; não há tabela separada de anos |
| terms | classId, number | classes; quatro bimestres no conjunto válido |
| students | id | classes; personId preservado, matrícula diferente de pessoa |
| lessons | id | classes; unicidade turma/data/ordinal |
| activities | id | classes; categorias existentes, sem resultados derivados |
| marks | lessonId, studentId | lessons/students; NR persistido difere de ausência |
| grades | activityId, studentId | activities/students; null difere de zero |
| recompositions | studentId, term | students; regras atuais |
| relocations | sourceStudentId | students origem/destino; auditoria aninhada integral |
| import_drafts | id | classes; conferência persistida, não matrícula |
| export_rows | id | classes/students opcional; disposição persistida |
| class_archives | classId | classes |
| lesson_records | id | classes; conteúdo independente da chamada |
| preserved_histories | classId | conteúdo histórico autossuficiente; sem FK para turma excluída |
| weekly_timetables | id | slots versionados com dados históricos; sem FK física para turma excluída |

Não sincronizar sessões/tokens, seleção de tela, cache, estado de menus, fila do
próprio sincronizador, resultados calculados, arquivos de backup ou material de
criptografia. Não criar novas fórmulas acadêmicas nem normalizar históricos JSON
de maneira que quebre os backups e as auditorias existentes.

## Contrato e proteções

- Chaves compostas são objetos JSON tipados, nunca nomes nem strings concatenadas
  ambíguas. IDs legados continuam válidos; não exigir UUID dos registros antigos.
- Titularidade está em todas as chaves remotas e relações. Nenhuma RPC aceita
  owner_id. Reutilizar o guard de sessão ativa, conta confirmada e JWT vigente.
- Por registro: comparar revisão conhecida com revisão atual. Lote com conflito
  não aplica nenhuma linha; preservar pedido e versões existentes. Reenvio do mesmo
  UUID retorna o mesmo recibo; reutilização com outro conteúdo é erro.
- Cursor por lote confirmado, com serialização por proprietário, impede perda
  de operações e separação de alterações dependentes entre páginas.
- Tombstone não apaga a versão anterior. Exclusão de pai com filhos vivos deve
  falhar, inclusive quando IDs iguais existirem em outra conta.
- Mesclar automaticamente apenas mudanças comprovadamente independentes, com base
  conhecida, como nome de turma. Validar o grafo acadêmico proposto antes de gravar.
  Notas, frequência, datas e movimentações conflitantes não usam última gravação vence.
- Nunca remover o conflito v1 existente por inferência. Revisão explícita posterior
  deverá preservar suas variantes e registrar a decisão.

## Migração inicial e coexistência

A fase A não liga o protocolo novo no aplicativo nem envia dados. Os RPCs v1 e as
quatro tabelas existentes permanecem intactos. Antes de ativar um cliente v2 será
necessário implementar uma barreira por proprietário para impedir gravações v1
concorrentes, comparar o estado v1 com Room e preparar envio inicial por lotes com
integridade global. Uma implantação SQL isolada não autoriza essa transição.

Não modificar Room v11, backup lógico 12, envelope criptográfico, titularidade ou
assinatura apenas para preparar o protocolo. Alterações futuras devem ser aditivas,
testadas na atualização da 0.21.0 e manter o banco anterior íntegro. Backup continua
necessário: sincronização parcial não é recuperação integral nem backup de incidentes.

## Evidências exigidas antes de liberar candidato

Teste local de papéis SQL não substitui JWT real, PostgREST hospedado, duas contas
autorizadas e homologação física. Não há credencial administrativa nem sessão real
disponível nesta execução. Não criar contas adicionais nem aplicar SQL hospedado
automaticamente. Entregar migrações revisáveis, resultados locais e pendências claras.
Sem B–E aprovadas, manter versão instalada/candidata 0.21.0, sem APK 0.22.0 anunciado
como sincronização completa.
