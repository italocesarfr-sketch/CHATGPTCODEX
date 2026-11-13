# H05 — instalação Android limpa e recuperação somente leitura

Preparação em **12/11/2026, America/Sao_Paulo**, branch `sync/academic-complete-0220`,
a partir de `eb16571d194d58fe13fcb24af4cb665429bcb3e5`.
> **Atualização 13/11/2026:** H05 hospedado foi autorizado e aprovado na
> [execução #201](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43433812685),
> para **24 registros dos nove tipos presentes**. Seis tipos estavam ausentes.
> A autorização dessa execução já foi utilizada; nova execução exige novo consentimento.

## Estado e escopo

H01/H02/H04 foram aprovados na #193; H03 na #194. Esses resultados não comprovam
recuperação Android limpa. H05 acrescenta essa prova, sem repetir aqueles testes.
A preparação usou identidades fictícias locais; a execução hospedada usou somente
a conta fictícia A autorizada. Nenhum dado real ou dispositivo existente foi utilizado.

O executor reutiliza AuthController, KeystoreAuthVault, AccountWorkspaces,
AcademicCoordinator.engine, AcademicEngine, AcademicStore, SupabaseAcademicSync,
serializers Room e validadores B–E. Não cria uma importação paralela ou backup de
banco para substituir a sincronização. O código está somente em androidTest/scripts
mais a seleção do APK instrumentado no Gradle; Room/SQL/app acadêmico não mudaram.

## Proteções

- Somente conta fictícia A extraída do Environment existente
  `noctua-homologacao-h01-h04`; nenhuma criação de usuário ou acesso à conta B.
- HOSTED exige variante `br.com.diariodeclasse.homologacao`, projeto/URL fixos
  `sscexzxtqdcsqdlcytum` e chave pública de homologação validada sem fallback.
- Exige conta confirmada/identidade esperada, elegibilidade e **protocolo 2,
  escopo E (5) já autorizado no servidor**. Se não estiver pronto, falha. Não
  chama activate/enable remotamente para preparar a conta.
- O consentimento local usa o método existente `AcademicEngine.activate()` depois
  da autorização H05. A conta já em E não precisa de alteração remota de protocolo.
- Decorador de transporte de teste permite apenas `status` e `pullE`. Todos os
  métodos de envio, decisão, staging e ampliação são bloqueados **antes** de
  alcançar o cliente HTTP. Qualquer tentativa invalida H05, mesmo sem envio.
- O runner só aceita máquina `github-hosted`, AVD exclusivo identificado pelo run,
  um único emulador esperado e app ainda não instalado. Instala sem `-r`; não
  usa pm clear/uninstall, restauração ou aparelhos externos.
- Secret JSON nunca é argumento de comando. Só A é transmitida via stdin para
  arquivo 0600 no sandbox UID do app. Logs de build, backend e instrumentação
  ficam privados, são removidos e nunca publicados. Não há prints de conta,
  UUID, token, payload, banco, screenshots, logcat ou APK em artefatos.
- Após o teste, remove entrada privada e encerra apenas sua sessão Auth; o novo
  AVD é descartado pela action. Não revoga as sessões dos emuladores do proprietário.

A Application de instrumentação controla o startup para não iniciar jobs/Activity
antes da autorização; o teste exercita engine/Room/workspace reais, não a navegação
visual de login. O app normal e seu startup permanecem inalterados. LOCAL usa
somente o Auth fictício já existente; não equivale a autenticação hospedada.

## Prova executada pelo Android

1. Verifica ausência de Room e sessão/titularidade anteriores.
2. Autentica A, valida UUID e confirmação, abre seu workspace real vazio.
3. Lê estado/protocolo e o histórico remoto E paginado, com limites explícitos;
   mantém IDs, revisões e tombstones. Exige a fixtureClassId conhecida de A.
4. Registra consentimento local e recupera pela engine existente, recebendo antes
   de preparar qualquer envio. Lotes continuam atômicos e validados por B–E.
5. Compara registros/identificadores/payloads normalizados, revisões, receipts,
   cursor, filas e chaves estrangeiras. Inclui todos os 15 tipos **presentes**;
   tipos ausentes têm contagem zero e `present=false`, sem falsa cobertura.
6. Lê novamente o remoto e exige cabeça, versões e lotes idênticos. Nenhuma
   escrita é delegada e não pode haver outbox, inbox ou conflito pendente.
7. Fecha/reabre o workspace Room, compara hash/cursor/filas e limpa a sessão.
   Isso prova reabertura do Room, não reboot ou morte forçada do processo.

O relatório só contém contagens por tipo, hashes SHA-256 agregados, tempos,
estágios/códigos fixos e booleanos de integridade. Não publica IDs individuais.
As comparações incluem zero/null, P/F/FJ/NR/motivos, diários, auditorias e históricos
quando existirem nos registros recebidos. Não inventa dados ausentes na nuvem.
Se outro dispositivo alterar a conta durante H05, o resultado é REMOTE_CHANGED;
pausar edições fictícias durante os poucos minutos do teste, sem apagar nada.

### Limites de segurança/custo

HOSTED não cria carga sintética. Limites do ensaio: até 1.000 registros por lote
histórico e vivos, 4.096 versões em cada inventário, 8 MiB por inventário,
128 páginas por inventário, 256 lotes e cinco minutos para a prova. As RPCs mantêm
seus limites originais. Um lote histórico de 5.171 registros é bloqueado antes de
recuperação integral; não se executa esse teste de carga no projeto hospedado.
Os limites são do ensaio, não mudança do protocolo/app ou do plano Supabase.

## Entrada manual independente

Usa `android.yml` registrado; não altera main. `h05_only=true` exclui H01–H04,
regressão geral, APK de distribuição e release. `authorize_hosted_h01_h04` não
autoriza H05. Não usa `secrets: inherit` para o novo job.

```bash
# Só formatos/configuração; sem Supabase ou emulador.
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h05_only=true -f h05_mode=PREFLIGHT

# Prova descartável local: sem Environment/secrets de conta, sem Supabase.
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h05_only=true -f h05_mode=LOCAL

# SOMENTE APÓS NOVA AUTORIZAÇÃO ESPECÍFICA; #201 já foi executada uma única vez:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h05_only=true -f h05_mode=HOSTED \
 -f authorize_hosted_h05=true -f hosted_auth_only=false -f homologacao_only=false
```

O proprietário não precisa fornecer novos Secrets: mantém as duas contas/fixture
no JSON já configurado e a publishable key correta. `allowZeroRowPermissionProbe`
não é consentimento de H05 e não provoca PATCH neste teste. Para autorizar depois:

> Autorizo H05 no emulador novo da CI, somente na conta fictícia A de
> noctua-homologacao, com login, consultas status/pull E, consentimento local,
> recuperação Room e logout da sessão criada. Nenhuma alteração acadêmica remota.

Se o Environment exigir revisão, aprovar o deployment no GitHub. Não enviar
senhas/UUIDs/JSON/tokens pelo chat. O consentimento de H01–H04 não é reutilizado.

## Resultados consolidados

**Código validado: `f9c4f22396bc12f221a0da9725bf19f68d34f888`.**

- [CI LOCAL #199](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43358380746):
  **aprovada**. Quatro testes Android executados, zero falhas/ignorados: recuperação,
  bloqueio dos 10 métodos de escrita, autorização independente e resumo por tipo.
- [Pré-verificação #200](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43358646359):
  **PREREQUISITES_READY_NOT_EXECUTED**. Secrets/formatos prontos; sem Java/AVD,
  sessões Auth ou chamadas Supabase. Não aprova H05 hospedado.
- 15 testes Python H05 + 16 do executor H01–H04: **31 aprovados**.
- JVM debug reexecutada com `:app:testDebugUnitTest --rerun`: **298 casos,
  293 aprovados, cinco condicionais ignorados, zero falhas**. Os cinco são
  SupabaseLiveAuthTest/H01–H04, não aprovados por esta rodada.
- Instrumentação debug compilada; instrumentação homologacao compilada com
  `-PnoctuaH05HomologacaoInstrumentation=true`, sem chave/contas ou execução real.
- actionlint 1.7.7 (sem ShellCheck/pyflakes), py_compile e diff --check aprovados.
- Regressão Android completa não foi reexecutada; a prova nativa foi focada nos
  quatro casos H05. Nenhuma regra/entidade/DAO/Room/SQL de produção foi alterada.

### Prova LOCAL de recuperação

[Relatório sanitizado](evidence/H05_LOCAL_20261112.json), extraído do
[artefato #199](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43358380746/artifacts/14007043539).
SHA-256 do ZIP: `5bbfb9a024f436990a1d7ff8aa57e32c1aa241e84f8ee0f539c652f19d383c21`.

A instalação sem Room/sessão anterior recuperou **171 registros**, **15 tipos** e
**37 matrículas** (36 na origem e uma de destino da mesma pessoa histórica),
com todas as identidades/payloads, revisões, referências e hashes iguais.

| Tipo | Registros recuperados |
|---|---:|
| classes / terms / students | 2 / 8 / 37 |
| lessons / activities / marks / grades | 3 / 2 / 71 / 36 |
| recompositions / relocations | 1 / 1 |
| import_drafts / export_rows / class_archives | 1 / 3 / 1 |
| lesson_records / preserved_histories / weekly_timetables | 2 / 1 / 2 |

- Cursor **2**, dois recibos de lotes atômicos; outbox/inbox/partes pendentes vazias.
- **Zero tentativas de escrita** pela engine; inventário remoto antes/depois igual.
- Segundo lote alterou nome de turma, preservando alunos e históricos.
- Reabertura do workspace, nova passagem da engine e verificação completa aprovadas.
- Entrada privada removida. H01–H04/release e job hospedado ficaram fora da prova.
- Hash global local/remoto:
  `62f371fdfecbb1e8b0016dc528a7410c6a1bc3d9e071442bf7010878ae2cabdd`.

Tempos **locais, não estimativas de Supabase Free**: recuperação pela engine **826 ms**,
prova completa **2.265 ms**, baseline **200 ms**, conferência **130 ms**, reabertura
**326 ms**. Transporte acumulado: **216 ms em 19 RPCs de leitura**, incluindo rede
local e decode; não isola tempo SQL no servidor. Compilação/boot do AVD não estão
nesses tempos. Auth nessa prova é **fictício**, com PostgreSQL/PostgREST reais.

### Pré-verificação dos Secrets

[Relatório #200](evidence/H05_PREFLIGHT_20261112.json) / [artefato](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43358646359/artifacts/14007880222).
SHA-256 do ZIP: `5ba0510135e682a19807a3338c8eb4f9c68ee8d9770057df8fe5abdc7467008d`.
Não foi necessário duplicar/configurar Secrets nem ampliar permissões. A prova
confirma formatos/disponibilidade; elegibilidade/escopo E e dados da conta A
foram posteriormente conferidos na execução hospedada #201 autorizada.
O preflight em si permanece uma prova offline, sem sessões ou recuperação.

## H05 hospedado — execução #201 aprovada

Executada uma única vez, com autorização explícita do proprietário, em
**13/11/2026**, exclusivamente em `noctua-homologacao` (`sscexzxtqdcsqdlcytum`),
conta fictícia A, AVD novo e app paralelo `br.com.diariodeclasse.homologacao`.

- [Workflow #201 / ID 43433812685](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43433812685).
- SHA executado: **`5e736b0562e18f415d72c7309c3d6ba47f3cfb2d`**;
  código igual ao `f9c4f22` validado localmente, com documentação posterior.
- **Um teste instrumentado aprovado; zero falhas/ignorados no ensaio H05.**
  H01–H04, LOCAL, regressão geral, APK de distribuição e release não foram executados.
- [Artefato sanitizado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43433812685/artifacts/14043330784)
  / [cópia persistente do relatório](evidence/H05_HOSTED_20261113.json).
- SHA-256 do ZIP: `20f97e4252efde46c0feb4344b8c936d4966dd998f8d59597335db6e399540b9`.
  Checksum conferido e conteúdo revalidado pelo schema sanitizado antes do registro.

| Tipo | Registros efetivamente recuperados |
|---|---:|
| Turmas (`classes`) | 2 |
| Bimestres (`terms`) | 8 |
| Matrículas (`students`) | 3 |
| Aulas (`lessons`) | 2 |
| Atividades (`activities`) | 1 |
| Frequências/justificativas (`marks`) | 4 |
| Notas (`grades`) | 2 |
| Remanejamentos (`relocations`) | 1 |
| Diário (`lesson_records`) | 1 |
| Recomposições, rascunhos, disposição de exportação, arquivamentos, históricos preservados e horários | 0 — ausentes no conjunto remoto |

**Total: 24 registros, nove tipos presentes.** Não criar dados para preencher os
seis tipos ausentes; eles não receberam cobertura hospedada de recuperação.
Ausência da entidade `preserved_histories` não significa exclusão do histórico
acadêmico: vínculos/remanejamento e os registros efetivamente presentes foram
recuperados, mas não se afirma uma prova adicional dessa entidade ausente.

### Integridade e estado observado

- Instalação sem Room ou sessão anterior confirmada; login real de A, confirmação
  de identidade e elegibilidade/protocolo E aprovados; consentimento local registrado.
- IDs/payloads, revisões e referências válidos e iguais ao remoto; hashes globais
  e por tipo coincidentes. Hash global:
  `e60421c6f9163c9cc0c46ffe660f53c642293d16a18bdb16ecbd8fdee7c15169`.
- Cursor **23**, **23 recibos**, zero tombstones; filas/inbox/partes pendentes vazias.
- **Zero tentativas de escrita acadêmica**. Inventário remoto/cabeça/versões e
  lotes iguais antes/depois; a instalação vazia não gerou exclusão/sobrescrita.
- Room fechado/reaberto, engine passada novamente e dados/estado revalidados.
- Entrada privada removida e etapa de limpeza do runner aprovada. Logout restrito
  à sessão criada pelo ensaio; nenhum logout global ou intervenção nos emuladores.
- Status final `PASSED_HOSTED_PRESENT_TYPES`, etapa `DONE`, sem código de falha,
  crash ou exceção na evidência sanitizada. Não houve nova tentativa automática.

| Medida no Android | Tempo |
|---|---:|
| Login | 1.473 ms |
| Inventário inicial | 2.341 ms |
| Recuperação pela engine, incluindo Room/HTTP | **2.980 ms** |
| Conferência local | 46 ms |
| Conferência remota posterior | 2.020 ms |
| Reabertura/revalidação | 259 ms |
| Prova completa, incluindo limpeza | **10.343 ms** |

Transporte de leitura acumulado: **6.956 ms em 79 chamadas status/pull E**, incluindo
rede/decode. Não é tempo de uma única função SQL nem inclui login/logout, build e
boot do AVD. Não confundir o tempo total com o limite SQL de oito segundos.
Nenhuma carga de 5.171 registros foi executada.

## Falhas corrigidas durante a preparação

As execuções locais #195–#198 **falharam** e não contam como aprovação.

- Um helper local `put` podia interceptar o campo `present` do builder JSON.
  A construção por tipo foi isolada e recebeu regressão; não se afrouxou o schema.
  A marca de autenticação só é registrada após conferir a identidade.
- A leitura `exec-out` não expunha corretamente a ausência do relatório nativo:
  texto de erro era tratado como conteúdo JSON. A leitura usa shell V2/base64,
  SHA-256 conferido no AVD e distinção entre arquivo ausente e schema inválido.
- A seleção da Application em `newApplication` dependia de argumentos ainda
  indisponíveis. H05 agora configura a Application existente em
  `callApplicationOnCreate`, como as fases B–E, antes de qualquer startup normal.
  Isso eliminou a Application incorreta e a ausência do relatório antes do teste.
- Falhas sem relatório preservam somente marcadores/nomes de exceção permitidos;
  nunca mensagens, stacks ou dados privados. A #199 executou os quatro casos.

Commits: `5ffdb6a` (executor), `f5aa045` (resumo/reabertura), `7d44e67`
(diagnóstico de schema), `bc88f45` (transferência byte a byte), `f9c4f22`
(ciclo de vida, código aprovado). O commit documental posterior não muda esse código.

## Arquivos alterados

- `AuthTestRunner.kt`, `H05RecoveryHarness.kt`, `H05RecoveryProbeTest.kt`,
  `H05ReadOnlyGuardTest.kt`: instrumentação e controles de leitura.
- `app/build.gradle.kts`: apenas escolha explícita do buildType da instrumentação;
  padrão debug, app oficial e configuração de assinatura preservados.
- `scripts/h05-recovery.py`, `scripts/test-h05-recovery.py`,
  `scripts/h05-local-fixture.py`, `scripts/hosted-auth-checks.py`: executor,
  privacidade/transferência, fixture descartável e reuso de validação de Secrets.
  O padrão de consentimento H03 continua obrigatório no executor H01–H04.
- `.github/workflows/android.yml`, `.github/workflows/h05-recovery.yml`: entrada
  manual independente, jobs LOCAL/PREFLIGHT/HOSTED, artefato único sanitizado.
- Este documento e `docs/evidence/H05_*_20261112.json`.

## Pendências e contingência

**H05 hospedado aprovado para os nove tipos presentes na #201.** Isso não aprova
os seis tipos ausentes, recuperação integral de qualquer acervo nem produção.

- Elegibilidade de A, protocolo/escopo E e recuperação dos tipos presentes foram
  confirmados na #201. Seis tipos permanecem sem prova hospedada por ausência de
  dados; qualquer ampliação/ensaio adicional exige planejamento e nova autorização.
- Não fazer mudanças SQL/allowlist para aprovar o teste. Se o estado não estiver
  pronto, preservar o resultado bloqueado e revisar a preparação com o administrador.
- Não editar a conta fictícia A durante a execução: divergência de cabeça/versões
  bloqueia aprovação em vez de sobrescrever dados.
- O ensaio LOCAL tem **zero tombstones**. Seu tratamento é mantido no recebimento,
  mas essa fixture não prova uma exclusão real nova. As fases anteriores têm
  regressões de exclusão; não anunciar nova cobertura hospedada de tombstones.
- Somente registros **confirmados no remoto** são recuperados. Pendências offline,
  variantes/conflitos locais e arquivos de backup de outro dispositivo não são
  magicamente recuperados. Não apresentar sincronização como backup administrativo.
- Tempos de Supabase Free e o risco de commit grande/timeout de 8 s permanecem
  fora deste teste somente leitura; nenhuma carga de 5.171 registros foi enviada.
- A Application de instrumentação controla jobs/startup para esta prova. Ela não
  homologa navegação visual, reboot do aparelho, assinatura permanente ou produção.
- Cancelamento/crash pode deixar uma sessão Auth efêmera ativa; não revogar sessões
  globalmente. Arquivos privados não são artefatos e o AVD/runner são descartados.
- Em falha, não repetir automaticamente HOSTED. Publicar somente o diagnóstico
  sanitizado e aguardar análise/autorização; LOCAL pode ser usado para regressão.

Esta execução está encerrada e sua autorização foi consumida. Não há ação manual
obrigatória para concluir a #201. Planejar separadamente a cobertura dos seis tipos
ausentes; não criar registros nem repetir testes hospedados sem nova autorização.
Nenhum segredo precisa ser enviado no chat. Main, produção, aparelhos existentes,
RLS e release intactos. O registro posterior altera somente documentação/evidência.
