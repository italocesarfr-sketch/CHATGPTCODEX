# Diagnóstico pontual da elegibilidade B

Continuação do [diagnóstico da P1 #25](P1_HOSTED_25_0220.md), sem alterar suas
evidências. Autorização específica do proprietário: uma verificação hospedada no
projeto `sscexzxtqdcsqdlcytum`, somente B, login/status/logout da sessão criada.
Não autoriza P1/P2/P3 nem transições, allowlists ou dados acadêmicos.

## Executor isolado

`scripts/b-eligibility.py` reutiliza a validação de Secrets e de branch/repositório
em `hosted-auth-checks.py`. Seleciona exclusivamente `accounts[1]` (B). Não recebe
URL, bearer ou chave administrativa do chamador. Origem HTTPS fixa no projeto de
homologação, certificado/hostname verificados, TLS >= 1.2, sem redirects/proxy de
ambiente, sem retry. Apenas estes três POSTs são possíveis, no máximo um de cada:

1. `/auth/v1/token?grant_type=password`: credenciais B do Secret protegido.
2. `/rest/v1/rpc/noctua_academic_status_c`: corpo vazio, bearer recém-criado.
3. `/auth/v1/logout?scope=local`: somente esse mesmo bearer, inclusive se falhar a
   conferência de identidade ou a leitura. Nenhum logout global ou refresh.

Qualquer outro passo, pull, staging, ativação, escrita, decisão ou repetição é
recusado antes de HTTP. Não há APK, Gradle, SDK, AVD, backup ou banco local. Respostas
limitadas a 256 KiB cada; conexão/socket até 10 s e deadline de leitura de 15 s
por chamada. São no máximo três requisições; jobs de CI têm limite de três minutos.
Rerun de tentativa GitHub >1 é recusado. Um marcador O_EXCL sem conteúdo privado
impede repetir a execução no mesmo job.

Identidade esperada/confirmada/não anônima é conferida no retorno do login; claims
sub/issuer/session/exp precisam ser compatíveis. Não é aceito token de entrada:
o bearer vem do login HTTPS real. A validação criptográfica do JWT e da sessão
ativa continua sendo feita pelo Supabase. Não são publicadas claims ou identidades.
O token fica apenas em memória e a sessão própria é encerrada no finally, sem
repetir logout. Resposta perdida/inválida pode impedir conhecer/encerrar uma sessão:
nessa situação o diagnóstico deve ser bloqueado e a limitação registrada, sem
nova tentativa automática.

O resultado registra somente enums de protocolo V1/V2/OTHER e escopo B/C/D/E/OTHER,
booleanos test_allowed/ready, contadores, tempos e status HTTP. Campos obrigatórios
faltantes/tipos inválidos bloqueiam o diagnóstico; não há defaults inventados.
Corpo v1, nomes, e-mails, UUIDs, tokens e respostas privadas não são exportados.
`DIAGNOSED_NOT_ELIGIBLE` é diagnóstico executado, **não aprovação da P1**.

## Proteção de CI

Workflow reutilizável `b-eligibility.yml`, chamado pelo dispatcher existente
`h131-investigation.yml`, opções `B_ELIGIBILITY_PREFLIGHT` e
`B_ELIGIBILITY_HOSTED`. Nova autorização independente
`authorize_hosted_b_eligibility=false` por padrão. Somente branch experimental,
evento manual, runner descartável e Environment `noctua-homologacao-h01-h04`.
Apenas os dois Secrets nomeados são repassados; não usar `secrets: inherit`.

O job de guardas, sem Secrets/Environment, executa testes sintéticos antes do job
protegido. O modo PREFLIGHT apenas valida formato/configuração, sem rede. HOSTED
exige autorização explícita e tentativa 1. O artefato contém exclusivamente
`build/b-eligibility/result.json`. Não há logs brutos privados nem credenciais em
arquivos. App oficial, assinatura, Room, SQL e critérios P1 permanecem intactos.

## Validação anterior à execução hospedada

26 testes sintéticos locais aprovados, cobrindo origem/TLS, timeout, limites,
redirects, seleção exclusiva de B, apenas uma RPC, ausência de retry, falhas de
login/status/logout, fechamento da própria sessão após identidade recusada,
protocolos/escopos/allowlist, sanitização e isolamento do workflow. As 16 guardas
compartilhadas de Auth também passaram. Estes testes não comprovam o backend real.

```sh
python3 scripts/test-b-eligibility.py
python3 scripts/test-hosted-auth-checks.py
```

A execução autorizada nesta tarefa será única:

```sh
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=B_ELIGIBILITY_HOSTED -f authorize_hosted_b_eligibility=true \
  -f authorize_hosted_p1=false
```

Execução única concluída: [#27 / 43662904726](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43662904726),
fonte `52084d81f0274b2de25b42b86eee2f706b1e9e9d`, tentativa 1, CI `success`.
O comando acima documenta a autorização já utilizada; não é permissão de
repetição nem convite para executá-lo novamente.

## Resultado hospedado efetivamente observado

**DIAGNOSED_NOT_ELIGIBLE.** O diagnóstico foi concluído; isso não aprova P1 nem
habilita sincronização E. Os dois jobs da CI passaram: 26 testes sintéticos novos
+ 16 guardas compartilhadas (42 casos, zero falhas/ignorados) e o diagnóstico
hospedado protegido. P1/P2/P3 e os laboratórios anteriores não foram executados.

| Predicado de B | Resultado |
| --- | --- |
| Protocolo | **V2** |
| Escopo | **B** (academicScope=2) |
| testAllowed | **true** |
| Elegibilidade para E | **false** |
| Identidade confirmada/não anônima esperada | Conferida |

A condição impeditiva atual está identificada: falta ampliar o escopo B até E.
Não é necessário migrar v1→v2 nem alterar a allowlist, pois esses dois requisitos
já estão satisfeitos. Não foi demonstrado problema de login ou falha da
sincronização automática de A. Este status novo é compatível com a recusa SCOPE
da #25, mas não preenche retroativamente os campos que aquele ensaio não coletou.

| Operação | Requisições | HTTP | Tempo |
| --- | ---: | ---: | ---: |
| Login B | 1 | 200 | 1.195 ms |
| Status acadêmico B | 1 | 200 | 767 ms |
| Logout da sessão criada | 1 | 204 | 383 ms |
| Total da verificação | 3 | — | 2.346 ms |

Sessão criada observada e encerramento da própria sessão confirmados. Não houve
refresh, login de A, pull, staging, decisão, exclusão, auditoria de decisão ou
alteração acadêmica. Zero solicitações bloqueadas e zero solicitações de mutação
acadêmica. Efeitos Auth normais de login/logout e seus logs internos são distintos
de alterações administrativas nas contas e de auditorias acadêmicas.
Nenhuma configuração hospedada, RLS, allowlist ou escopo foi alterada.

- [Relatório sanitizado original](evidence/B_ELIGIBILIDADE_27.json).
- [Artefato](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43662904726/artifacts/14140134322),
  `b-elegibilidade-sanitizado`, ZIP 562 bytes.
- ZIP SHA-256 `c0584a6eedd451a595dc77e0643e57cc5ad9c1784cb71e663f9e146ae872eb2b`.

Digest comparado à API GitHub; JSON examinado para ausência de e-mails, UUIDs,
tokens, chaves e dados acadêmicos. Nenhum APK foi gerado ou instalado. Produto,
Room, contratos e SQL permanecem idênticos ao estado anterior.

## Caminho mínimo específico para B — ainda não autorizado

1. Preparar ampliação **B → C → D → E**, com a mesma conta B e o mecanismo já
   existente de consentimentos/transições. Não alterar protocolo ou allowlist,
   não reaplicar migrações e não executar UPDATE direto em academic_scope.
2. Antes de cada transição, conferir titularidade, backup fictício verificável,
   revisões/cursores, filas e conflitos do workspace. Receber as mudanças pendentes
   e usar a sequência atual validada; não presumir sequência zero. As rotinas
   `prepareC/prepareD/prepareE` e os RPCs correspondentes exigem estado compatível,
   ausência de bloqueios e sequência conhecida. Se divergirem, parar preservando
   os dados. A UI experimental já encadeia os consentimentos C/D/E.
3. Os consentimentos locais existentes chamam `seed()` e podem enfileirar dados
   locais das entidades recém-habilitadas. Por isso, a futura autorização deve
   delimitar o workspace e eventuais envios acadêmicos; não prometer uma mudança
   apenas de metadados usando automaticamente um workspace não conferido. Uma
   alteração isolada no servidor sem preparar os clientes pode deixá-los com
   estado local incompatível. Nenhum aparelho existente será tocado sem autorização.
4. Após aprovação e transição efetiva, solicitar **nova autorização para P1**.
   A autorização desta consulta pontual já foi consumida. A P1 completa continua
   não aprovada; o resultado verde deste diagnóstico não autoriza distribuição
   ou atualização do aplicativo oficial.

Commits: `52084d8` implementou/testou o executor e o workflow; commit documental
posterior preserva o resultado e o plano mínimo acima.

## Procedimento legítimo se B não estiver pronta

- Se `testAllowed=false`: revisar a autorização da conta fictícia com o administrador;
  qualquer alteração de allowlist exige nova autorização. Não contornar a guarda.
- Se protocolo V1: conferir backup/titularidade, dados locais e remotos v1, filas,
  conflitos e versões dos clientes. A transição existente usa consentimento e
  `noctua_activate_academic(p_expected_sequence,p_expected_v1)`, sob sessão B,
  comparando o snapshot v1 e instalando a barreira aos clientes antigos. Não é
  equivalente a um UPDATE na tabela de protocolo e não deve ser forçada.
- Se V2 com escopo B/C/D: depois de receber e conferir as revisões, usar as
  transições existentes C → D → E, na ordem aplicável, cada uma com consentimento
  e sequência conhecida (`noctua_enable_academic_c/d/e`). Preservar filas, conflitos,
  recibos e dados; não pular etapas nem inventar revisão. Avaliar antes se há
  workspace v1, restauração pausada ou operações incompatíveis.
- Se V2/E/allowed=true: não é necessária transição; uma nova P1 completa ainda
  exige autorização própria.
- OTHER ou campos ausentes: interromper e revisar compatibilidade somente por
  leitura; não reaplicar migrações antigas ou alterar contratos para fazer passar.

Nenhuma dessas alterações está autorizada neste diagnóstico. Após conhecer o
estado real, preparar somente o passo necessário e pedir autorização separada
para ele, antes de qualquer alteração ou nova P1.
