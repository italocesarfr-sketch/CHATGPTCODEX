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
