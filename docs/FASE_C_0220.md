# 0.22.0 — fase C: avaliações e frequência

Branch `sync/academic-complete-0220`, base B `78aff91aa9d1`, auditoria B consolidada
em `aad093b`. `main` e candidata 0.21.0 preservadas.

## Estado da validação

**Fase C validada no ambiente automatizado local. Não homologada no Supabase
hospedado, nos aparelhos físicos ou para produção. Não é sincronização completa.**

Desenvolvimento **0.22.0-dev-c / código 42**. Código validado:
`c2e92f5c6cea4343830f477ac6fd1b0b6a6aa0cc`; lógica de produto em
`f76a18e265ad79ffcef4e2319eb2ea671a004412` (commits seguintes endurecem automação).

Workflow [41466557420](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41466557420)
aprovado nos dois jobs de validação; job de release estável corretamente ignorado.
[XMLs, logs e capturas finais](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41466557420/artifacts/13152954250),
SHA-256 ZIP `fce675c1a7ff15b68061d1ae147122fd6e75c3f21c543283d96daf7759e09ddf`.

| Prova final efetivamente executada | Resultado |
|---|---|
| JVM | 248 casos: 247 aprovados, 1 condicional ignorado, zero falhas |
| Android completo | 383 casos: 374 aprovados, 9 condicionais ignorados, zero falhas |
| Room B / C focados | 12/12 e 10/10 |
| PostgreSQL v1 / A / B / C | 30 / 38 / 18 / 20 aprovados |
| HTTP de segurança B | 9/9 |
| Android HTTP, dois AVDs independentes | B 10/10 fases; C 13/13 fases |
| Fontes reais 1,3 e 1,6 / largura 320 dp | aprovadas, capturas conferidas |
| XLSX/CSV/TSV, leitores independentes | aprovados |
| Backup/restauração e atualização da referência com chave descartável | aprovados |
| Reinício real do emulador / processos independentes | aprovados, inclusive AuthProcess read |
| Atualização assinada original 0.21.0/c40 | **pendente**, não executada nesta branch |

O teste JVM ignorado exige Supabase Auth real. Os nove condicionais Android são os
probes de AuthProcess, AuthSignedUpgrade, FrequencyProcess, PhaseBNetwork,
PhaseCNetwork, ReleaseContinuity, StableUpgrade, SyncProcess e UpgradeProbe. Os
scripts executam separadamente os probes de processo/referência/rede disponíveis;
os de release permanente não rodam aqui. Não somar as execuções focadas novamente
como casos únicos da suíte completa.

Capturas reais estão em `phasec-network/primary-captures.tar`,
`phasec-network/secondary-captures.tar` e `phasec-layout.tar`. A imagem final de
conflito foi conferida: sem spinner indevido, estado correto e botão alcançável.
Textos/ações no componente compacto foram conferidos com fontes 1,3 e 1,6.
Impeccable (contexto nativo, clareza, craft floor e detector) auxiliou a revisão;
o detector não substituiu os testes e a inspeção dos pixels.

O artefato [noctua-apk-teste](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41466557420/artifacts/13153423619)
contém **somente debug para ambiente separado**. `aapt` confirmou package
`br.com.diariodeclasse`, versão `0.22.0-dev-c`, código 42. APK SHA-256
`9fc61dc47b165d3192083483f6a6946bdd9ef083708c4746f38769f5bcc7854e`.
Certificado debug `118d8cca8748d2b86f49691e0dfb4845659f317cb198a14686555b7d2ea550ad`;
a conferência da referência distribuída apontou certificado diferente. **Não instalar
sobre o APK permanente do proprietário, não desinstalar nem limpar seus dados.**
Nenhuma release foi produzida ou promovida.

## Entidades e arquitetura

Sincronização incremental habilitada: `classes`, `terms`, `students`, `activities`,
`grades`, `lessons`, `marks`, `recompositions` — **oito das quinze entidades mapeadas**.

A mesma fila/engine/transporte da fase B foi ampliada. Não há upload integral do
banco, arquitetura paralela ou fórmula acadêmica nova. Room permanece **v12**,
mesmo hash e entidades físicas; gatilhos adicionais são instalados na abertura.
A migração B **11→12** continua aditiva. Backup lógico **12**, envelope criptográfico,
package `br.com.diariodeclasse`, titularidade e configuração da assinatura não mudaram.

- INSERT/UPDATE/DELETE atualizam contadores na transação da escrita escolar.
- Pedido preparado persiste UUID, corpo e versões imutáveis antes de HTTP.
- Somente recibo confirmado/recepção da operação confirmada remove a outbox.
- Recebimento inteiro, validação do grafo, upserts, recibo e cursor são uma transação.
  Falha conserva lote bruto na inbox, variantes, pendências e cursor anterior.
- Pais são aplicados antes dos dependentes; exclusões explícitas seguem ordem
  inversa e não podem deixar referências inválidas. Nenhuma restauração integral.
- IDs da pessoa/matrícula e número oficial são preservados. Zero, null, ausência de
  linha e P/F/FJ/NR/motivos continuam distintos. Textos não são reescritos.
- Coordenador único por workspace/mutex, invalidação Room, foreground, conectividade
  e JobScheduler existentes fazem retomada automática e reenvio idempotente.
- Restauração de backup pausa ambos os protocolos; reconciliação permanece etapa E.

## Consentimento e barreira de escopo

`202611020002_phase_c_scope.sql` acrescenta `academic_scope` (2 ou 3) aos perfis
existentes. Protocol continua v2. Novas RPCs:

- `noctua_academic_status_c`
- `noctua_enable_academic_c`
- `noctua_push_academic_c`
- `noctua_pull_academic_c`

Os RPCs reutilizam CAS, recibos, versões, cursores e grafo da fase A. RLS e
`noctua_sync_principal()` continuam exigindo sessão ativa/e-mail confirmado. O
proprietário vem da autenticação; não existe parâmetro para escolher outro owner.
Aliases privados permanecem sem EXECUTE para clientes; search_path é fixo.

A conta precisa de autorização administrativa de teste. O aplicativo exige debug,
workspace não legado, UUID diferente do proprietário legado e consentimento de dados
fictícios. A atualização do APK **não** envia automaticamente avaliações já locais.

Primeiro sincronizar B sem outbox/inbox/conflitos; depois aceitar a ampliação C.
PREPARED_C é persistido antes da rede e ACTIVE_C somente após confirmação do servidor.
A negociação confere revisão/cursor; B→C conserva todas as bases e recibos. Escritores
v1 continuam bloqueados pelo protocolo B; clientes B são bloqueados depois do escopo C.

Se o head B mudar durante o consentimento, inclusive em retry, a transição fica
preservada e bloqueada para conferência. Não adotar revisão nova, apagar metadados
ou rebaixar o estágio para contornar isso. Reconciliação assistida permanece pendente.
Uma instalação limpa reconhece o escopo C já autorizado e recupera sem novos IDs.

## Prova Android HTTP independente

O cenário envia/recupera **153 registros**: uma turma, quatro bimestres, 36 matrículas,
duas aulas, duas atividades, 36 notas (zero/null/positivas), 71 frequências e uma
recomposição zero. Há uma nota e uma frequência sem linha persistida, distintas
respectivamente de nota null e de NR. Categorias incluem Produção textual; FJ mantém
motivo opcional por matrícula/aula. Diário local fica intacto e não vai à rede.

As 13 fases cobrem escrita offline, perda de resposta após commit, reenvio do mesmo
UUID, reinício, recuperação em AVD independente, edição inversa, reconexão no mesmo
processo, edições independentes, conflito de nota e isolamento entre owners fictícios.
PostgreSQL/PostgREST são reais e descartáveis; JWTs/usuários Auth são fixtures locais.
**Isso não é integração com Supabase Auth hospedado ou duas contas reais.**

## Correções encontradas na auditoria

1. `36f86f1`: uma nova tentativa após inbox bloqueada definia SYNCING e retornava
   sem repor CONFLICT. Dados/cursor estavam seguros, mas o spinner podia permanecer.
   Agora o bloqueio é reconhecido antes de anunciar atividade. Regressões B/C
   conferem tentativas repetidas; prova HTTP verifica estado e botão no painel real.
2. `f76a18e`: uma sonda HTTP local comprovou que JSONB devolve `1.0E-4` como `0.00010`.
   Comparação JSON literal gerava falso conflito. Depois da validação estrita de
   campos/tipos, os próprios serializers Room normalizam apenas a comparação do
   payload. Textos/IDs/valores não mudam; lote bruto, inbox e digest/recibo continuam
   intactos. Não alterar hash histórico nem resolver conflitos antigos automaticamente.
   Testes JVM, Room de replay/digest bruto/recuperação e as 13 fases HTTP passaram.
   A nota 0,0001 integra envio, resposta perdida/reenvio e recuperação independente.

As falhas de automação foram diagnosticadas sem remover asserções: credencial
PostgreSQL descartável (`994afeb`), nome do runner (`9eb5c8a`), propagação assíncrona
da fonte nativa (`cd3e088`) e localização do ícone na gaveta (`c2e92f5`). O teste de
marca agora confirma a gaveta real e percorre a lista, mantendo as verificações do
ícone, pixels, tela inicial e configurações. Uma tentativa perdeu o emulador durante
reboot; o reboot e a leitura posterior passaram na execução final. A prova HTTP foi
antecipada, mantendo a regressão completa e seus asserts.

Commits de implementação/auditoria: `3d54d01` (C), `36f86f1` (estado de conflito),
`f76a18e` (números), `cd3e088` e `c2e92f5` (automação). Consolidação B: `aad093b`.

## Implantação futura e contingência — nada executado no hospedado

1. Confirmar v1 `202610300001_class_sync.sql` e proteção de sessão
   `202610310001_sync_session_guard.sql` já instalados; **não reaplicar**.
2. Após revisão/aprovação administrativa: A `202611010001_academic_protocol.sql`,
   B `202611020001_phase_b_fence.sql`, C `202611020002_phase_c_scope.sql`, nessa ordem,
   uma vez cada. Não reaplicar A/B depois de C: recriariam RPCs antigas.
3. Executar [phase_c_readonly.sql](../supabase/checks/phase_c_readonly.sql). Ele só
   lista catálogo/grants/políticas e agregados, sem UUIDs, dados acadêmicos ou segredos.
   No PostgreSQL descartável confirmou 13 tabelas com RLS, nenhuma escrita direta
   para authenticated, RPCs C sem EXECUTE anônimo e aliases privados sem EXECUTE
   para cliente. Esse inventário administrativo **não prova isolamento hospedado**.
4. Autorizar somente contas de teste explicitamente aprovadas. Usar dois emuladores
   limpos; nunca o celular principal. Ativar B, sincronizar pendências e consentir C.
   Contas legadas/UUID do proprietário continuam bloqueados até migração verificada.
5. Se falhar: pausar teste e revogar allowed apenas da conta de teste, preservando
   perfis, versões, recibos e bancos locais. Não restaurar SQL antigo nem rebaixar
   escopo. Guardar evidências; corrigir com migração aditiva e revalidar antes de retomar.

Não foram criadas contas reais, aplicadas migrações hospedadas ou solicitados tokens,
senhas/chaves administrativas. SMTP/domínio/pagamentos não são necessários ao mecanismo.

## Limites e próximo passo

- Hosted: aplicar SQL somente após aprovação e testar com duas sessões Auth comuns
  autorizadas, inclusive API direta e RPCs. Nenhuma prova hospedada foi executada.
- Physical: homologação e atualização assinada original 0.21.0/c40 continuam pendentes.
  APK debug de teste não substitui a instalação assinada do proprietário.
- Bootstrap conservador: até 256 linhas/1 MiB; staging maior e migração de dados reais
  dependem da fase E. Não enviar subconjunto silencioso de coleções maiores.
- Matrículas remanejadas/com cadeias protegidas continuam bloqueadas até a fase D;
  não recuperar uma matrícula sem seus vínculos. Conflitos legados não são apagados.
- D pendente: `lesson_records`, `relocations`, `weekly_timetables`, `class_archives`,
  `preserved_histories`, `export_rows`, `import_drafts`. Diário/históricos/preferências
  ainda não são recuperáveis pela conta. D não foi iniciada nesta execução; é o próximo escopo técnico após revisão desta entrega.
- E pendente: resolução explícita de conflitos, reconciliação após restauração,
  bootstrap maior, titularidade/migração real e consolidação de segurança.
- Manter backups/criptografia; sincronização parcial não é backup ou recuperação integral.
  Não promover main nem gerar release estável.

## Arquivos desta fase

Lista relativa à consolidação B `aad093b` (inclui correções e documentação):

```text
.github/workflows/android.yml
README.md
app/build.gradle.kts
app/src/androidTest/java/br/com/diariodeclasse/AuthTestRunner.kt
app/src/androidTest/java/br/com/diariodeclasse/OfficialBrandInstrumentedTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBPanelLayoutTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseBRoomTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseCNetworkProbeTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseCPanelLayoutTest.kt
app/src/androidTest/java/br/com/diariodeclasse/PhaseCRoomTest.kt
app/src/main/java/br/com/diariodeclasse/sync/SyncPanel.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicDatabase.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicEngine.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicPanel.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRecords.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicStore.kt
app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicTransport.kt
app/src/test/java/br/com/diariodeclasse/PhaseCStatusTest.kt
docs/FASE_B_0220.md
docs/FASE_C_0220.md
docs/PLANO_SINCRONIZACAO_0220.md
scripts/ci/test-phase-b-network.sh
scripts/ci/test-phase-c-network.sh
scripts/ci/validate-android.sh
scripts/phase-b-http-fixture.py
scripts/test-academic-postgres.py
scripts/test-phase-b-postgres.py
scripts/test-phase-c-postgres.py
supabase/checks/phase_c_readonly.sql
supabase/migrations/202611020002_phase_c_scope.sql
```
