# H05 — instalação Android limpa e recuperação somente leitura

Preparação em **12/11/2026, America/Sao_Paulo**, branch `sync/academic-complete-0220`,
a partir de `eb16571d194d58fe13fcb24af4cb665429bcb3e5`.
**Não executar HOSTED sem autorização específica posterior.**

## Estado e escopo

H01/H02/H04 foram aprovados na #193; H03 na #194. Esses resultados não comprovam
recuperação Android limpa. H05 acrescenta essa prova, sem repetir aqueles testes.
A preparação não usa contas reais, dados do proprietário ou dispositivos existentes.

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

# SOMENTE APÓS NOVA AUTORIZAÇÃO ESPECÍFICA (não executado nesta preparação):
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

## Validação e continuidade

- Compilação de instrumentação debug aprovada neste executor.
- 12 testes offline de segurança H05 aprovados; 16 do runner H01–H04 preservados.
- actionlint dos workflows e py_compile aprovados.
- Prova nativa LOCAL em AVD independente: **ainda pendente de aprovação**.
  A [primeira execução #195](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43353738219)
  foi bloqueada por `INVALID_REPORT`. Uma função local chamada `put` interceptava
  o booleano `present` do builder JSON, colocando-o fora do item de tipo. A
  validação estrita recusou o relatório; não foi flexibilizada. A construção por
  tipo foi isolada, ganhou regressão nativa e a marca de autenticação passou a
  ser registrada após conferir a identidade. A reabertura agora repete a engine
  de leitura e valida revisões/receipts/estado completo. Nova prova LOCAL necessária.
  Fixture reutilizada da Fase D: 171 registros, 15 tipos,
  36 alunos de origem, um segundo lote de renomeação com históricos preservados.
- HOSTED: **não executado**, bloqueado por autorização posterior.

No LOCAL, PostgreSQL/PostgREST são descartáveis, com identidades JWT fictícias e
migrações/fixture existentes. O modo local pode gravar exclusivamente esse banco
local para semear o cenário. O processo de recuperação usa a mesma barreira de
leitura do HOSTED. Os três testes instrumentados adicionais verificam bloqueio dos
10 métodos de escrita antes da delegação, autorização independente obrigatória
e campos/hash/presença dentro de cada tipo do relatório.
Não considerar casos ignorados como aprovação. Publicar somente
`build/h05/result.json`, validado por lista fechada, no artefato sanitizado.
