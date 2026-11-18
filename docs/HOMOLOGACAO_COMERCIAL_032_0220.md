# Fase 3.2 — preparação da homologação comercial hospedada

Preparação em 18/11/2026 (America/Sao_Paulo), branch
`sync/academic-commercial-auto-031`. **Nenhum SQL, login ou alteração hospedada
foi executado nesta preparação. Não é aprovação de produção.**

## Base e evidências reaproveitadas

- HEAD inicial: `aa32046307bcef93ad0c674e6ba1464aaab5cccd`.
- Implementação: `a1c97044a601d6743b32eff9280e446981bee1c8` (servidor) e
  `ed550105301832f76780186e1740df9bd989dbb4` (Android).
- [CI local 44228928532](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44228928532):
  20 provas PostgreSQL comerciais, 27 de regressão D, 35 verificações HTTP locais
  e 60 aprovações Android em dois AVDs, sem falhas Android ou testes ignorados.
  Cada configuração JVM teve 322 casos, 317 aprovados e cinco ignorados
  (H01–H04 hospedados e sonda Auth legada). Não contar esses cinco como aprovação.
- 171 registros dos quinze tipos recuperados entre Room independentes; hash final
  igual, IDs, FJ, históricos, atualização bidirecional, offline/reinício e resposta
  de inicialização perdida verificados. Auth dessa CI era sintético, não Supabase
  Auth hospedado. Detalhes em [FASE_31_AUTOMATICA_0220.md](FASE_31_AUTOMATICA_0220.md)
  e [evidências verificadas](evidence/FASE_31_CI_0220.json).
- O ambiente hospedado tem histórico de implantação A–E e testes anteriores,
  mas **o catálogo atual não foi consultado**. Relatos antigos não substituem a
  inspeção abaixo. Não reutilizar as contas antigas nem a conta Aron Danone.

## Migração revisada e dependências

Aplicar futuramente **somente**
`supabase/migrations/202611170001_commercial_bootstrap.sql` (7.450 bytes).
SHA-256:
`1d5c48c0b55a24edbae948407a8775030490338c8a3b0c6a64b2a98e5bf051e2`.
Há um único `BEGIN;` e `COMMIT;`; o arquivo foi preservado byte a byte.

Dependências que já precisam existir, **não reaplicar**:

1. `202610300001_class_sync.sql` e `202610310001_sync_session_guard.sql`.
2. `202611010001_academic_protocol.sql`.
3. `202611020001_phase_b_fence.sql`.
4. `202611020002_phase_c_scope.sql`.
5. `202611020003_phase_d_scope.sql`.
6. `202611030001_phase_e_paging.sql`.
7. `202611030002_phase_e_decisions.sql`.
8. `202611030003_phase_e_indexed_graph.sql`.

Não incluir H13 (`202611130001`) nem PT409 (`202611150002`), nem o pacote
transacional antigo de sete migrações. Ausência, objetos parciais ou divergência
de definições bloqueiam a implantação e exigem revisão, não reparo automático.

A mudança cria duas tabelas administrativas com RLS, sem SELECT/DML de clientes:
`noctua_commercial_rollout` e `noctua_commercial_accounts`. Ausência de políticas
nessas duas tabelas significa deny-all; não criar `own_rows` para abri-las.
O rollout nasce desligado e **nenhuma conta é concedida**.

As duas RPCs públicas autenticadas novas são `noctua_commercial_status()` e
`noctua_ensure_academic_e()`, ambas sem parâmetros. Principal, sessão Auth ativa,
confirmação de e-mail e titularidade continuam impostos no servidor. Helpers
continuam privados, com `search_path=pg_catalog`; clientes não informam owner_id.
No escritor E, muda somente o predicado de autorização, com exigência da expressão
original exatamente uma vez. A comparação local preservou todos os demais bytes,
predicados acadêmicos, guarda histórica, revisões, recibos e ACL. RLS anterior e
contratos A–E continuam intactos; não há migração Room nova (base real: Room 14).

## Portões de autorização independentes

1. **Inspeção/backup administrativos** no projeto separado.
2. **Implantação da migração comercial**, após autorização explícita específica.
3. **Criação/identificação de uma conta fictícia inédita confirmada**, sem copiar
   alunos reais. Configurar e testar a entrega de confirmação sem desabilitá-la.
4. **Concessão comercial individual e ativação do rollout**, por nova autorização.
5. **Ensaio Auth e acadêmico nos dois AVDs**, com limites de escrita aprovados.

A aprovação da migração não autoriza os portões seguintes. Não conceder acesso
por domínio, automaticamente a novos cadastros ou a todas as contas. O cliente
não é provisionador comercial. Não inserir a nova conta em
`noctua_academic_test_accounts`; até uma inscrição `allowed=false` nessa tabela
bloqueia a admissão comercial. Usuários antigos continuam pelo caminho anterior.

## Inspeção somente leitura, preparada, não executada

Administrador: confirmar no painel **noctua-homologacao**, referência
**sscexzxtqdcsqdlcytum**, antes de cada execução futura. Produção
`noctua-auth` não participa. Não compartilhar conexão, senha, dump ou tokens.

Arquivo único de preflight:
`supabase/checks/commercial_032_preflight_readonly.sql`.
Ele usa transação READ ONLY e ROLLBACK e consulta somente catálogo/configuração;
não invoca RPC de negócio, não lê linhas acadêmicas nem conteúdo de Auth.

Critérios de aprovação:

- Dezesseis tabelas anteriores presentes, RLS=true, leitura anônima=false,
  escrita direta autenticada=false. Conferir políticas por proprietário e a
  ausência de políticas públicas permissivas; tabelas internas podem ser deny-all.
- Zero objetos comerciais novos antes da implantação.
- Funções enumeradas existentes, `SECURITY DEFINER`/`search_path` e ACL conforme
  esperado: anon sem EXECUTE, authenticated somente nas RPCs públicas e no
  principal v1 já existente; helpers E privados.
- Guarda de sessão consulta sessão e confirmação; seu proprietário pode consultar
  `auth.sessions`. Corpos do principal v1, principal E e escritor guardado coincidem
  com os corpos revisados. MD5 de catálogo detecta divergência, não autentica arquivo;
  SHA-256 acima autentica o arquivo de implantação. Até mudança de formatação exige
  revisão, sem contornar o bloqueio.
- Expressão de autorização original ocorre uma única vez; registry original em
  linguagem SQL, nenhuma candidata PT409 nas transições.

## Implantação futura e conferência

1. Fazer backup administrativo independente e verificável do projeto separado,
   incluindo esquema/ACL e procedimento de recuperação. Não enviar o backup a
   artefatos de CI. Documentar limites do restore ensaiado e configurações externas.
2. Suspender ensaios concorrentes, confirmar projeto e aprovar o preflight. Revisar
   limites de locks/statement da sessão de implantação; esta migração **não define
   lock_timeout**. Um valor 5s usado pelo pacote A–E antigo não é herdado aqui.
   Não aumentar timeouts globais nem permissões de papéis.
3. Conferir localmente:

   ```bash
   sha256sum supabase/migrations/202611170001_commercial_bootstrap.sql
   wc -c supabase/migrations/202611170001_commercial_bootstrap.sql
   ```

4. Após autorização, executar o arquivo inteiro no SQL Editor, uma única sessão,
   sem fatiar, sem scripts de fixtures, sem reaplicar versões anteriores.
5. Se erro/timeout/resposta incerta, não repetir. Inspecionar catálogo novamente
   para distinguir rollback de commit confirmado sem resposta. Objetos parciais
   ou estado inesperado são bloqueadores. Nunca remover guardas para prosseguir.
6. Após sucesso, usar `supabase/checks/commercial_031_readonly.sql` e repetir a
   parte de tabelas/políticas do preflight. Esperar **18 tabelas** com RLS;
   duas comerciais sem SELECT/DML comum e duas RPCs públicas autenticadas,
   helpers privados, anon negado, `search_path` fixo. Os testes de ausência e
   corpos antigos do preflight são **pré-implantação**, não expectativas posteriores.
   Administrador confirma separadamente rollout=false e zero concessões criadas
   pela migração. Não chamar ensure/status pelo SQL Editor como prova de RLS real.

## Autorização individual futura

Administrador escolhe uma conta fictícia **nova**, confirmada, não anônima, não
legada, sem qualquer inscrição de testes, dados/versões/recibos v1 ou estado v2.
Inspecionar somente contagens por esse UUID no ambiente protegido, sem divulgá-lo
no relatório. Também conferir ausência de filas, restaurações e conflitos nos dois
workspaces locais, que deverão ser novos. Nunca limpar um workspace para elegibilidade.

Após autorização específica, o comando administrativo mínimo é uma transação com
`INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES
('<UUID fictício novo previamente conferido>'::uuid,true)` e
`UPDATE public.noctua_commercial_rollout SET enabled=true WHERE singleton`.
Exigir exatamente uma linha em cada operação e confirmar o UUID/projeto antes.
Não usar UPSERT sobre uma concessão existente, UPDATE de protocolo/escopo, allowlist
de testes nem papel privilegiado dentro do aplicativo. Se houver concessões anteriores,
não ligar o rollout global sem revisar seu efeito sobre todas elas. Interromper ante
qualquer estado prévio inesperado. Não executar este modelo nesta preparação.

O login real da conta autorizada deve retornar NEW; ensure passa a READY, inicializa
V2/E atomicamente e sem intervenção no menu. Corridas entre aparelhos e respostas
perdidas reutilizam a admissão. REVIEW/INELIGIBLE não autorizam limpar dados nem
forçar estágio. O servidor, não o APK, decide a admissão.

## APK paralelo e compilação sem operações hospedadas

Reutilizado `.github/workflows/homologacao.yml`, chamado por `android.yml`.
A branch comercial com `homologacao_only=true` passa `commercial_auto=true`;
a branch anterior mantém o modo antigo. Somente a chave pública é encaminhada,
sem `secrets: inherit`, contas fictícias ou Secrets de assinatura permanente.

```bash
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
  --ref sync/academic-commercial-auto-031 -f homologacao_only=true
```

Esse caminho compila, testa JVM e inspeciona o APK; não executa SQL, instala apps,
faz login ou dispara provas hospedadas. O default comercial da distribuição oficial
permanece desligado. Exige o Secret de build `NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`,
com a chave **pública** de sscexzxtqdcsqdlcytum. Sua presença não pôde ser consultada
pela API (403); não confundir essa limitação com prova de ausência. Não reutilizar a
chave pública de produção; nunca solicitar service_role, senhas ou tokens pelo chat.
O gate local sem chave falhou como esperado, **sem fallback para produção**.

Identidade exigida: `br.com.diariodeclasse.homologacao`, label **NOcTuA Homologação**,
DEBUG=true, ACADEMIC_COMMERCIAL=true, HTTPS exclusivo
`https://sscexzxtqdcsqdlcytum.supabase.co`, callback
`br.com.diariodeclasse.homologacao://auth/callback`. Configurar futuramente esse
retorno no projeto correto, preservando validação de esquema/host/path e PKCE
S256/estado/expiração. O app oficial usa outro esquema. Não se alterou configuração
de redirect hospedada nesta tarefa; SMTP/confirmação real continuam pré-requisitos.

Build bloqueia chave ausente, inválida ou igual à de produção. APK inspecionado
para endpoint, identificador, callback, ausência de UID compartilhado, backup Android
desligado, HTTPS, ausência de cliente de testes/segredos e certificado diferente do
permanente. Validade da chave no servidor só pode ser comprovada na futura Auth real.
SHA/tamanho/certificado e resultado da compilação serão registrados na evidência
específica desta preparação. Não instalar sobre homologação anterior: certificado
debug efêmero pode diferir. O APK anterior #206 tinha certificado
`9864b5240c60d2557b77d86d8a429358d32c06c0a5d77de16915ced67dc1b21a`.
Usar exclusivamente dois AVDs **novos**, com diretórios/dados independentes, sem
instalar, desinstalar ou limpar os AVDs e o celular existentes.

Callback da variante: `br.com.diariodeclasse.homologacao://auth/callback`.
Conferir a autorização desse retorno **somente em homologação**; o parâmetro
de estado PKCE pode exigir o padrão de Redirect URLs já usado pelo projeto
(`br.com.diariodeclasse.homologacao://auth/callback**`). O aplicativo continua
validando esquema/host/caminho exatos, estado de uso único, prazo e S256. Não
autorizar localhost como substituto e não remover validações para aceitar o link.
Esta preparação não altera Redirect URLs, SMTP nem confirmação de e-mail.

Reprodução da compilação sem sessão/SQL hospedado (a chave pública exclusiva
precisa estar no Secret de build já existente):

```bash
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
  --ref sync/academic-commercial-auto-031 -f homologacao_only=true
```

O dispatcher reutiliza `homologacao.yml`, passa somente
`NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`, e seleciona o opt-in comercial apenas nesta
branch. Não recebe Secrets de contas ou assinatura permanente. Para compilação
local, configurar essa variável pública sem imprimir seu valor e executar
`./gradlew -PnoctuaAcademicCommercial=true testHomologacaoUnitTest assembleHomologacao`;
validar com `scripts/validate-homologacao.py --expect-commercial --apk
app/build/outputs/apk/homologacao/app-homologacao.apk --build-tools
"$ANDROID_HOME/build-tools/35.0.0"`. Não usar a chave de produção ou uma chave fictícia.

## Roteiro hospedado futuro — dois AVDs, Auth real

| Etapa | Operações e efeitos que precisarão de autorização |
| --- | --- |
| Inspeção | Catálogo/contagens específicas somente leitura; não autentica por si só RLS |
| Cadastro/login | Auth cria conta/sessões; confirmar e-mail sem desativar segurança; logs sem identificadores |
| Primeira abertura A | Status é leitura; ensure grava admissão/protocolo/head da própria conta; nenhum botão A–E |
| Trabalho A | Turma, quatro bimestres, alunos e dados fictícios dos quinze tipos: staging/commit/versões/recibos são escritas reais |
| Login B | Mesmo usuário em workspace vazio: recuperação automática antes de envio; comparar IDs, quantidades, hashes e revisões; ausência local não exclui remoto |
| Bidirecional/offline | Alterar registros distintos em A/B; desligar rede de um AVD, editar, reiniciar, reconectar; conferir fila persistente, replay e recibos sem duplicação |
| Conflito | Duas revisões válidas da mesma nota; dispensa preserva variantes, maior/menor requer decisão explícita e auditoria; terceira revisão bloqueia decisão obsoleta |
| Históricos | FJ e motivo, notas zero/null, diário, remanejamento válido e auditorias imutáveis; preservar regras de disciplina/data |
| Isolamento | Outro usuário comum sem concessão não recebe direitos comerciais; tentar leitura/alteração de IDs alheios sob autorização própria, sem grants extras |
| Restauração | Backup criptografado exclusivamente fictício, restauração só em AVD descartável, pausa e reconciliação explícita; decisão/recibo remoto requer autorização de escrita |
| Encerramento | Logout somente das sessões criadas pelo ensaio; nenhuma limpeza remota automática |

As quinze entidades são classes, terms, students, activities, grades, lessons,
marks, recompositions, lesson_records, relocations, weekly_timetables,
class_archives, preserved_histories, export_rows e import_drafts. Presença no
contrato não significa presença no conjunto hospedado: relatar quantidade real por
tipo, tipos ausentes e hashes sanitizados; nenhum conteúdo escolar em artefatos.
Reutilizar provas locais existentes em vez de executar cargas hospedadas de 5.171
registros. Medir banco/RPC, rede e aplicação Room separadamente. Não tratar skips
como sucesso nem Auth sintético como confirmação de Auth real.

## Contingência, riscos e critérios de interrupção

- Migração transacional falha: conferir catálogo, não reaplicar cegamente. Backup
  administrativo é contingência separada, não se restaura por cima sem diagnóstico
  e autorização. Nenhum downgrade, DROP ou exclusão de histórico para reverter.
- Após ativação autorizada, revogar `enabled` da concessão afetada ou desligar
  rollout sob nova autorização interrompe o caminho comercial no servidor. Mantém
  dados, inicialização, filas e revisões. O caminho de testes permanece separado;
  o rollout não é um interruptor desse caminho. Não mudar conta comercial para testes.
- Retirar o flag de implantação em build futuro não desfaz admissões ou dados.
  Não reinstalar APK antigo sobre o workspace nem limpar Room para 'voltar'.
- Em timeout, offline, conflito ou REVIEW, conservar operações e variantes; não
  aumentar timeout para mascarar custo nem tentar repetir mutações indefinidamente.
- Supabase Free: orçamento autenticado padrão de oito segundos é risco conhecido;
  valor efetivo/API/cliente e capacidade do projeto **não foram medidos aqui**.
  SHOW no editor não prova orçamento de RPC. Volumes grandes continuam limitados
  pelos relatórios H13; esta preparação não atesta capacidade institucional ampla.
- Pausa do projeto Free, quotas, entrega de e-mail/SMTP, redirects e desempenho
  real precisam ser conferidos pelo administrador sem contratar recursos pagos.
- Não iniciar alterações hospedadas sem backup, preflight aprovado, identidade
  do projeto conferida, autorização específica e conta inédita elegível.

## Estado da preparação

Oito testes estáticos offline aprovados, sintaxe Python e diff verificados; gate
Gradle sem chave comprovou bloqueio sem fallback. As provas SQL/Android da Fase 3.1
foram reaproveitadas, sem executar SQL novamente. A migração validada não foi editada.
Compilação **#209 / 44299568876 aprovada** em
`013448fc11fe38be1bf44c5e7516ba1e88133ac3`. O código de aplicação é idêntico ao
`ed550105301832f76780186e1740df9bd989dbb4` já validado; mudaram nesta rodada
workflow de compilação, validação de identidade, consulta preparada e documentação.
Foram executados somente build/testes JVM no job de homologação. SQL, testes
hospedados, H13 e assinatura permanente ficaram ignorados pelo escopo; não contam
como aprovados nessa execução.

Cada suíte (`testDebugUnitTest` e `testHomologacaoUnitTest`) teve **322 casos,
317 aprovados, cinco ignorados e zero falhas/erros**, em 45 classes de teste.
Os ignorados são H01–H04 hospedados e a sonda Auth legada. Os XMLs e o APK foram
baixados e conferidos, além das validações do próprio job. Nenhum novo ensaio
Android/SQL foi necessário para mudanças de preparação; as provas Android/SQL
anteriores continuam explicitamente locais e descartáveis.

- [Execução #209](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44299568876).
- [Baixar artefato noctua-homologacao-comercial-debug](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44299568876/artifacts/14425576261).
  Abrir autenticado no GitHub e baixar o ZIP; extrair
  `app/build/outputs/apk/homologacao/app-homologacao.apk`.
- APK: **13.036.622 bytes**, versão **0.22.0-dev-e2-homologacao / c45**,
  ID **br.com.diariodeclasse.homologacao**, nome **NOcTuA Homologação**,
  DEBUG=true, ACADEMIC_COMMERCIAL=true e projeto exclusivo **sscexzxtqdcsqdlcytum**.
- SHA-256 do APK:
  `6cf5a8295d41bcb0ba801a11e7505bee0d5d6983e81b21c8bec9b93b7790b6d8`.
- Certificado debug SHA-256:
  `14c8104440337ade51f9fc15d95419160222e23cc4d24f0b6015c3d7527470b8`.
  **Incompatível com o certificado da homologação #206** citado acima e diferente
  do certificado permanente. Não instalar sobre os apps anteriores. Nenhum AVD
  ou aparelho existente recebeu instalação nesta preparação.
- A chave pública foi aceita pelo gate de formato e separação da chave de
  produção; sua validade no backend ainda não foi testada. Não ler valores de
  Secrets nem interpretar uma compilação como login hospedado aprovado.
- [Evidência sanitizada desta preparação](evidence/FASE_32_PREPARACAO_0220.json).

Permanecem pendentes: catálogo/backup atuais de homologação, implantação comercial,
concessão individual, entrega de e-mail/redirects, autenticação real e roteiro em
dois AVDs. Produção, conta Aron Danone, main, assinatura oficial e instalações
existentes não foram alteradas. Não há liberação de APK oficial.
