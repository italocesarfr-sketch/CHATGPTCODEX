# 0.22.0 — fase D: cadeia acadêmica e históricos

Branch `sync/academic-complete-0220`, base C `258e4ceb4da9`, código C validado
`c2e92f5c6cea`, workflow `41466557420`. Main e candidata 0.21.0 preservadas.

## Plano e critérios de segurança

Ampliar a mesma fila Room v12, transporte RPC e motor incremental para
`lesson_records`, `relocations`, `weekly_timetables`, `class_archives`,
`preserved_histories`, `export_rows` e `import_drafts`. Não criar uma segunda fila.

1. Validar recursivamente os DTOs persistidos, sem preencher IDs/defaults ausentes.
2. Capturar as quinze tabelas na transação escolar, somente no escopo D autorizado.
3. Receber lotes completos com validação de cadeia, auditorias e revisões imutáveis;
   aplicar filhos/pais sem cascatas e avançar cursor apenas depois do commit.
4. Ampliar SQL com escopo próprio e validação profunda, mantendo RLS, sessão,
   titularidade derivada e bloqueio dos clientes B/C antigos.
5. Testar Room, PostgreSQL/PostgREST e dois emuladores independentes; manter toda
   regressão anterior e verificar relatórios, backup e textos sem reescrita.

A fase permanece **em implementação, sem aprovação local D** até executar todas
as provas. Não confundir o contrato/codec de quinze tipos com sincronização validada.
Nenhuma migração hospedada, conta real, instalação física ou release está autorizada.

## Limites preservados

Bootstrap/lote até 256 registros e 1 MiB, sem divisão de remanejamento ou histórico.
Acervos maiores continuam bloqueados. Reconciliação assistida, staging maior e
migração de dados reais pertencem à fase E, que não será iniciada nesta rodada.
Backups, criptografia, isolamento, IDs e regras acadêmicas permanecem intactos.

## Continuidade

Registrar aqui commits, resultados efetivos, falhas, workflow e ponto exato de
retomada ao consolidar cada entrega. Supabase hospedado e homologação física
continuam pendentes, independentemente dos testes descartáveis locais.

## Marco de implementação (validação Android ainda pendente)

Cliente ampliado na arquitetura existente: quinze gatilhos transacionais, chaves
compostas estáveis, validação recursiva de todos os campos persistidos, serializers
Room, mesma outbox/inbox/recibos e aplicação atômica. Reordenação usa posições
negativas temporárias apenas nas linhas alteradas; IDs e linhas intactas permanecem
intocados. Valores temporários não deixam a transação nem entram na fila.

Escopo 4: PREPARED_D durável, consentimento próprio, checagem de cursor, permissão
administrativa de teste e bloqueios debug/workspace legado/UUID do proprietário
preservados. Escritores B/C e tentativa de rebaixar D por `enable_academic_c` ficam
bloqueados. Não existe ativação automática para dados reais.

Migração nova: `supabase/migrations/202611020003_phase_d_scope.sql`.
Reutiliza as tabelas/RLS existentes; helpers privados com search_path fixo, sessão
confirmada e principal derivado. Valida projeção viva+histórica sem recriar turmas
excluídas, ligações, identidade, datas, ciclos, calendários, recomposições por
linhagem, auditorias, registros retirados, horários imutáveis e disposição.
A auditoria anterior continua protegida ao migrar de linha física para histórico.

Room permanece 12 e esquema versionado idêntico; backup lógico 12 e criptografia
não mudaram. Desenvolvimento `0.22.0-dev-d` / código 43, sem release estável.

Provas locais executadas neste marco:

- JVM: 259 casos, 258 aprovados, 1 LiveAuth condicional ignorado; zero falhas.
- Build debug e APK de instrumentação: aprovados.
- PostgreSQL descartável v1: 30/30; A: 37 aprovados e um teste de wire condicionado
  ao caminho dos artefatos; B: 18/18; C: 20/20; D: 24/24.
- Cenário D inclui 36 matrículas na origem, destino com identidade da mesma pessoa,
  histórico anterior, FJ retirada com auditoria e conversão interna, zero/null,
  P/F/FJ/NR, dois diários, slots legados de sábado/12º e demais sete tipos.
- Inventário de APK: nenhuma chave administrativa, cliente fictício ou login
  demonstrativo detectado no APK do aplicativo. Fixtures ficam no APK de testes.
- Impeccable 4.7.0 lido: contexto, clareza e craft floor aplicados ao consentimento
  e painel existentes; detector sem apontamentos. Inspeção de pixels ainda pendente.

**Ainda não é aprovação da fase D.** Os testes Room/Android, dois AVDs, capturas e
regressão do workflow precisam terminar. Testes locais usam Auth fictício; não
comprovam duas contas reais no Supabase hospedado. Atualização assinada original
0.21.0/c40 e homologação física permanecem pendentes; não instalar APK debug no
celular do proprietário.

## Implantação hospedada — apenas roteiro, nada executado

Preservar migrações v1 e guard de sessão já instalados. Após revisão/autorização,
aplicar A `202611010001_academic_protocol.sql`, B
`202611020001_phase_b_fence.sql`, C `202611020002_phase_c_scope.sql`, D
`202611020003_phase_d_scope.sql`, uma vez, nessa ordem. Não reaplicar A/B/C por cima
de D. Verificar grants/RLS e só então autorizar contas de teste. Não há autorização
para executar esse roteiro no projeto hospedado nesta tarefa.

Contingência: pausar teste e revogar `allowed` da conta fictícia; preservar filas,
versões, recibos, perfis e bancos. Não rebaixar escopo, apagar conflito ou restaurar
SQL antigo. Migração de dados reais e reconciliação pertencem à fase E.

Marco salvo em `325432b34b38af76f27ee6a9324da7b509552b54` e publicado na mesma
branch. Rodada Android iniciada; resultados ainda não aprovados. Provas adicionais
preparadas: consentimento D pela interface real, reordenação via HTTP nos dois AVDs,
remoção de linha vazia, conflito de layout e verificação de que a linha inalterada
não recebe UPDATE/DELETE durante uma troca de posições.

As RPCs novas são `noctua_enable_academic_d`, `noctua_push_academic_d` e
`noctua_pull_academic_d`; a consulta existente `noctua_academic_status_c` informa
`academicScope=4`. Não é necessário criar uma segunda consulta de estado.
`supabase/checks/phase_d_readonly.sql` permite conferir catálogo e permissões,
sem consultar conteúdo acadêmico. Não substitui ensaio entre contas comuns.

Reforço posterior: históricos confirmados aceitam apenas a poda de componentes
sem matrícula preservada, conforme o domínio; não recebem dados inventados nem
perdem aulas/atividades ainda cobertas pelo período de matrícula. Duplicação de
período de aula no histórico também é rejeitada no servidor.

Resultados locais após esses reforços: JVM 260 casos, 259 aprovados e um LiveAuth
condicional ignorado; PostgreSQL D 27/27; A reexecutada com os artefatos wire:
38/38. HTTP real local enviou e recuperou 171 linhas dos quinze tipos, editou diário
e repetiu o mesmo UUID com confirmação idêntica; 32 verificações HTTP negativas de
RLS/RPC/sessão passaram. **Ainda não substitui os dois AVDs nem valida Supabase Auth
hospedado.** A autorização D ganhou rótulo conjunto de checkbox/descrição para
acessibilidade; novo cenário Android exercita esse consentimento real.

Prova Android ampliada para 21 etapas HTTP: além do bootstrap dos quinze tipos,
executa pelo repositório real um novo remanejamento interno e uma nova vigência de
horários; confere hashes acadêmicos iguais nos dois AVDs e depois exclui a origem
preservando duas matrículas remanejadas no histórico. O foco Room tem 13 cenários,
incluindo falha SQLite depois da escrita de um pai: rollback integral, lote bruto
na inbox e cursor preservado após reabrir. Essas provas aguardam o resultado do CI;
não foram declaradas aprovadas apenas por compilar.
