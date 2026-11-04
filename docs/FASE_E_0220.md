# 0.22.0 — fase E: consolidação experimental

Base: branch `sync/academic-complete-0220`, HEAD documental `25c61e7`, código D
`9717146b6b5c63ac9aa560f57ac778efed654858`, CI `41527729515` aprovado (tentativa 2).
Working tree inicial: apenas `.agents/` não versionado; preservado. Main intacta.

## Plano incremental e critérios

1. **E1 — transporte paginado durável.** Reutilizar batches, versões, recibos,
   validadores D e fila Room. Novos RPCs de escopo E para enviar partes limitadas e
   confirmar um lote inteiro; receber partes imutáveis em armazenamento Room de
   estágio e só aplicar o grafo completo. Migração local aditiva para metadados,
   nunca alteração destrutiva das quinze tabelas acadêmicas. Cursor acadêmico
   permanece anterior enquanto o lote não for integralmente validado/aplicado.
2. **E2 — decisão explícita.** Revisão local/remota por campo e conjunto atômico,
   decisão com evidências persistentes e CAS remoto. Conservar conflito se mudar
   qualquer versão. Não oferecer sobrescrita unilateral de cadeia/auditoria.
3. **E3 — restauração.** Manter pausa RESTORE, comparar estado restaurado com remoto,
   reaproveitar a revisão/decisão E2; nunca tratar backup antigo como autoritativo.
4. **E4/E5 — continuidade/segurança.** Mesmo mutex, coordenador, JobScheduler e
   isolamento; PostgreSQL/PostgREST descartáveis, sessões fictícias, testes negativos.
5. **E6/E7 — compatibilidade/regressão.** Esquemas anteriores, backup criptografado,
   referência de atualização com chave descartável e dois AVDs. Preservar toda
   regressão A–D e leitores independentes de planilhas.

Cada marco deve registrar implementação, resultados efetivos, commits e limites.
Não declarar E concluída com base em testes de codec/SQL sem transporte Android.

## Segurança e implantação

- Nada aplicado ao Supabase hospedado. Migrações A/B/C/D continuam dependentes de
  revisão administrativa, seguidas das migrações E que forem efetivamente criadas.
- Consentimento experimental e allowlist permanecem; nenhuma conta real é ativada
  por atualizar o APK. O conflito legado do proprietário não é resolvido.
- Manter Room operacional, IDs/titularidade, package, assinatura e criptografia.
- Não instalar no aparelho principal, não publicar release nem promover main.
- Atualização original 0.21.0/c40 com certificado permanente, duas contas reais
  hospedadas e homologação física continuam pendentes até execução autorizada.

## Estado desta execução

Inspeção inicial confirmou a base D e sua CI aprovada. E1–E7 ainda não aprovadas.
Registros grandes exigirão limites explícitos por parte e por conjunto atômico;
ultrapassar um limite deve bloquear e preservar dados, nunca truncar/dividir cadeia.

## Contingência

Pausar o teste, preservar bancos, outbox/inbox, partes, recibos e evidências.
Corrigir aditivamente; não rebaixar o escopo, limpar filas ou reaplicar SQL antigo.
Não confundir sincronização com backup administrativo independente.

## Marco E1 — implementação preparada, Android ainda não aprovado

- Room 12→13 aditivo: somente `sync_academic_parts`, mantendo idênticas as 23
  entidades anteriores. Partes incompletas persistem com dono, sequência, offset,
  operação, contagem e digest; depois do commit, só os recibos/digests permanecem.
  Nenhuma alteração do formato lógico 12 ou criptografia do backup.
- Escopo 5 experimental explícito e persistente; D não escreve/lê no novo escopo.
  Client E usa os mesmos estados, outbox, inbox, engine, mutex e coordenador.
- Migração `202611030001_phase_e_paging.sql`, não aplicada no projeto hospedado.
  RLS nas duas tabelas de estágio, sem escrita direta; auxiliares privados;
  titularidade da sessão ativa/confirmada e allowlist existentes.
- RPCs `noctua_enable_academic_e`, `noctua_begin_academic_e`,
  `noctua_stage_academic_e`, `noctua_commit_academic_e`, `noctua_pull_academic_e`.
  O servidor só publica um lote completo depois de CAS e dos validadores A/D.
  Cliente confere recibos de partes e confirma cursor somente após aplicação Room.
- Limites explícitos: 128 registros/parte, 768 KiB/parte, 16.384 registros e 16 MiB
  por transação; uma linha histórica não é dividida. Até quatro transferências
  não confirmadas por conta. Acervos/linhas acima desses limites são bloqueados e
  preservados, nunca truncados. Transferências confirmadas são retidas no servidor;
  política administrativa de retenção/limpeza ainda requer revisão.
- Removidas leituras quadráticas de todos os metadados em cada linha: busca pela
  chave primária e índices em memória no mesmo contrato de revisão/CAS.

Executado localmente: PostgreSQL E1 **15/15** (inclui 5.171 registros dos 15 tipos,
partes incompletas, replay, sessões, RLS, auditorias e conflitos); JVM **266/267**,
um LiveAuth condicional não executado, nenhuma falha. APK debug e instrumentação
compilaram, incluindo a conferência dos recibos de partes.

Preparados, ainda dependentes do CI Android: sete testes Room E, migração 12→13,
e prova HTTP com dois AVDs (5.171 registros, retomada após morte real do processo,
edita nota no sentido inverso, diário offline/reconexão e conflito preservado).
As fases E2/E3 e a aprovação global E continuam pendentes.

Rodada E1 `41756048249`: PostgreSQL A/B/C/D e E1 passaram; Android parou na
sonda de atualização porque esperava Room 12 em vez de 13. Revisão encontrou
também a checagem fixa de esquema no BackupManager. Correção usa constantes
explícitas da versão/hash 13, preservando os hashes legados e todas as comparações
de dados. Backup lógico 12 e criptografia continuam inalterados. E1 ainda aguarda
nova rodada Android; nenhuma aprovação antecipada.
