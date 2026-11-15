# Sincronização acadêmica em produção — fase 1 (somente aplicativo)

Base: `59cb7a1c3da1d2416fc0546fe230a1aa2bba5137` (`sync/academic-complete-0220`).
Branch temporária: `temp/academic-production-phase1`. **Nenhuma alteração em produção, Supabase,
main, workflows ou no caminho de assinatura.** Não há APK novo nem execução protegida.

## O que muda

| Arquivo | Mudança |
| --- | --- |
| `app/build.gradle.kts` | Propriedade `noctuaAcademicProduction` (padrão ausente = `false`) gera `BuildConfig.ACADEMIC_PRODUCTION` |
| `sync/academic/AcademicRuntime.kt` | `academicTestEligible` aceita `DEBUG` **ou** `ACADEMIC_PRODUCTION`; exclusões do proprietário legado mantidas |
| `scripts/test-official-signing.py` | A guarda passa a exigir a nova condição, o padrão desligado e a ausência da propriedade no caminho de assinatura e nos workflows |

Sem a propriedade, o release continua exatamente como o candidato c45 aprovado na
CI #40: `academicTestEligible=false`, nenhum controle E criado. As 11 fases e a
sonda `C40BoundUpgradeProbeTest` não mudam: o workspace vinculado do proprietário
é `legacy`, portanto permanece inelegível mesmo com a propriedade ligada.

## O que não muda

- Conta proprietária (`LEGACY_OWNER_ID`) e workspace `legacy` continuam excluídos.
  O conflito v1 preservado não é lido nem resolvido.
- A allowlist do servidor (`noctua_academic_test_accounts`) continua decidindo quem
  sincroniza. `recoverAuthorizedE` só recupera contas já ativadas no escopo E; uma
  conta nova passa uma vez pelo consentimento manual do painel.
- Room, backup, protocolo, contratos SQL, textos do painel ("teste") e versões.

## Como compilar um release com a sincronização ligada

`-PnoctuaAcademicProduction=true`, junto de `-PnoctuaOfficialCandidate=true`.
O caminho de assinatura **não** passa essa propriedade; ligá-la no candidato oficial
é uma alteração separada de `scripts/official-signing.py` e da guarda acima, com
novo SHA revisado e nova autorização.

## Como seguir

Ordem obrigatória. Cada etapa só começa depois de a anterior estar conferida.
Detalhes, riscos e recuperação: [HOMOLOGACAO_SUPABASE_0220.md](HOMOLOGACAO_SUPABASE_0220.md).

### 1. Banco de produção (administrador, pelo painel do Supabase)

Projeto de produção: `mpsgdchpgyxoqowfjoqm`. Conferir o projeto aberto antes de
cada execução; o de homologação é `sscexzxtqdcsqdlcytum` e **não** deve receber nada.

1. Fazer backup do banco de produção pelo painel.
2. Inventário, somente leitura: executar no SQL Editor
   `supabase/checks/prehomo_0220_readonly.sql` e guardar o resultado. O pacote do
   passo 4 só vale se a produção tiver a base v1 + guarda de sessão
   (`202610300001`, `202610310001`) e **nenhum** objeto acadêmico v2. Se houver
   objeto parcial ou divergência, parar: o pacote padrão não serve.
3. Gerar o pacote localmente (não acessa banco nem rede):

   ```bash
   python3 scripts/prepare-phase-e-deployment.py --output build/producao-0220.sql
   ```

4. Revisar e executar `build/producao-0220.sql` **inteiro, em uma única sessão**
   do SQL Editor. Ele reúne as sete migrações A–E (`202611010001` a
   `202611030003`) em uma transação; qualquer erro desfaz tudo. Nunca executar os
   arquivos separados nem reaplicar migrações anteriores por cima.
5. Se houver erro, timeout ou resultado incerto: conferir o catálogo com o
   inventário antes de qualquer nova tentativa. Não repetir às cegas.
6. Repetir o inventário e executar `supabase/checks/phase_e_readonly.sql`.
   Comparar com o resultado da homologação.

Fora do pacote, **não aplicar junto**: `202611130001_h13_registry_cache.sql` e
`202611150002_scope_conflict_http.sql`. São candidatas validadas só em ambiente
descartável e não estão na homologação; cada uma exige revisão e autorização próprias.

### 2. Liberar uma conta de teste (administrador)

O pacote não habilita ninguém. Criar uma conta nova de teste em produção, com dados
fictícios, e autorizar somente o UUID dela:

```sql
insert into public.noctua_academic_test_accounts(owner_id, allowed)
values ('UUID_DA_CONTA_DE_TESTE_APROVADA'::uuid, true)
on conflict(owner_id) do update set allowed = excluded.allowed;
```

Conferir uma linha afetada. Não autorizar por domínio ou em massa, e **não** incluir
o UUID de `AuthConfig.LEGACY_OWNER_ID` (conta proprietária).

### 3. Candidato assinado com a sincronização ligada (desenvolvimento)

1. Levar esta branch para `sync/academic-complete-0220` (PR ou merge), com
   `[skip ci]` no commit: push nessa branch dispara `android.yml`, que usa a chave.
2. Em `scripts/official-signing.py`, na chamada do Gradle de `prepare()`, acrescentar
   `-PnoctuaAcademicProduction=true`; ajustar a guarda em
   `scripts/test-official-signing.py`, que hoje exige a ausência da propriedade.
3. Rodar `OFFICIAL_SIGNING_PREFLIGHT` e depois `OFFICIAL_SIGNED_CANDIDATE` no novo
   SHA revisado, com autorização específica do proprietário para cada um. O APK
   aprovado na CI #40 não muda; é necessário um candidato novo.

### 4. Homologação física (proprietário)

1. Backup pelo aplicativo, guardado fora do aparelho.
2. Instalar o candidato novo por cima, sem desinstalar.
3. Entrar com a conta de teste, abrir o painel de sincronização e fazer uma vez o
   consentimento ("Testar turmas e alunos" e as ampliações até o escopo completo).
4. Em um segundo aparelho ou emulador, entrar com a mesma conta: os dados devem
   aparecer sozinhos, com status "Sincronizado".

### 5. Conta proprietária (etapa posterior, ainda não preparada)

Exige migração do workspace `legacy` para o protocolo novo e revisão explícita do
conflito v1 preservado. Não há código nem teste para isso nesta branch. Não remover
as exclusões de `academicTestEligible` para contornar.

## Validação local desta branch (Windows, sem emulador nem rede hospedada)

- `./gradlew testDebugUnitTest`: 318 casos, 313 aprovados, 5 ignorados, 0 falhas.
- `BuildConfig.ACADEMIC_PRODUCTION`: `false` por padrão; `true` com
  `-PnoctuaAcademicProduction=true`.
- `scripts/test-official-signing.py` (31) e
  `scripts/test-official-upgrade-preparation.py` (10): aprovados.
- Não executados aqui: testes Android instrumentados, suítes PostgreSQL/HTTP,
  compilação release (exige a chave) e qualquer verificação hospedada. Devem rodar
  na CI depois que a branch for levada para `sync/academic-complete-0220`.


## Registro histórico da implantação assistida — pré-execução

O registro abaixo descreve a sessão anterior. O relato posterior do administrador
e a preparação local estão consolidados na seção seguinte; os estados de espera
deste registro não representam as informações mais recentes.

Registrado em `2026-11-15T16:01:50-03:00`. Referência de código/SQL:
`ac56867ef3c6093670fafd91ddd4f591bc589f8a`.

O proprietário delimitou esta nova tarefa ao SQL Editor de **produção**, referência
`mpsgdchpgyxoqowfjoqm`, com confirmação do projeto antes de cada execução e
aprovações separadas para o pacote e para uma única conta fictícia. O texto
histórico de homologação foi lido integralmente para ordem, riscos e recuperação;
não autoriza tocar `sscexzxtqdcsqdlcytum` nesta tarefa.

### Conferências locais efetivamente realizadas

- Branch remota e referência conferidas; worktree separado para
  `temp/academic-production-phase1`, preservando a árvore e o histórico de
  `sync/academic-complete-0220` e main.
- Leitura integral deste documento e de `HOMOLOGACAO_SUPABASE_0220.md`, inclusive
  pacote transacional, timeouts, contingência e manifesto.
- Gerador e os dois SQLs de inventário revisados por leitura. O inventário usa
  catálogo/configurações/tamanhos; não consulta linhas acadêmicas ou de Auth.
- Hashes dos **sete** fontes A–E comparados ao manifesto: **7/7 coincidem**.
  A lista do gerador exclui `202611130001_h13_registry_cache.sql` e
  `202611150002_scope_conflict_http.sql`.
- Não se executou o gerador ainda: a ordem exige inventário elegível antes da
  geração. `build/producao-0220.sql` não existe neste worktree.

### Estado hospedado: não executado, sem aprovação implícita

| Etapa | Estado |
| --- | --- |
| Backup administrativo pelo painel | Confirmação do proprietário pendente |
| Projeto atualmente aberto no SQL Editor | Confirmação pendente |
| Inventário anterior | Não executado; baseline atual desconhecido |
| Pacote de produção / SHA aplicado | Não gerado / não aplicado |
| Autorização específica do pacote | Não recebida |
| Inventário posterior / conferência E | Não executados |
| UUID da conta fictícia / autorização | Não informados / não recebida |
| Linha da allowlist afetada | Nenhuma operação executada; resultado não verificado |

Nesta sessão não há ferramentas de navegador/desktop expostas para controlar ou
inspecionar o SQL Editor. A consulta de disponibilidade do ambiente retornou
`capabilities=[]`, embora o ambiente esteja em execução. Identificador do ambiente:
`ccarenv_b64_Y2NhcmVudl80YWQ4ZGU1OTQwZTQ4MTkxOGFiZWIwZTlmMTNlYTY2OQ`.
Nenhuma conexão alternativa ao banco foi tentada e nenhuma credencial foi solicitada.

Para retomar sem controle de navegador: depois das confirmações de backup/projeto,
o proprietário poderá executar o inventário original no próprio SQL Editor e
fornecer somente os resultados de catálogo para conferência. Isso deve ser registrado
como execução pelo administrador, não execução direta pelo Codex. Não encaminhar
backup, conteúdo acadêmico/Auth, e-mails, tokens ou credenciais.

**Ponto de parada:** antes do passo 1.2 (inventário). Não há conclusão sobre o estado
da produção nem autorização de DDL. Preservar a sequência original: backup/projeto,
inventário elegível, geração/revisão local, autorização específica e execução única
do pacote inteiro. Se surgir divergência ou objeto parcial, parar sem corrigir;
se houver erro/timeout/resposta incerta, não repetir e conferir novamente o catálogo,
confirmando o projeto aberto antes da nova leitura.

Não houve SQL hospedado, allowlist, fixture, carga, APK, workflow, acesso à chave,
modificação de Secrets ou alteração de aplicativo. A produção **não está declarada pronta**.

## Continuação — inventário relatado e pacote local revisado (15/11/2026)

O proprietário informou execução manual do inventário em `noctua-auth`, produção
`mpsgdchpgyxoqowfjoqm`: quatro tabelas v1 com RLS, doze A–E ausentes, guarda de
sessão presente, ativação/wrapper v2 ausentes, quatro políticas de proprietário e
três funções SECURITY DEFINER com search_path seguro e ACL informada como restrita.
Esses resultados são **relato do administrador**, não consulta independente nesta
sessão. São compatíveis com o baseline nos itens descritos; privilégios efetivos,
helper de validação, definições completas e demais objetos ainda exigem conferência.

Backup atualizado relatado: **pg_dump 17, 426.467 bytes**, catálogo legível por
`pg_restore --list`, incluindo v1/Auth. Não houve acesso ao arquivo nesta sessão.
Checksum do backup não informado e **restauração integral ainda não comprovada**.

A pedido do proprietário, o gerador oficial foi executado **somente localmente**:

- `build/producao-0220.sql`: **120.010 bytes**, 1.504 linhas.
- SHA-256: `7fd28b6dbce5f72724abc1a2644041f19f373fc7675eb37a24bd17fa9a46659b`.
- Sete hashes coincidentes com o manifesto histórico; corpos preservados;
  transação externa única, preflight e `lock_timeout=5s` conferidos.
- 18 verificações estáticas aprovadas; sem H13, PT409 ou reaplicação v1/guarda.
  Não houve nova execução de SQL, parser SQL, benchmark ou suíte Android.
- Cópia byte a byte idêntica ao SQL A–E já versionado para revisão; gerados SQL e
  manifesto em `build/`, sem sobrescrever artefato anterior.

Detalhes, sete hashes, limitações, riscos de timeout e checklist de restauração:
[REVISAO_PACOTE_PRODUCAO_0220.md](REVISAO_PACOTE_PRODUCAO_0220.md).
Evidências locais: [evidence/PRODUCAO_PACOTE_0220.json](evidence/PRODUCAO_PACOTE_0220.json).

**Ponto de parada atual:** pacote preparado para revisão. DDL de produção, conta de
teste, verificação posterior e assinatura/APK não executados nem autorizados por
esta continuação. Antes de uma autorização futura, completar a conferência de
catálogo e a avaliação de recuperabilidade do backup. Nenhum acesso hospedado,
modificação de contas/allowlist, dados reais, aplicação, assinatura, main ou branch
de sincronização foi realizado.
