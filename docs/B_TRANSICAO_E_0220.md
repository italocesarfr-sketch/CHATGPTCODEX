# Preparação controlada B → C → D → E

Base: `428cb4c`, [diagnóstico hospedado #27](B_ELEGIBILIDADE_0220.md).
**Somente preparação/local. Nenhuma transição, consulta ou nova P1 hospedada
está autorizada nesta tarefa.** Nenhuma instalação existente será modificada.

## Reutilização e limites

A UI atual já chama `AcademicEngine.activateC/activateD/activateE`, que reutilizam
`AcademicStore.prepare*/activate*` e os RPCs existentes. A preparação não cria
outra fila, protocolo ou restauração integral. Room, backup, assinatura e pacote
oficial não mudam.

`prepareC` exige fase B local compatível, filas sem bloqueio, versões B confirmadas
e cursor igual ao head remoto. `prepareD/prepareE` exigem as fases anteriores,
validam o grafo, preservam as filas e verificam a sequência conhecida. Cada etapa
salva consentimento PREPARED antes de chamar o servidor. Depois da confirmação,
ativa a nova fase. A sequência inteira não é uma transação remota única: cada RPC
é atômico. Falha após C pode deixar C confirmado e D não iniciado; **não rebaixar
escopos para tentar desfazer**.

Os métodos chamam `seed()`: dados locais das entidades recém-habilitadas podem
ficar pendentes para envio automático. Portanto não é correto prometer apenas
metadados sem conferir todo o workspace. A preparação conservadora bloqueia
qualquer diferença local não confirmada entre os 15 tipos e as bases reconhecidas,
inclusive diário e históricos fora do escopo B. Dados e filas são preservados.

Não temos acesso autorizado aos arquivos dos emuladores existentes. Uma instalação
vazia não comprova ausência de dados locais não sincronizados em outro dispositivo.
A titularidade de B não autoriza reassociar ou ler os arquivos de A. A prévia dos
workspaces/clientes B, sua compatibilidade e suas pendências são requisitos reais
antes de qualquer execução hospedada. Não se considera essa parte verificada.

## Auditoria da definição efetiva C/D/E

A revisão inicial isolou a definição anterior de C e suspeitou de recuo de escopo.
A leitura da ordem completa corrigiu essa hipótese: a migração D,
`202611020003_phase_d_scope.sql`, **já substitui** o RPC C e exige escopo 2 ou 3.
Os RPCs D e E também restringem seus predecessores. A sequência acadêmica não
muda com a ampliação, portanto é essa guarda final do servidor que precisa ser
verificada, não apenas o check de sequência.

A candidata `202611150001_scope_transition_guard.sql` criada na preparação inicial
foi retirada antes de qualquer uso hospedado: era desnecessária. A retirada dessa
candidata não exige implantação. A candidata **002**, descrita abaixo, trata outro
problema comprovado. Os scripts A–E originais e o pacote publicado não foram alterados. O teste descartável confere recuos E→C, E→D e D→C recusados e preserva
ACL/RLS/SECURITY DEFINER/search_path. A definição hospedada não foi consultada nesta
tarefa; uma futura conferência administrativa deve avaliar a definição final D,
sem reaplicar o script C antigo isoladamente.

Não afirmar que houve vulnerabilidade no protocolo final A–E com base na definição
intermediária de C. O histórico dos commits conserva a hipótese e sua retirada.

## Bloqueador HTTP comprovado em cluster descartável

Na preparação #30, as 63 regressões PostgreSQL C/D/E passaram, mas o teste HTTP
com sequência antiga expirou antes de chegar ao Android. A reprodução em um novo
cluster próprio PostgreSQL 17.11 / PostgREST 12.2.3 confirmou a causa: os RPCs de
ampliação lançam `40001` para um head incompatível. O PostgREST/Hasql repetiu
transações de serialização até o cliente expirar (20 s), mantendo atividade no
servidor até o processo descartável ser encerrado.

Foram observadas 251.137 mensagens desse erro no log local. O intervalo completo
não foi medido, portanto isso não é benchmark nem contagem de requisições HTTP:
o cliente fez uma única requisição. [Métricas sanitizadas](evidence/B_TRANSICAO_RETRY_LOCAL.json).
O cenário não foi repetido no hospedado e a versão/comportamento do PostgREST
hospedado não foi consultada nesta tarefa. Não se pode extrapolar a contagem local.

Candidata real e distinta da hipótese anterior retirada:
`supabase/migrations/202611150002_scope_conflict_http.sql`.
Ela preserva as definições finais de C/D/E e altera **somente** o SQLSTATE do
head desatualizado, de `40001` para `PT409`. Isso sinaliza conflito de domínio
HTTP 409 sem induzir a repetição de uma transação que não pode convergir usando
a mesma sequência fixa. Não muda validações, locks, predecessores, identidades,
parâmetros, dados, ACL ou RLS. Não aumenta timeouts. Não trata conflitos de notas
ou decisões; somente os três RPCs de ampliação de escopo.

A preparação local confirmou a rejeição original 40001 por SQL direto (sem
reproduzir de novo o loop HTTP) e, depois da candidata, HTTP 409/PT409 imediato,
com escopo e sequência intactos. A regressão completa com a candidata ainda será
consolidada. A candidata **não foi aplicada hospedada**; requer revisão e autorização
administrativa separadas. O formato de erro é uma mudança de compatibilidade:
os clientes atuais tratam falha não-2xx como `SyncFailure` e preservam PREPARED,
mas a validação hospedada de erro 409 ainda é pendente. Não aplicá-la automaticamente.
A guarda monotônica correta de D permanece intacta.

## Executor de preparação — exclusivamente LOCAL

- Workflow `b-transition.yml`, via dispatcher existente, opção `B_TRANSITION_LOCAL`.
  Não existe opção HOSTED/transição nem acesso a Secrets/Environment nesse workflow.
- PostgreSQL/PostgREST descartáveis; identidades e sessões sintéticas. A fica com
  fixture de 171 registros/15 tipos; B começa em V2/B com 41 registros, quatro
  bimestres e 36 matrículas. Nenhuma conta real é criada.
- `BTransitionNetworkTest` usa os mesmos métodos de consentimento/engine, com
  `BTransitionRemote` permitindo no máximo C, D e E sob identidade B, consentimento
  individual e sequência atual. Todos os envios acadêmicos, staging e decisões
  são barrados antes do HTTP. A binding atual é apenas Debug LOCAL; não há executor
  hospedado habilitado.
- Prévia verifica filas, fragmentos, decisões, v1, conflitos/RESTORE, dono, cursor,
  revisões/bases, hashes dos recibos remotos/local e FKs. Limites conservadores:
  256 registros/versões, 64 lotes e 256 leituras por wrapper. Casos acima dos limites
  são bloqueados; não se aumenta timeout ou reduz validação.
- Uma cópia lógica criptografada fictícia é conferida antes dos consentimentos;
  a abertura por A é recusada. Não se restaura sobre banco existente.
- Testa dados locais pendentes, todos os 15 tipos/históricos ainda locais,
  revisão/recibo adulterado, dono errado, conflito, restauração e cursor antigo.
- Testa resposta C perdida após commit, reabertura do Room e retomada por nova
  conferência explícita do consentimento persistido. Não reenvia C já confirmado.
  É reabertura do banco, não prova de morte real do processo nesta rodada.
- Um segundo banco B antigo é preservado e bloqueado se tentar C depois de o
  servidor alcançar E. Isso demonstra a necessidade de coordenar todos os clientes,
  não de forçar as versões locais antigas a adotar metadados novos.
- Hash de todas as tabelas do proprietário A permanece igual; para B só se admite
  mudança em academic_scope. Registros, versões, recibos, cabeçalhos, auditorias,
  históricos e identidades devem permanecer iguais.

A comprovação Android usa **um novo AVD da CI**, com vários bancos Room
independentes; não é prova de migração dos emuladores existentes nem de morte
real do processo. As 33 regressões Room são adicionais à prova de transição.
Resultados e limites efetivamente observados ficam na consolidação abaixo.

## Procedimento hospedado exato proposto — não executável automaticamente ainda

1. Confirmar projeto e B em V2/B/allowed, identidade confirmada e sessão própria.
   Não autenticar A. Nenhuma chave administrativa no cliente.
2. Conferir cópia de segurança verificável e inventário **de todos os workspaces B
   relevantes**, inclusive dados fora do escopo e edições offline. Suspender edição
   e sincronizações concorrentes durante a janela da transição, sem limpar apps.
   Fazer backup não dispensa a conferência das filas/revisões.
3. Se existir pendência, conflito, histórico não reconhecido, restauração pausada,
   cliente incompatível ou titularidade divergente, interromper. Não enviar os dados
   para “limpar” a fila sob uma autorização apenas de metadados. Preparar outra
   autorização delimitada se algum envio acadêmico for necessário.
4. Após revisão/aplicação separada da candidata 002 e conferência das definições finais A–E, gerar prévia B→C, verificar
   filas/revisões/head e consentir C. Usar `p_expected_sequence` observado, nunca zero
   inventado. Conferir resposta C e invariância dos dados antes de prosseguir.
5. Repetir a conferência e o consentimento próprios de C→D e D→E, com os RPCs
   existentes e metadados Room coerentes. Revalidar estado antes de cada chamada.
   Payloads acadêmicos, staging, exclusões e decisões permanecem proibidos no plano
   de metadados; o gate deve impedir o envio se `seed()` revelar qualquer diferença.
6. Se a resposta se perder ou houver concorrência, parar no último estágio conhecido,
   preservar PREPARED e filas, reler status sob autorização e revisar antes de
   retomar. Não rebaixar escopo, descartar variantes nem repetir a sequência inteira.
7. Conferir E e a invariância dos registros, revisões, hashes, recibos e auditorias.
   Encerrar somente a sessão criada. Só depois solicitar nova autorização da P1.

Não se garante congelamento dos editores/clientes existentes a partir da CI. A
preparação não habilita transição hospedada enquanto a origem local de B não tiver
sido conferida e a binding autenticada/mutex/barreira de comandos do workspace
real não tiver sido revisada. Isso é um bloqueio de liberação, não justificativa
para usar atualização SQL direta.

## Textos de autorização para revisão posterior

A candidata 001 foi retirada. A candidata **002**, que trata o conflito HTTP,
precisa de autorização administrativa própria, após revisão da prova local:

> Autorizo aplicar exclusivamente 202611150002_scope_conflict_http.sql em
> sscexzxtqdcsqdlcytum, após backup administrativo e conferência das três definições
> e privilégios atuais. Autorizo somente a alteração de sinalização do conflito de
> sequência para HTTP 409 nos RPCs C/D/E. Não autorizo transição de B, dados
> acadêmicos, RLS, allowlist, produção, P1/P2/P3 ou reinstalação de aplicativos.
> Interromper se o preflight ou a compatibilidade divergir.

Após revisar os inventários B, a candidata e o ambiente/workspace seguro, a
transição também exige sua própria autorização:

> Autorizo a transição controlada da conta fictícia B, de V2/B para C, depois D
> e depois E, exclusivamente em sscexzxtqdcsqdlcytum, com consentimentos existentes,
> backup verificado e workspaces B previamente conferidos. Autorizo login B,
> leituras necessárias, atualização dos metadados locais de sincronização e os três
> RPCs de ampliação, cada um após confirmar os requisitos e a sequência anterior,
> além de logout somente da sessão criada. Não autorizo criar/alterar/excluir
> registros acadêmicos, staging, decisões, auditorias de decisão, dados de A,
> allowlist, RLS, produção ou P1/P2/P3. Se houver dados locais não reconhecidos,
> filas, conflitos, divergência, timeout ou resposta perdida, interromper preservando
> tudo e não repetir automaticamente. Não tocar instalações existentes fora do
> workspace/ambiente expressamente identificado para esta operação.

Esses textos são propostas revisáveis, não autorizações recebidas. Ainda faltam
inventários locais, definição do workspace seguro e revisão da binding autenticada,
da barreira de comandos e da coordenação/pausa de tentativas automáticas durante
a janela. A engine normal não pode ficar fora do gate de transporte restrito. Isso não é substituído por uma instalação
vazia na CI.


## Execuções intermediárias

- #28 / `43665602114`, fonte `bee1de3`: C 20/20; D 26/27, uma falha porque a fixture
  de exclusão com histórico ainda não havia sido gerada pela suíte JVM. Não se
  removeu essa asserção. [Evidência parcial](evidence/B_TRANSICAO_28.json).
- `c652992` corrigiu a ordem: JVM/fixture antes de PostgreSQL. `3431f2d` retirou
  a candidata redundante e passou a conferir a definição C efetiva de D; o teste
  legado que reaplica C continua com suas asserções originais, sem contaminar
  os casos seguintes.
- #29 / `43665975764` foi cancelada ao perceber que D já continha a guarda. Não
  contar a execução cancelada como aprovação. A candidata não será implantada.
- Essa rodada usou apenas as definições existentes, sem alteração remota.

- #30 / `43666296847`, fonte `3431f2d`: JVM 313 aprovados/cinco condicionais
  ignorados; C20/D27/E16 aprovados. HTTP de sequência antiga expirou; a etapa
  Android não executou. Diagnóstico local acima, sem repetir esse loop em nuvem.
- #31 / `43668344387`, fonte `03ff435`: JVM 313 aprovados/cinco condicionais
  ignorados; C20/D27/E16 aprovados; HTTP 409/PT409 em 3 ms. Android falhou no
  estágio `PENDING` ao construir a fixture: tentava preparar uma outbox B após
  inserir dados de todos os escopos. [Evidência](evidence/B_TRANSICAO_31.json).
- `e2cb6cd` separou as provas: grafo de 15 tipos ainda não reconhecido deve ser
  recusado e preservado; edição B válida com outbox deve ser recusada e preservada
  em outro banco. Acrescentou diagnósticos restritos a nome de arquivo/linha e
  estágio, sem exportar mensagens ou stack trace bruto. Nenhuma asserção de
  preservação foi removida.


## Conferência administrativa futura da candidata 002 — somente leitura

**Não executada no hospedado nesta tarefa.** Antes de autorizar qualquer aplicação,
conferir o projeto, backup administrativo e as definições finais e privilégios.
O preflight do arquivo verifica presença das três funções; não substitui esta
conferência de compatibilidade. Interromper se houver definição/privilégio diferente,
sem substituir alterações administrativas desconhecidas.

```sql
SELECT p.proname, pg_catalog.pg_get_function_identity_arguments(p.oid) AS arguments,
       p.prosecdef, p.proconfig, p.proacl, pg_catalog.md5(p.prosrc) AS body_version
FROM pg_catalog.pg_proc p
JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public'
  AND p.proname IN ('noctua_enable_academic_c',
                   'noctua_enable_academic_d','noctua_enable_academic_e')
ORDER BY p.proname;
```

Argumento esperado: `p_expected_sequence bigint`; `SECURITY DEFINER=true`,
`search_path=pg_catalog`; comparar ACL com a instalação A–E aprovada. MD5 aqui é
somente fingerprint de versão do corpo da função, não proteção criptográfica de dados.

| RPC | Corpo original final A–E | Corpo candidato 002 |
| --- | --- | --- |
| C | `a28db08fe489659a9302b7f762950ca3` | `747e3c4e286606eab95a41a07455cbc7` |
| D | `c62f34b87960b99f9de77905156439b9` | `29a3986d9c0200be05ddee3119f88349` |
| E | `29fb817e0b65c7ec274bf40b3a713e4b` | `8bca68b2ecf57e664188111615153717` |

SHA-256 do arquivo candidato:
`da26570bf40e94ddf8a7b5c18202af0223e706eb90ae0a8191b3a006997e70bc`.
Não executar a migração C antiga isolada e não reaplicar o pacote A–E para corrigir
este retorno HTTP. Não executar a candidata sob autorização apenas de transição.

## Comandos locais e evidências adicionais

No workspace, sem credenciais e sem acesso Supabase, passaram:

```sh
python3 scripts/test-b-transition-preparation.py  # 5
python3 scripts/test-b-eligibility.py             # 26
python3 scripts/test-hosted-auth-checks.py        # 16
python3 scripts/test-p1-recovery.py               # 19
```

66 verificações Python, zero falhas/ignoradas; cinco delas também executam na CI,
portanto não somar essa repetição como casos novos. A instrumentação corrigida
compilou (`compileDebugAndroidTestKotlin`, offline). Cluster próprio PostgreSQL
17.11/PostgREST 12.2.3 adicional no workspace completou B→C→D→E com 41 registros,
head 1 e hashes invariantes de A e dos dados/recibos/históricos de B. Essa prova
usou RPCs locais e sessões sintéticas, **não Android nem Auth hospedado**.
[Evidência independente](evidence/B_TRANSICAO_RUNTIME_LOCAL.json).
Os dois processos locais foram encerrados após a prova; nenhum cluster preexistente
foi reaproveitado, removido ou alterado.


## Arquivos e limites da entrega

| Arquivo | Finalidade |
| --- | --- |
| `BTransitionPreparation.kt` (androidTest) | Permissão de etapa única e bloqueio antes de HTTP |
| `BTransitionNetworkTest.kt` (androidTest) | Métodos reais de consentimento/Room com PostgREST local |
| `scripts/b-transition-local.py` | Fixtures sintéticas e fingerprints de A/B e segurança |
| `scripts/b-transition-android.py` | Um AVD novo, provas e diagnósticos sanitizados |
| `scripts/b-transition-regression.py` | Regressões C/D/E com definições finais/candidata |
| `scripts/test-b-transition-preparation.py` | Guardas estáticas sem credenciais |
| `.github/workflows/b-transition.yml` | Laboratório sem Secrets/Environment/modo hospedado |
| `.github/workflows/h131-investigation.yml` | Uma opção LOCAL adicional no dispatcher existente |
| `supabase/migrations/202611150002_scope_conflict_http.sql` | Candidata administrativa ainda não aplicada hospedada |

Não há diferença em `app/src/main`, Gradle de produção, Room, versões, formato de
backup ou scripts SQL A–E originais frente à base `428cb4c`. A atualização assinada
oficial, certificado permanente, instalação física e testes Auth reais novos não
foram executados nesta preparação. Nenhum resultado local equivale a habilitação
hospedada, nova aprovação da P1 ou liberação do produto oficial.


### #32 — transição concluída, regressão nativa interrompida

Fonte `e2cb6cd`, execução `43669731962`: a prova B concluiu 41 registros, 21
verificações negativas, C→D→E, resposta perdida/reabertura e zero escritas acadêmicas.
Porém a instrumentação do conjunto de 34 casos encerrou com `Process crashed`;
o OK final não foi obtido. **CI reprovada**, não contar os 34 casos como aprovados.
JVM 313 aprovados/cinco condicionais ignorados e PostgreSQL C20/D27/E16 passaram.
A conferência final das tabelas foi pulada por consequência da falha nativa; o
fingerprint pós-transição desta rodada ainda não foi aprovado. A causa do
encerramento não consta do artefato anterior. [Evidência](evidence/B_TRANSICAO_32.json).

A próxima instrumentação registra somente casos iniciados/concluídos da lista
controlada de fontes, nomes fixos de exceções e arquivo/linha do crash. Logs brutos
permanecem temporários privados e são removidos antes de subir artefatos. A
conferência de invariância de A/B também será executada se a prova principal tiver
concluído, mesmo que outra regressão falhe; isso não altera o resultado da CI.
Não foi presumido ou corrigido um defeito de produto sem evidência.
