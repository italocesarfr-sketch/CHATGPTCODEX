# P1 — recuperação automática pela UI em dois AVDs

Continuação de [HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md](HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md).
Somente preparação/local nesta entrega; P1 hospedada, P2 e P3 não autorizadas.

## Etapa 1 — APK paralelo atualizado

[Build #208 / 43632816764](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764),
`success`, fonte `6f3ae05f8ccdd918600f99c2ef5b6c6ee6d15474`, cujo código coincide
com o aprovado `b8206c3`. Workflow `homologacao.yml` via dispatcher existente,
sem login hospedado. [APK paralelo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764/artifacts/14128872479).

- `br.com.diariodeclasse.homologacao`, NOcTuA Homologação.
- `0.22.0-dev-e2-homologacao` / código 45, preservados.
- SHA-256 APK `3aa87abdc60f2f42184ca9a9a90a5783d3c09e97d60ab8a9954cea191252bc35`.
- Certificado Debug `8e9b7b97b14914182d42a6be2e1ed0f32a92a48926cbc94288a983ae9e2676a5`.
- Assinatura/AAPT/DEX/relatório de identidade conferidos offline. Endpoint exclusivo
  `sscexzxtqdcsqdlcytum`, callback próprio; novas funções presentes e marcadores
  privados/produção ausentes. O vínculo da chave opaca não foi consultado online.
- Certificado diferente do #206: **não atualiza aquela instalação**. Nenhum APK
  foi instalado nesta etapa; usar somente AVDs novos, jamais limpar/desinstalar
  aplicações existentes para contornar incompatibilidade.
- JVM Debug: 313 aprovados, cinco condicionais ignorados; Homologação: os mesmos
  313 aprovados/cinco ignorados, zero falhas. Não somar como 626 casos distintos.

[Identidade e hashes estruturados](evidence/APK_P1_208.json).

## Etapas 2/3 — executor

Executor de instrumentação separado do aplicativo, via `P1RecoveryProbeTest` e
`P1RecoverySupport`. A Application executa seu `onCreate` normal, com cofre
Keystore vazio e autenticação por `entry-email`/`entry-password`/`entry-enter`.
O teste não chama `activate()`, `run()`, `request()` nem controles de recuperação.
A recuperação pertence ao startup/coordenador aprovado. O logout usa a UI.

Dois AVDs novos, nomes vinculados ao run/attempt, instalações sem `-r`; qualquer
pacote previamente instalado, dispositivo extra ou runner não descartável bloqueia
o executor. Nenhum `pm clear`, uninstall ou instalação em dispositivo existente.
Cada AVD verifica A → B → A, inventário/revisões/recibos/filas/cursor/FKs,
hashes por tipo e imutabilidade do inventário e auditorias remotos.

A barreira reutiliza `H05ReadOnlyRemote`, que recusa as dez operações mutantes
antes de delegar ao HTTP. Somente status/pull E e SELECTs fixos de auditoria e
isolamento são permitidos; Auth admite exclusivamente login/verify/refresh/logout
das próprias sessões. Cadastro, recuperação, alteração de senha e troca PKCE
não são permitidos neste ensaio. H05/H12 continuam inalterados.

LOCAL ignora Secrets e utiliza Auth sintética com PostgreSQL/PostgREST reais,
fixtures A=171 registros/15 tipos e B=6 registros/3 tipos. O caso B vazio/E/head=0
possui guarda adicional própria; não relaxa os limites do H05. HOSTED exige
contas confirmadas, protocolo 2/escopo E/allowlist em tempo de execução, sem
ativar escopos remotamente. A ausência de elegibilidade bloqueia a prova.

Limites: 1000 registros vivos/por lote, 4096 versões, 8 MiB de inventário,
128 fragmentos, 256 lotes, 512 leituras/instalação, 300 s de prova e 30 s de
cleanup, três logins por AVD (seis no conjunto). Ausência de cobertura de um
tipo na fixture hospedada permanece explícita nos quinze contadores.

Credenciais chegam por stdin a um arquivo 0600 no sandbox do novo aplicativo,
são removidas no finally; logs nativos/build permanecem em temporário privado.
Somente relatório com vocabulário fixo, booleanos, tempos, contagens e hashes é
publicado. Nenhum e-mail, UUID, dado acadêmico ou token é permitido no relatório.

O workflow reutilizável `p1-recovery.yml` tem modo PREFLIGHT padrão e autorização
HOSTED independente, desativada por padrão. É chamado pelo dispatcher existente
`h131-investigation.yml`, seleção P1_*; não integra candidatos H13.1/H13.2.
LOCAL não recebe Secrets/Environment. HOSTED/PREFLIGHT usam apenas os dois Secrets
nomeados no Environment já existente. PREFLIGHT não faz requisições nem gera AVD.

### Parâmetros futuros — somente após autorização específica

```sh
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=P1_PREFLIGHT -f authorize_hosted_p1=false
# NÃO executar a linha seguinte sem a autorização específica P1:
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=P1_HOSTED -f authorize_hosted_p1=true
```

A prova usa target/test APKs compilados juntos, com o mesmo certificado descartável,
porque a chave Debug varia entre jobs. Isso não prova identidade binária com o
APK #208: o relatório registra o SHA/certificado do par efetivamente instalado.
O código principal deve permanecer idêntico ao código validado; nenhuma chave
privada permanente é solicitada. As duas instalações recebem os mesmos bytes.

### Situação da validação — 14/11/2026 UTC

**Executor pronto para solicitar autorização P1; nenhuma homologação hospedada P1
executada.** Não houve alterações no código principal, Gradle ou SQL relativamente
a `b8206c3`, nem alterações em produção, RLS, contas, main ou dispositivos existentes.

| Etapa | Fonte e execução | Resultado efetivamente observado |
| --- | --- | --- |
| APK atualizado | `6f3ae05`, [#208 / 43632816764](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764) | `success`, identidade/assinatura/DEX/SHA conferidos; JVM 313 aprovados + cinco condicionais ignorados por variante Debug/Homologação |
| Primeira prova P1 | `f2f171301745083cd46b7c3cb30b2b188cbe69b1`, [#23 / 43634689365](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43634689365) | Dois AVDs, quatro testes por AVD: oito execuções aprovadas; 46 testes Python aprovados |
| Prova P1 final, orçamento compartilhado antes de HTTP | `6509f36dd0567b7316ad0c20f1e5ebdb150abe97`, [#24 / 43635622151](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43635622151) | Dois AVDs, cinco testes por AVD: dez execuções aprovadas, zero falhas/ignorados; 46 testes Python aprovados (Auth 16, H05 15, P1 15) |

São cinco casos Android distintos, executados em cada instalação; as rodadas não
são casos únicos adicionais. A rotina de backend existente também executou suas
nove verificações HTTP/RLS de isolamento, sessão e escrita negada. PostgreSQL 17 /
PostgREST 12.2.3 reais descartáveis; Auth local inteiramente sintética, sem contas
reais. Não repetida a regressão acadêmica integral de `43627336525`, pois o produto,
contratos e migrações estão inalterados. Nenhum candidato H13.1/H13.2 foi integrado.
Compilação Debug/test APK e compilação Kotlin da instrumentação Homologação
conferidas localmente, sem chave hospedada e sem enviar requisições.

Comandos locais/CI utilizados nesta continuação:

```sh
python3 scripts/test-hosted-auth-checks.py
python3 scripts/test-h05-recovery.py
python3 scripts/test-p1-recovery.py
./gradlew --offline --no-daemon assembleDebug assembleDebugAndroidTest
# Compilação da instrumentação Homologação, sem executar Auth/HTTP:
./gradlew --offline --no-daemon -PnoctuaH05HomologacaoInstrumentation=true \
  compileHomologacaoAndroidTestKotlin
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=P1_LOCAL -f authorize_hosted_p1=false
```

As tarefas Debug e Homologação usam invocações Gradle separadas: o seletor
`testBuildType` existente registra somente um conjunto AndroidTest por invocação.
Uma tentativa inicial de pedir ambos no mesmo comando foi recusada por tarefa
inexistente; separá-las compilou sem alteração de código ou Gradle.

A quinta guarda comprova que a 513ª leitura é recusada antes de alcançar o
transporte. O orçamento é único para startup, inventário e SELECTs diretos; não
são quotas independentes que possam ser somadas inadvertidamente.

#### Resultados da prova final

| Verificação | AVD 1 | AVD 2 |
| --- | --- | --- |
| Instalação/sessão/Room inicialmente vazios | Sim | Sim |
| Login UI, startup automático e A → B → A | Aprovado | Aprovado |
| A: registros / tipos / sequência | 171 / 15 / 2 | 171 / 15 / 2 |
| B: registros / tipos / sequência | 6 / 3 / 1 | 6 / 3 / 1 |
| IDs/payloads/revisões/recibos/FKs | Iguais e válidos | Iguais e válidos |
| Filas outbox/inbox/fragmentos/decisões e v1 | Vazias | Vazias |
| Inventário e auditorias remotos antes/depois | Inalterados | Inalterados |
| Leituras / tentativas de escrita | 40 / 0 | 40 / 0 |
| Logins / logout das próprias sessões | 3 / 3 | 3 / 3 |
| Tempo total da prova | 5.383 ms | 5.094 ms |
| Input privado removido / cleanup das sessões | Sim / sim | Sim / sim |

Os tempos são da **prova inteira** (três logins, recuperação, consultas e
verificações), não benchmarks SQL nem medições isoladas do transporte Android.
`RECOVER_A` no JSON mede o inventário de referência em paralelo ao startup;
`VERIFY_A` inclui a espera por convergência e a validação. Não interpretar esses
campos como tempo exclusivo de download ou de commit. Os hashes de A e B, totais
e contadores por tipo coincidiram exatamente entre os dois AVDs.

- [Evidência final sanitizada](evidence/P1_LOCAL_24.json).
- [Artefato final](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43635622151/artifacts/14130127734),
  ZIP SHA-256 `4247fe54af3f6dacc4b7020df5dd3c62cb92d74668958600db5ad60b6a65cac1`.
- [Primeira prova](evidence/P1_LOCAL_23.json),
  [artefato](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43634689365/artifacts/14129174979),
  ZIP SHA-256 `f53e7e2232d4bde8c780c3e29b568b97f685352528d7ccb5dfdb24a89ab41d62`.

Os ZIPs foram baixados e seus SHA-256 conferidos com a API. Os JSONs foram
revalidados pelo sanitizador e examinados para ausência de UUID/e-mail/credenciais.
Somente o relatório é artefato; banco, input, logs privados e screenshots não são
publicados. O relatório final identifica o target **Debug local** instalado nos
AVDs, que não é o APK paralelo #208. Isso é proposital: prova local com HTTP
local e Auth sintética. O modo HOSTED só admite o pacote paralelo, HTTPS e a
referência `sscexzxtqdcsqdlcytum`; compila target/test juntos, valida manifesto,
DEX e certificado e registra os hashes efetivamente usados.

### Commits e arquivos

- `ddd8cf4`: identidade e evidências do APK #208.
- `f2f1713`: executor P1, instrumentação UI, fixtures locais e workflows.
- `6509f36`: orçamento compartilhado de leituras e quinta guarda Android.
- Commit documental posterior: consolidação dos resultados e evidências acima.

Arquivos técnicos: `AuthTestRunner.kt`, `P1RecoverySupport.kt`,
`P1RecoveryProbeTest.kt`, `P1GuardTest.kt` (todos em `androidTest`),
`scripts/p1-recovery.py`, `scripts/p1-local-fixture.py`,
`scripts/test-p1-recovery.py`, `.github/workflows/p1-recovery.yml` e o dispatcher
existente `.github/workflows/h131-investigation.yml`. Documentos: este roteiro,
a atualização de estado do plano anterior e os JSONs em `docs/evidence/`.

### Pendências e condições da futura execução

1. **Autorização específica P1 ainda necessária.** Somente após ela, usar os
   parâmetros HOSTED acima. Não há necessidade de enviar credenciais pelo chat:
   reutilizar os dois Secrets existentes no Environment. Eventual ajuste do
   `fixtureClassId` deve ser feito privadamente pelo administrador.
2. O modo HOSTED valida formato/endpoint antes de qualquer login, depois identidade
   confirmada e elegibilidade E de A/B durante a execução. A condição atual das
   contas/fixture não foi consultada nesta preparação. Se alguma estiver fora do
   escopo E, interromper; não ativar escopo, alterar allowlist ou criar contas.
3. A autorização deverá abranger seis logins (A/B/A em cada AVD), verify/refresh
   quando necessário, status/pull E, SELECTs fixos e logout somente das sessões
   criadas. **Nenhuma escrita acadêmica, staging, exclusão, decisão ou auditoria
   remota é permitida por P1.** Falha bloqueia a prova; não há repetição automática
   do workflow nem autorização implícita para corrigir dados remotos.
4. Contadores indicarão os tipos realmente presentes. Os 15 tipos validados localmente
   não comprovam presença ou integridade desses 15 no projeto hospedado. O H05
   histórico tinha apenas nove tipos; não extrapolar sua cobertura.
5. P2/P3 (offline com escritas e decisões de conflito) permanecem fora do escopo,
   sem execução. A prova P1 tampouco homologa produção, assinatura permanente,
   atualização de instalações existentes ou dispositivos físicos.

Não instalar #208 sobre homologação anterior com certificado diferente. Para
revisão manual, somente dispositivo/AVD separado vazio; para P1, o próprio workflow
cria os dois AVDs novos. Nenhuma reinstalação ou limpeza de aplicativo é necessária.
