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

## Continuidade E2/E3 (trabalho local em andamento)

A prévia compara base/local/remoto por identidade e campo; a decisão vale para
um conjunto consistente. Auditorias e revisões confirmadas são protegidas também
contra o estado remoto, além do estado local. Ausência em backup não é exclusão;
registros exclusivos são mantidos, distinguindo-os de tombstones remotos conhecidos.

Migração local 13→14 adiciona somente `sync_academic_decisions`, sem alteração das
24 entidades anteriores. Corpo de decisão imutável, estados PENDING/CONFIRMED/
APPLIED/STALE. Usuário confirma uma vez; fila existente retoma rede/processo, com
revalidação local e remota. Migração SQL preparada
`202611030002_phase_e_decisions.sql`: auditoria RLS append-only, CAS de cabeça e
revisões; referências base/remota derivadas das versões imutáveis do servidor.
Cancelamento depois de resposta perdida retorna o recibo original, sem desfazer
registro confirmado nem aplicar dados locais desatualizados.

PostgreSQL E2/E3 **15/15** executados localmente (não hospedado). Integração UI,
Room e dois AVDs ainda não aprovadas. Código E2/E3 permanece em desenvolvimento;
CI E1 é do commit `5bb0226` e não inclui essas alterações locais posteriores.

Marco local E2/E3: build debug/instrumentação e 272 testes JVM aprovados (273
casos, um LiveAuth condicional, zero falhas). Um teste detectou risco de aceitar
uma auditoria local já truncada: proposta agora compara imutabilidade também
contra o remoto confirmado. Outro ajuste corrigiu fixtures que copiavam uma
matrícula Remanejada para criar aluno novo; o bloqueio legítimo foi mantido.
Ainda falta prova Room/HTTP Android da decisão e restauração.

CI E1 `41757077066` (código `5bb0226`): atualização, Room D 15/15 e Room E1 7/7
passaram. O último cenário C parou na regra de entrada, sem encontrar a navegação
antes de testar o conflito. Harness trocava Auth em runner.onStart, depois de
Application.onCreate agendar jobs; AccountWorkspaces retém o controlador original.
Correção de teste antecipa a configuração ao callApplicationOnCreate, exercita
criação antecipada do manager e conserva timeout/assert original. Acrescentadas
capturas/estado sem tokens na falha de entrada. A regressão C precisa passar na
próxima rodada, sem ser omitida.

Provas E2/E3 preparadas: dez testes Room (prévia sem gravação, duas escolhas,
resposta perdida, alterações concorrentes, rollback SQLite, restauração protegida
e migração 13→14), diálogo com fonte nativa ampliada e continuação HTTP em dois
AVDs: decisão local após morte de processo, escolha remota, backup criptografado
antigo, nova matrícula remota ausente do backup e comparação final de hashes.
Essas provas ainda não estão aprovadas.

## Disponibilidade da referência original e assinatura

Artefato original correto localizado no workflow `41228898103`, commit
`36f58a239318c42f8293c6d0a4c7fa28747d865b`, artefato `13053517551`. APK
inspecionado com aapt/apksigner: `br.com.diariodeclasse`, 0.21.0/c40; SHA-256
`18335b1e7a640d733e9975d88d3f51a8cea4f73bd9e3bf536f32e90a40495a73`; certificado
`6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
ZIP SHA-256 `5b430a7d9b9d7f2d36c8f72ae02282d99c778d2da8273eb9e6bc29c6b8645bc9`.
O run antigo `40988203896` fornece c39, não deve ser confundido com c40.

Não há chave privada permanente disponível neste executor nem alteração da barreira
de release desta branch. O APK E debug não atualiza a instalação permanente.
A atualização original c40 e os dispositivos físicos permanecem pendentes; o CI
continua executando a referência isolada com chave descartável e os testes de
migrações Room, sem instalar no celular principal. Nenhuma chave foi solicitada.

## Validações locais anteriores à nova rodada

Build debug/instrumentação passou; JVM 274 casos, 273 aprovados, um LiveAuth
condicional, zero falhas. PostgreSQL E1 15/15 e decisões/reconciliação 15/15.
Asserções acadêmicas anteriores preservadas. Prova Android E2/E3 e regressão
completa ainda precisam do workflow posterior; não confundir com aprovação E.
Backup de conta nova agora persiste RESTORE com o titular já vinculado ao workspace
antes de gravar os registros, impedindo que uma inscrição futura envie um backup
antigo como se fosse criação nova. Não muda criptografia nem esquema acadêmico.

## Roteiro de homologação hospedada — preparado, não executado

1. Autorizar administrativamente ambiente/contas fictícias, preservando o projeto
   atual e as proteções de sessão v1 já instaladas. Não autorizar o UUID do
   proprietário nem remover a barreira do conflito legado. Conferir backup
   administrativo independente e contingência antes de qualquer migração real.
2. Revisar/aplicar uma vez, nesta ordem: A `202611010001_academic_protocol.sql`,
   B `202611020001_phase_b_fence.sql`, C `202611020002_phase_c_scope.sql`,
   D `202611020003_phase_d_scope.sql`, E1 `202611030001_phase_e_paging.sql`,
   E2/E3 `202611030002_phase_e_decisions.sql`. **Não reaplicar A/B/C/D depois de E**.
   Nenhum desses comandos foi executado no Supabase hospedado nesta rodada.
3. Executar `supabase/checks/phase_e_readonly.sql`: RLS nas três tabelas E, apenas
   SELECT próprio para authenticated, nada para anon, nada de escrita direta;
   auxiliares privados, RPCs autorizados e search_path fixo. Catálogo não prova RLS.
4. Com duas contas comuns autorizadas em instalações separadas, usar somente
   turmas fictícias. Confirmar sessões/e-mails e acionar consentimentos B→C→D→E.
   Testar leitura direta e RPCs com IDs conhecidos da outra conta, sessão revogada
   e token expirado; resultados e capturas não devem revelar tokens ou dados reais.
   Não compartilhar senhas, tokens ou links de recuperação pelo chat.
5. Repetir acervo grande, perda de conexão/resposta, morte do processo, dois
   sentidos, conflitos local/remoto e restauração protegida em aparelho separado.
   Verificar hashes acadêmicos, IDs, zeros/null, FJ, auditorias e revisões.
6. Só após esses resultados, validar candidato com certificado permanente sobre
   o APK original c40 em dispositivo isolado. Nada é instalado no aparelho principal
   e nenhuma release estável é autorizada por este roteiro.

Contingência: pausar teste, conservar bancos/filas/partes/recibos/auditorias, e
revogar a allowlist fictícia sob autorização administrativa se necessário. Não
rebaixar escopo, truncar tabelas nem substituir backups/criptografia.

Limites adicionais da conferência: 256 transações/64 MiB por prévia, pedido de
decisão 768 KiB e evidência servidor/corpo local de até 16 MiB. Excedentes bloqueiam
a operação conservando variantes. A decisão é por conjunto consistente; lotes
com novas atividades/aulas/matrículas/movimentações e dependências são agrupados.
Incompatibilidades de grafo e auditorias imutáveis continuam bloqueadas, sem
qualquer regra de última gravação vence.

### Continuidade E4 e segunda rodada Android

A execução `41760729100` passou a atualização isolada, as regressões HTTP B/C/D, os sete testes Room de paginação e os dez de reconciliação. Falhou em `e-lost`: a confirmação perdida do primeiro envio grande não foi observada. Não há aprovação HTTP E nem da regressão Android final nessa execução. O próximo ensaio preserva a asserção e acrescenta somente diagnóstico técnico sem conteúdo acadêmico, conta ou token.

O transporte agora exige revalidar a admissão da conta entre partes e antes do commit; uma fase HTTP interrompe após uma parte durável e exige que o servidor permaneça sem publicação, com a mesma outbox. A capacidade de transição E é calculada sobre o delta pendente, não sobre registros já confirmados: um acervo de 20.000 registros com uma alteração não deve ser recusado por seu tamanho total. Operações acima do limite continuam bloqueadas com mensagem específica. A nova suíte JVM executou 276 casos: 275 aprovados, um teste de Auth hospedado condicional ignorado, zero falhas. APKs debug e de instrumentação compilaram. Sem alteração hospedada ou instalação no aparelho do proprietário.
