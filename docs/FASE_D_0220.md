# 0.22.0 — fase D: cadeia acadêmica e históricos

Branch `sync/academic-complete-0220`; base C `258e4ceb4da9`, validada em
`41466557420`. Código D em validação `9717146b6b5c63ac9aa560f57ac778efed654858`.
Desenvolvimento **0.22.0-dev-d / código 43**. Main e candidata 0.21.0 preservadas.

## Estado e evidências

As provas D passaram na primeira tentativa do workflow
[41527729515](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41527729515),
mas a execução integral ainda aguarda a repetição do job interrompido pelo emulador
na sonda de reinício. **Ainda não é aprovação final do CI.** Não promover como
estável nem instalar no aparelho do proprietário.

Artefato da primeira tentativa:
[resultados-etapa6 / 13182257116](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41527729515/artifacts/13182257116).
SHA-256 do ZIP: `eb541f86044e63f3a56f1e9097e0839ae8175f43c0deaba977f6ad3214d59ad3`.
A tabela usa XMLs e saídas efetivamente inspecionados, sem somar novamente os
mesmos testes executados em foco e na regressão.

| Verificação local automatizada | Resultado da primeira tentativa |
|---|---|
| JVM, XMLs `testDebugUnitTest` | 261 casos: 260 aprovados, 1 condicional, 0 falhas/erros |
| Android completo, XML `TEST-emulator-5554 - 11-_app-.xml` | 402 casos: 392 aprovados, 10 condicionais, 0 falhas/erros |
| PostgreSQL protocolo A / B / C / D | 38 / 18 / 20 / 27 aprovados |
| Room em foco A / B / C / D | 3 / 12 / 10 / 15 aprovados |
| Painel: conflito e carregamento persistente | 2 aprovados |
| HTTP Android B / C / D, dois AVDs | 10 / 13 / 24 etapas aprovadas |
| Interrupção real durante recebimento D | Morte exigida, rollback e replay aprovados |
| HTTP/RLS D e segurança B | 32 e 9 verificações aprovadas |
| Leitor independente dos arquivos gerados no Android | 3 XLSX, 3 CSV e 9 TSV aprovados |
| Largura compacta 320 dp / fontes nativas 1,3 e 1,6 | B/C/D aprovados, capturas reais inspecionadas |
| Frequência após reinício do processo | escrita/leitura aprovadas |
| Autenticação após reinício do emulador | escrita aprovada; leitura não executada: emulador indisponível após reboot |

A contagem Android é a do XML final. O progresso do Gradle chegou a mostrar
412/402, mas o XML contém 402 casos; esse contador transitório não foi usado.
O JVM condicional é o teste LiveAuth, sem credenciais reais nesta execução.
Os dez condicionais Android são sondas que exigem argumentos/instalações próprias:
AuthProcess, AuthSignedUpgrade, FrequencyProcess, PhaseBNetwork, PhaseCNetwork,
PhaseDNetwork, ReleaseContinuity, StableUpgrade, SyncProcess e UpgradeProbe.
As sondas B/C/D, frequência e referência de atualização são executadas também
pelos roteiros dedicados; isso não transforma as sondas de assinatura permanente
não executadas em testes aprovados.

O PostgreSQL v1 tem 30 verificações aprovadas, preservadas na execução. As provas
locais não equivalem a Supabase Auth hospedado nem a homologação física.

## Implementação

As quinze entidades usam a mesma fila, engine, RPC e coordenador B/C. Sete tipos
adicionados: `lesson_records`, `relocations`, `weekly_timetables`, `class_archives`,
`preserved_histories`, `export_rows`, `import_drafts`. Não há envio de Room/backup
inteiro, arquitetura paralela, cálculo novo ou deduplicação por nome.

- Gatilhos INSERT/UPDATE/DELETE capturam alterações na própria transação escolar.
- Outbox conserva UUID, corpo, versões e tentativas até recibo real confirmado.
- Inbox conserva a página bruta em caso de conflito ou grafo inválido; cursor não
  avança. Recebimento, upserts, exclusões explícitas, recibo e cursor são atômicos.
- Validação recursiva exige campos/IDs mesmo quando o DTO Kotlin tem defaults.
  Não se geram identidades ao recuperar. Os serializers Room normalizam somente
  a comparação tipada; textos e digest da página bruta permanecem intactos.
- Remanejamento exige origem, destino quando interno, pessoa, datas e cadeia
  consistente. Calendários/escala/FJ, ciclos, recomposição por linhagem e auditorias
  seguem os validadores existentes. Não se recebe matrícula remanejada isolada.
- Auditorias são prefixos imutáveis inclusive ao mover o vínculo para um histórico.
  Remanejamento interno confirmado não pode ser alterado unilateralmente.
- Históricos são fragmentos autossuficientes ligados a matrículas preservadas.
  Não viram turmas/alunos vivos artificialmente. Dados históricos não são
  reescritos nem perdem aulas sem marcação ainda cobertas pela matrícula.
- Horários preservam revisões, slots e retratos de turmas excluídas, inclusive
  legados de fim de semana/períodos acima do sexto. Revisões confirmadas são imutáveis.
- Reordenação usa posições temporárias negativas apenas nas linhas alteradas,
  dentro da transação. IDs e linhas inalteradas não recebem UPDATE/DELETE; todas
  as restrições únicas continuam ativas. Após commit só há posições finais válidas.

A exclusão confirmada de turma segue `ClassLifecycle`: registros exclusivos
indicados na prévia são tombstonados, inclusive o diário daquela origem; vínculos
acadêmicos necessários às matrículas remanejadas sobrevivem nos históricos.
Não se inventam campos novos em HistoricalContent nem se alteram regras de exclusão.
As versões remotas e os recibos anteriores permanecem disponíveis.

## Escopo e segurança

Migração aditiva `202611020003_phase_d_scope.sql`: escopo 4 por conta, RPCs
`noctua_enable_academic_d`, `noctua_push_academic_d`, `noctua_pull_academic_d`.
A consulta `noctua_academic_status_c` continua informando escopo/revisão.

PREPARED_D é durável antes da rede; ACTIVE_D somente após confirmação. Exige
consentimento próprio, ausência de inbox/outbox impeditivas, cursor conferido,
admin allowlist de teste, debug, workspace não legado e UUID diferente do proprietário
legado. Contadores C ainda locais podem integrar uma movimentação completa na
transição; pedidos congelados/conflitos não são rebaseados ou apagados.

Clientes v1/B/C deixam de escrever depois da ampliação. A antiga autorização C
agora também bloqueia o rebaixamento de D. RPCs derivam titularidade da sessão
ativa/confirmada; não aceitam owner escolhido. Tabelas/RLS anteriores permanecem,
sem DML direto. Helpers privados têm EXECUTE revogado e search_path fixo; nenhuma
tabela temporária ou SQL dinâmico controlado pelo cliente. Nenhuma chave
administrativa foi adicionada ao APK.

## Provas e cenário

Fixture inicial de 171 linhas dos quinze tipos: duas turmas vivas, oito bimestres,
36 matrículas na origem e uma no destino, três aulas, 71 frequências P/F/FJ/NR,
atividades Produção textual, 36 notas zero/null/0,0001/positivas, recomposição zero,
FJ retirada com auditoria, conversão externa→interna, histórico anterior, dois
diários com acentos/texto longo/quebras, duas vigências, arquivamento, disposição
com linha vazia/ocultação e rascunho revisado. Histórico embute a cadeia anterior
sem recriar a turma excluída.

HTTP local é PostgreSQL/PostgREST real com JWTs/Auth fictícios, **não Supabase
Auth hospedado**. O teste não usa credenciais administrativas no cliente Android.

Roteiro Android: dois AVDs, consentimento pela interface, bootstrap, resposta perdida,
reenvio do mesmo UUID, recuperação exata, reordenação, edição inversa/offline,
reconexão sem reiniciar, alterações independentes, nova vigência, novo remanejamento,
exclusão da origem com resultados acadêmicos/hashes preservados e conflito de diário.

Recebimento interrompido: checkpoint exclusivo de debug termina o processo após
escrita de um pai e antes do commit. O script exige morte do processo e marcador;
na reabertura confere hash/cursor originais, teste pausado e inbox vazia, depois
retoma a página real. São 24 etapas normais e uma interrupção intencional; o crash
não é aprovação por ignorar uma falha. O sincronizador normal passa checkpoint
nulo e builds release rejeitam sua instalação. Há ainda rollback por falha SQLite,
com lote bruto conservado na inbox após reabrir.

## Correções da retomada e evidências

| Commit | Mudança comprovada |
|---|---|
| `55b6f08a9fd8` | Estado de conflito único para texto, spinner e retry; regressões de estado e captura com diagnóstico |
| `8be79ca612cd` | Captura C/D adquire o mesmo mutex por conta que o polling, evitando corrida entre espera e assert |
| `01f849281aa7` | Flows Room lembrados por workspace; carregamento distinto de protocolo inexistente |
| `9717146b6b5c` | Gatilho registra apenas metadado inexistente antes de incrementar a versão; regressões pelo repositório real |

No workflow original `41505368680`, o teste já havia comprovado o conflito e
falhou porque a captura exigia `academic-retry` habilitado. O produto precisa de
conferência do conflito antes de repetir uma operação incompatível: nesse estado,
**Conflito preservado**, sem spinner, retry desabilitado. Não se habilita reenvio
artificial nem se apagam variantes/cursores. O painel usava critérios diferentes
para texto e spinner/botão; agora usa a mesma condição, inclusive quando a inbox
chega antes da emissão do controle Room. A regressão prova esses estados e que
novas tentativas bloqueadas não geram novos envios.

Rodadas seguintes produziram diagnósticos distintos, tratados sem ampliar timeout:

- `41518744662`: polling legítimo iniciou entre a espera e a captura D. O mutex
  torna observação e pixels uma verificação consistente; a engine não é desativada.
- `41521165297`: controle ACTIVE_D/OFFLINE persistido, mas árvore exibiu ação v1.
  A recriação de Flows e a confusão entre nulo inicial/ausência de configuração
  permitiam essa troca. A regressão nativa força vinte recomposições e comprova
  que o painel D não retorna ao v1 nem ao carregamento após ficar pronto.
- `41524730086`: bootstrap D recuperou os quinze tipos, mas reordenação local
  encontrou `UNIQUE sync_academic_records(kind,key1,key2)`. No SQLite, o
  `INSERT OR ABORT` exterior prevalecia sobre o `INSERT OR IGNORE` do gatilho.
  O novo `INSERT ... SELECT ... WHERE NOT EXISTS` evita a colisão sem remover
  a chave única, excluir metadados ou reinicializar versões. Testes usam
  `DiaryRepository.saveExportLayout` e `saveLessonRecord`, incluindo OR ABORT.
  A normalização existente da disposição segue a ordem da lista; os testes
  passaram a fornecer a lista na ordem desejada, sem mudar essa regra.
- `41527729515`, tentativa 1: C 13/13, D 24 etapas + morte controlada, Room D 15,
  HTTP D 32 e regressão completa passaram. Após a escrita da sonda AuthProcess,
  `adb reboot` foi seguido de espera vencida (exit 124), sem conexão à porta 5554.
  Não houve falha JUnit. Repetido somente o job que falhou, no mesmo SHA, para
  verificar o reinício; a causa da indisponibilidade do processo emulador ainda
  não está demonstrada pelos logs existentes.

Evidências D: `build/phased-network/summary.txt`, arquivos `primary-d-*` e
`secondary-d-*`, `kill-checkpoint.txt`, `primary-d-crash-receive.txt`,
`primary-d-recover-receive.txt` e `http-security.txt`. O crash contém
`Process crashed`, o checkpoint confirma escrita de pai antes do cursor, e a
etapa seguinte exige rollback exato e replay. Não foi contado como ignorado.

Hashes acadêmicos iguais nos dois bancos independentes:

- Após a nova movimentação: `5cd88ef894c304abb5137857615f4c6709bd2f47b741b03d446fb5dcbaf1e565`.
- Após exclusão com histórico preservado: `d1f7bed22f74c497a7a0219d9e4bf149889c1f6cf2641ccced3549a1aa8a5457`.

Capturas reais estão em `build/phasec-layout.tar`, `build/phased-layout.tar`,
`build/phasec-network/primary-captures.tar` e nos dois arquivos `*-captures.tar`
D. Incluem recuperação, reordenação, recebimento retomado, nova movimentação,
histórico e conflito. Impeccable 4.7.0 foi consultado/aplicado ao contexto nativo,
clareza do estado e revisão visual; detector sem apontamentos. Não houve redesenho.

## Persistência e compatibilidade

Room **12**, hash `f7a581230efcfacc6e9659189abb0448`, mesma migração aditiva 11→12.
Não houve nova mudança de esquema. Backup lógico **12**, criptografia, isolamento
por conta, package `br.com.diariodeclasse` e configuração de assinatura inalterados.
Restauração continua pausando a sincronização para reconciliação explícita futura.

Atualização original assinada 0.21.0/c40: pendente; a referência de atualização do
CI usa chave descartável e não comprova o certificado permanente. APK debug só em
ambiente separado, nunca sobre a instalação do proprietário. Nenhuma release ou
promoção para main foi feita.

## Implantação hospedada e contingência

Nada foi aplicado ao Supabase hospedado. Preservar v1 e proteção de sessão já
instaladas. Após revisão/aprovação administrativa, aplicar uma vez e nesta ordem:
A `202611010001_academic_protocol.sql`, B `202611020001_phase_b_fence.sql`,
C `202611020002_phase_c_scope.sql`, D `202611020003_phase_d_scope.sql`.
Não reaplicar A/B/C por cima de D.

`supabase/checks/phase_d_readonly.sql` confere catálogo/grants/constraint, sem
conteúdo acadêmico. Não é prova de isolamento hospedado. Autorizar apenas contas
fictícias aprovadas e testar duas sessões comuns contra API direta e RPCs antes
de qualquer ensaio físico. Não usar o celular principal.

Se falhar: pausar teste e revogar allowed da conta fictícia; conservar perfis,
versões, recibos, filas e bancos. Corrigir aditivamente, sem rebaixar escopo,
restaurar SQL antigo ou remover conflitos.

## Limites e fase E

- Até 256 registros por lote/bootstrap, 1 MiB por pedido e 512 KiB por payload no
  servidor. Não fragmentar remanejamento/histórico. Acervos maiores continuam
  bloqueados; staging/volumes reais dependem da fase E.
- Conflitos conservam variantes, mas a conferência/resolução assistida e a
  reconciliação após restauração continuam pendentes.
- Migração do proprietário/v1 e conflito físico legado não foram resolvidos
  automaticamente. Dados reais não foram inscritos, enviados, apagados ou instalados.
- Supabase hospedado, duas contas Auth reais e homologação física permanecem
  pendentes. Catálogo administrativo e fixtures não substituem essas provas.
- Backup administrativo independente e recuperação após incidente precisam ser
  comprovados antes de mudar o mecanismo atual de backup/criptografia.

A fase E não foi iniciada. Nenhuma funcionalidade NOcTuA Escola foi implementada.

## Arquivos alterados na fase D e correções desta retomada

Relação contra a base C `258e4ceb4da9`:

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/assets/phase-d.json`
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicConflictPanelTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicNetworkCapture.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBPanelLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseCNetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseCPanelLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseCRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseDNetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseDPanelLayoutTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseDRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/SyncPanelReadinessTest.kt`
- `app/src/main/java/br/com/diariodeclasse/data/Database.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/SyncPanel.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicDatabase.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicEngine.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicPanel.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicStore.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/PhaseDGuard.kt`
- `app/src/test/java/br/com/diariodeclasse/PhaseCStatusTest.kt`
- `app/src/test/java/br/com/diariodeclasse/PhaseDGuardTest.kt`
- `docs/FASE_D_0220.md`
- `scripts/ci/test-phase-b-network.sh`
- `scripts/ci/test-phase-c-network.sh`
- `scripts/ci/test-phase-d-network.sh`
- `scripts/ci/validate-android.sh`
- `scripts/phase-b-http-fixture.py`
- `scripts/test-academic-postgres.py`
- `scripts/test-phase-b-postgres.py`
- `scripts/test-phase-c-postgres.py`
- `scripts/test-phase-d-http.py`
- `scripts/test-phase-d-postgres.py`
- `supabase/checks/phase_d_readonly.sql`
- `supabase/migrations/202611020003_phase_d_scope.sql`
