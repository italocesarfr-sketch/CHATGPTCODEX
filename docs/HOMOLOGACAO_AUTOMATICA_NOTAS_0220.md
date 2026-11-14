# Homologação hospedada — sincronização automática e conflitos de notas

Preparação em **14/11/2026, UTC**. Repositório `italocesarfr-sketch/CHATGPTCODEX`,
branch `sync/academic-complete-0220`.

**Estado: plano revisável preparado; execução hospedada não autorizada nem
realizada.** Não é homologação antecipada nem executor hospedado já implementado.
Nesta tarefa só foram alterados este documento e seu relatório de evidências.

## 1. Base preservada e fatos verificados

| Item | Evidência / situação |
| --- | --- |
| Código aprovado | `b8206c39188cebf2bf0baa28c4c61164e08e85c5`, [CI 43627336525](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43627336525), `success` reconfirmado na API GitHub |
| HEAD inicial local/remoto | `388522747436cb106e559c5c65c164cb86b26978`; apenas documentação depois do código aprovado |
| Integridade | `git diff --exit-code b8206c3 HEAD -- app scripts .github supabase` sem diferenças |
| Prova local anterior | 313 JVM aprovados / cinco condicionais ignorados; 96 Android selecionados + oito compactos; 27 fases em dois AVDs; 18 decisões PostgreSQL e nove verificações HTTP |
| Projeto exclusivo previsto | `noctua-homologacao`, `sscexzxtqdcsqdlcytum`, `https://sscexzxtqdcsqdlcytum.supabase.co` |
| Produção | Fora do escopo; nenhum acesso ao Supabase de produção |
| Environment GitHub | `noctua-homologacao-h01-h04` existe; política customizada permite exatamente a branch de trabalho. A API mostrou somente proteção `branch_policy`, sem comprovação de revisão humana obrigatória |
| Secrets | A listagem por API foi negada (403). O PREFLIGHT offline seguro #207 confirmou disponibilidade/formato no Environment, sem expor valores nem criar sessão Supabase |
| Escopo E atual A/B | **Não consultado**: nenhuma sessão, login ou requisição Supabase nesta preparação |
| H05/H12 hospedados anteriores | A passou com 24 registros / nove tipos presentes. São evidências históricas, não inventário atual nem validação da nova inicialização |
| APK das novas funções | Não identificado nos artefatos publicados. O último build paralelo identificado é #206, anterior às funções |

A conta A demonstrou protocolo 2, escopo 5 e elegibilidade nos H05/H12 anteriores,
pois o executor recusava qualquer outro estado. Isso não comprova o estado atual
de A nem o escopo E atual de B. O relato anterior de isolamento A/B permanece
registrado como relato prático; não substitui as novas provas automatizadas.

Os seis tipos ausentes nos últimos inventários H05/H12 foram `recompositions`,
`import_drafts`, `export_rows`, `class_archives`, `preserved_histories` e
`weekly_timetables`. Não presumir que continuam ausentes nem anunciar recuperação
hospedada desses tipos sem registros realmente presentes.

## 2. Elegibilidade e configuração — sem alteração de escopo

No código aprovado, `academicTestEligible` exige DEBUG, workspace não legado e
identidade diferente do proprietário legado. `AccountWorkspaces` identifica o
workspace pelo UUID autenticado e mantém bancos distintos por conta.

A adoção automática exige, conjuntamente:

- Sessão válida/confirmada da identidade esperada em configuração de homologação.
- `noctua_academic_status_c` retornando `protocol=2`, `academicScope=5` e
  `testAllowed=true`. O nome da RPC de status continua esse, também no escopo E.
- Ausência de controle acadêmico anterior, filas, partes ou decisões órfãs e
  metadados v1 no workspace de instalação nova.
- Ausência de restauração/pausa/transição local a substituir. Não resolver o
  conflito legado, reassociar dados ou ampliar silenciosamente B/C/D para E.

Na futura fase autorizada P1, fazer essas consultas para A **e** B com os clientes
Auth normais, conferindo UUID/confirmado sem publicá-los. Se qualquer requisito
falhar, registrar `SCOPE_NOT_READY`/`IDENTITY_MISMATCH` e interromper. Não chamar
`activate`, `enableC`, `enableD`, `enableE`, criar conta ou mudar allowlist/RLS.
O perfil remoto deve estar pronto por autorização anterior; este ensaio não o
prepara no servidor. A `fixtureClassId` protegida de A deve existir no inventário;
se não existir, interromper com `FIXTURE_MISSING`, sem recriar a turma antiga.

O leitor H05 atual exige cabeça entre 1 e 256. Uma conta B já em E, mas vazia
(cabeça 0), precisa de ramo específico no novo adapter: status 0, pull E vazio,
workspace vazio e hashes/filas compatíveis, sem criar fixture para passar. Os
requisitos originais de H05/A não serão relaxados. B vazia demonstra isolamento,
não recuperação de tipos ausentes.

A configuração Android fixa app e callback em
`br.com.diariodeclasse.homologacao` e
`br.com.diariodeclasse.homologacao://auth/callback`. `AuthEnvironment` valida
projeto, URL exata, chave publishable e rejeita a chave de produção. As validações
PKCE de estado/verificador/retorno permanecem inalteradas. Login de contas já
confirmadas não valida novamente confirmação de e-mail ou recuperação de senha.

## 3. APK paralelo e assinatura — bloqueio antes de executar

O último artefato identificado é
[APK #206](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43614108749/artifacts/14121343254),
do commit `4346d55c192e85b64e2c9322b683339a1601ae86`.
Foi reconferido **offline**, usando os bytes já baixados do artefato:

| Campo | Valor reconferido |
| --- | --- |
| Nome | NOcTuA Homologação |
| applicationId | `br.com.diariodeclasse.homologacao` |
| Versão / código | `0.22.0-dev-e2-homologacao` / 45 |
| APK SHA-256 | `513a6a93bdb08599f95491b9eb397ad1588ac59c511e7945d099ff1bc1929580` |
| ZIP SHA-256 | `1e580f74e212b9ef72126b9095d5be2755643252c9741d6aac63715246d05ae1` |
| Certificado SHA-256 | `9864b5240c60d2557b77d86d8a429358d32c06c0a5d77de16915ced67dc1b21a` |
| Referência anterior | Certificado `b6dd351f4e3e1e633c7447e02f449316779e8ff0b14707f9981c80e363d92189` |
| Resultado | Ambos têm assinatura válida, sem v3/rotação; certificados distintos, portanto sem atualização compatível entre essas referências |

AAPT confirmou identidade/versão; `apksigner verify --print-certs` confirmou as
assinaturas. Manifesto: sem UID compartilhado, Auto Backup/cleartext desativados
e provider do pacote paralelo. DEX: referência de homologação presente, produção
e marcadores privados proibidos ausentes. A associação da chave pública opaca
ao projeto não foi consultada online.

**O #206 não contém sincronização automática nem o novo popup.** Não usá-lo para
aprovar essas funções. O artefato da CI funcional contém evidências, não um novo
APK paralelo de distribuição.

Antes do ensaio será necessário gerar um candidato paralelo pelo
`homologacao.yml` existente, a partir de SHA cujo código coincida com `b8206c3`.
Não há necessidade de mudança funcional/versão nesta preparação. O entrypoint
registrado é `android.yml`; despacho direto de `homologacao.yml` retornou 404 na
API atual. Com autorização posterior apenas para build, o comando existente é:

```bash
gh workflow run android.yml --ref sync/academic-complete-0220 \
  -f homologacao_only=true -f hosted_auth_only=false \
  -f h05_only=false -f h12_only=false \
  -f authorize_hosted_h01_h04=false \
  -f authorize_hosted_h05=false -f authorize_hosted_h12=false
```

**Não executado nesta tarefa.** Esse despacho só compila/testa/valida; não é
consentimento para login hospedado. Conferir SHA do fonte, APK, certificado,
callback, endpoint e ausência de credenciais privadas novamente no novo binário.
A chave pública deve vir da configuração de build existente. Não recuperar
keystore privado, não reutilizar assinatura de produção e não presumir que o
certificado Debug de outra execução será igual.

Instalar **os mesmos bytes** do novo APK em dois AVDs CI completamente novos,
com userdata independente e sem snapshots anteriores. Recusar qualquer app
oficial/paralelo/test APK pré-existente no destino; não usar `-r`, uninstall,
`pm clear` ou outro perfil para contornar incompatibilidade de certificado.
Não tocar nos emuladores existentes. O certificado de uma instalação do
proprietário não foi lido e sua compatibilidade continua sem verificação.

## 4. Infraestrutura reutilizada e lacunas do executor

Reutilizar Environment/credenciais de `hosted-auth-checks.py`, preparação/ADB/
sanitização de `h05-recovery.py`, `RecoveryInventory`, `H05ReadOnlyRemote`, clientes
Supabase reais e observação dos DAOs. Reutilizar a construção dos dois AVDs e a
prontidão observada de boot/Settings/Package das provas funcionais. Manter o app
acadêmico, Room 14, backup 12 e protocolo A–E aprovados.

O H05 existente **não prova o caminho automático novo**:

- `AcademicTestApplication.automaticAcademicRecovery()` só permite os cenários
  locais `e-auto-*`, não o modo H05 hospedado.
- O H05 controla o startup, fecha o coordenador e chama explicitamente
  `AcademicEngine.activate()` e `run()` depois do consentimento do teste.
- Seu guard de AVD admite um único aparelho. Não editar esse guard para aceitar
  dispositivos arbitrários nem retirar suas restrições.
- H12 permite exatamente sua auditoria KEEP_REMOTE específica; não autoriza
  decisões de conflito de notas ou alterações de notas.

**Pendente antes de habilitar execução:** adaptar somente a infraestrutura de
instrumentação/runner, em commit separado e validado localmente, para login pela
UI real, startup normal e dois AVDs nominados da execução, mantendo proteções
H05/H12. O novo modo deve ter autorização independente; não reutilizar seus
flags como permissão implícita. A Application normal deve continuar usando
`automaticAcademicRecovery()=true`; a injeção de testes só poderá habilitar esse
caminho após admissão explícita no APK de homologação.

A prova não poderá chamar `engine.activate/run`, `coordinator.request`, botão de
sincronização, importar/restaurar backup ou gravar controle E para fazer a
recuperação funcionar. O login UI e os sinais normais do produto devem fazê-lo.
O teste pode ler estado/fingerprint no sandbox próprio e aplicar decorators que
**recusam** operações fora da autorização antes do HTTP, sem fingir confirmação.

Não usar `scripts/audit-sync-hosted.py`: é um script legado de probes hospedados
com referência de produção no fonte, não um preflight offline. Não foi executado.
Não redirecionar as fixtures JWT/SQL administrativas locais para Supabase.

## 5. Separação das autorizações e efeitos

P1/P2/P3 são **identificadores deste plano**, ainda sem novos inputs de workflow
ou executor implementados. Todos devem começar bloqueados. As autorizações
H01–H04, H05 e H12 já utilizadas não valem para essas fases.

| Fase | Prova | Dados acadêmicos remotos | Outros efeitos no servidor |
| --- | --- | --- | --- |
| P0 | Verificações locais desta preparação | Nenhuma leitura/escrita Supabase | Nenhuma sessão Auth criada |
| P1 | Elegibilidade A/B, login visual e recuperação automática em dois AVDs limpos; isolamento B | Somente leituras status/pull E e verificações próprias permitidas por RLS | Login/renovação criam/atualizam sessões Auth; logout somente das sessões criadas |
| P2 | Fixture nova, envio automático, offline/reconexão, reinício/logout/reentrada e atualização inversa | Criação de até oito registros novos; mudanças posteriores só na nota criada pelo ensaio | Staging, lotes, versões, cabeças e recibos normais do protocolo; nenhuma decisão de conflito autorizada |
| P3 | Popup: dispensa, maior/menor nota, terceira revisão e auditoria | Alterações apenas no valor da nota pertencente à fixture P2 | Até duas decisões aceitas e, no máximo, uma recusa auditada excepcional; metadata/staging/recibos correspondentes |

“Somente leitura” em P1 significa **sem escrita acadêmica, staging ou auditoria**;
login/refresh/logout têm efeitos Auth e também precisam de autorização. Até a
escolha KEEP_REMOTE, sem alteração de nota, **grava auditoria**. P3 não é um teste
somente leitura. Revisão desatualizada detectada antes da confirmação deve
bloquear sem criar decisão. Uma recusa que chegue à RPC pode deixar auditoria e
partes: registrar isso, não limpar nem tentar apagar o registro de decisão.

### Limites propostos para o futuro executor

- Projeto fixo, branch fixa, runner `github-hosted`, somente contas A/B existentes
  e confirmadas. No máximo dois AVDs próprios por fase; nenhuma conta nova.
- Até oito sessões Auth criadas por autorização, incluindo controlador de
  inventário e eventuais reentradas; registrar privadamente quais pertencem ao
  teste e encerrar somente essas, com `scope=local`.
- Reutilizar limites H05: até 1.000 registros por lote/inventário vivo, 4.096
  versões, 8 MiB por inventário, 128 páginas, 256 lotes e 512 consultas por
  instalação. Inventário incompleto/limite atingido impede aprovação.
- P2/P3 também exigirão baseline com até **671 registros vivos**, sem geração de
  carga. É teto de admissão, não recomendação de capacidade institucional.
- Fixture mínima de oito registros; lote de envio com até 16 mudanças / 64 KiB.
  No máximo 16 commits acadêmicos distintos e 32 tentativas de commit no conjunto
  P2/P3; repetição deve usar o mesmo identificador persistido. Até três registros
  de decisão, dos quais dois aceitos são esperados. Qualquer excesso bloqueia.
- Até cinco minutos de execução de cada prova depois do login; até 180 segundos
  por convergência observada, incluindo polling em primeiro plano. Sem sleeps
  fixos para afirmar sucesso. A provisão/build do AVD é contabilizada separadamente.
- Não ampliar timeouts. O orçamento de oito segundos do papel authenticated é
  referência de risco, não limite do projeto reconferido nesta tarefa. Uma única
  nota ainda passa pela validação integral do acervo. API/cliente/SQL podem ter
  limites diferentes; medir RTT/tempo Android sem apresentá-los como tempo SQL.
- Não induzir timeout, resposta perdida, rollback, morte durante commit ou carga
  de 5.171 registros no hospedado. Essas provas continuam locais/descartáveis.

Somente recursos gratuitos existentes. Não contratar serviços nem mudar o plano.
A ocupação atual e os limites efetivos do projeto não foram consultados; ao
atingir limite ou timeout, interromper sem ampliar orçamento/configuração.

Esses tetos deverão ser impostos pelo adapter antes da execução, não tratados
apenas como orientação. Não aprovar fase parcial por truncamento de inventário.

## 6. Proteção dos registros fictícios anteriores

RLS protege contas diferentes, mas **não** impede o professor A de alterar um
registro antigo da própria conta. É indispensável guard adicional de ensaio.
Ele ainda não está implementado para P2/P3; esse é bloqueio de execução.

1. Capturar baseline paginada completa, revisões, tombstones, recibos e hashes
   normalizados, com o leitor já existente. Conservar dados brutos apenas no
   temporário privado; relatório público só contém contagens/hashes/booleanos.
2. Coordenar uma janela sem edição das contas fictícias pelos emuladores do
   proprietário; não fechá-los/limpá-los nem revogar suas sessões. Se a cabeça
   mudar por ator alheio ao ensaio, interromper com `REMOTE_CHANGED`.
3. Gerar uma identidade de campanha e IDs estáveis novos. Verificar ausência de
   **todas** as chaves no baseline antes de criar. Nomes de turmas ou estudantes
   não autorizam pertencimento; não deduzir permissão a partir de texto livre.
4. Fixture P2: uma turma nova com quatro bimestres, uma matrícula com pessoa
   também nova, uma atividade de máximo 10 e uma nota inicial válida: **8 linhas**.
   Usar repositório/UI existentes, mesmo ano letivo, disciplina/datas/bimestre
   compatíveis. Sem vínculo com matrículas ou históricos anteriores.
5. Guard de transporte P2: recusar antes do HTTP qualquer tombstone, operação de
   ativação/ampliação, decisão ou mudança em chave anterior. Após a criação,
   permitir atualizações somente de `grades.value` na chave nova conhecida.
   Confirmar propriedade da fixture pelos recibos de criação e IDs, não só nome.
6. Em outra autorização P3, reconstruir/conferir o manifesto da fixture da P2,
   usando a execução de origem e seus recibos/digests. Não publicar IDs no
   relatório nem selecionar uma nota antiga por conveniência. Se o vínculo não
   puder ser comprovado, interromper; não adotar uma fixture por semelhança.
7. Guard P3: mudanças apenas no valor dessa nota e decisões cuja evidência/
   alterações estejam restritas à mesma chave. Rejeitar conjuntos interdependentes
   ou qualquer modificação adicional proposta pela reconciliação.
8. Comparar ao final **cada chave anterior**, payload normalizado, revisão,
   tombstone e histórico: devem permanecer idênticos. Auditar ledger anterior
   sem modificar suas entradas. Cabeça global, sessões, novos recibos/versões e
   novas auditorias avançam legitimamente e são efeitos expressamente declarados.

Nenhuma restauração ou exclusão hospedada faz parte dessas fases. Novos registros
fictícios ficarão no projeto para rastreabilidade; removê-los futuramente exigirá
outra autorização. Sincronização não é backup administrativo. Os mecanismos
lógicos/cifrados existentes não serão removidos ou usados sobre dados anteriores.

## 7. Casos hospedados preparados no plano

### P1 — Elegibilidade, recuperação e isolamento; sem escrita acadêmica

1. Validar Secrets offline com o helper existente, projeto exato e UUIDs distintos
   esperados de A/B. Não imprimir valores nem executar se faltar configuração.
2. Com autorização P1, autenticar A/B, validar confirmação, identidade e status E.
   Ler baseline e permissões de leitura dos próprios recibos/auditorias necessárias
   às fases seguintes; acesso negado impede o passo dependente, sem ampliar RLS.
3. Provisionar dois AVDs novos, confirmar ausência de Room/sessões/apps anteriores,
   instalar somente o mesmo APK paralelo admitido. Login A pela UI em ambos.
4. O startup normal deve adotar a autorização E já existente e recuperar sozinho.
   Decorator somente leitura reutilizado de H05 deve bloquear **qualquer** envio/
   staging/decisão antes do HTTP; tentativa bloqueada também reprova o caso.
5. Conferir os tipos efetivamente presentes, IDs/relacionamentos/revisões, hashes,
   chaves estrangeiras, cursor/cabeça, recibos e ausência de filas/conflitos/partes.
   O segundo AVD não usa backup, restauração ou ação manual de sincronização.
6. Comparar remoto antes/depois: mesmas versões acadêmicas, cabeça e auditorias;
   zero tentativas de escrita. Dados ausentes por tipo são `NOT_PRESENT`, não
   aprovados por hipótese. Uma instalação vazia não autoriza exclusão.
7. No segundo AVD novo, sair de A e entrar em B. Recuperar só B quando elegível;
   consultas B por chaves conhecidas de A devem retornar vazio. Voltar a A e
   conferir o workspace original. Nenhum dado local é limpo para trocar de conta.
8. Encerrar exclusivamente as sessões criadas pelo ensaio e descartar só os AVDs
   da execução. Se houver falha, preservar diagnóstico redigido, sem repetir.

### P2 — Automação de gravações, offline e logout; fixture exclusivamente nova

Pré-condições: P1 aprovada para o candidato, nova autorização P2 e guards de
fixture/baseline implementados e aprovados localmente. A fase usa dois AVDs novos
com A; B não recebe escrita acadêmica.

1. Recuperar o baseline automaticamente; exigir filas limpas. Criar a fixture
   nova pelo fluxo/repositório existente. Cada transação conserva seus IDs;
   registro acadêmico e captura ficam juntos no Room.
2. Sem tocar em sincronizar, aguardar o envio, recibo confirmado e recebimento no
   outro AVD. Conferir exatamente as oito chaves; nenhuma duplicação por pessoa
   ou matrícula, nenhum registro anterior alterado.
3. Desconectar **somente** o novo AVD 1. Editar sua nota, conferir commit Room e
   estado capturado pendente; não representar ausência por NR. Fechar/reabrir o
   aplicativo nesse mesmo AVD, conferindo persistência sem reinstalar/limpar.
4. Sair da conta offline com alteração ainda pendente. Conferir bloqueio da área
   acadêmica após logout. **Não exigir login real offline.** Reconectar, entrar
   novamente e verificar que a fila do mesmo workspace envia sozinha a intenção
   preservada. O outro AVD deve receber a revisão correta.
5. Fazer uma edição normal sobre a revisão atual no AVD 2; exigir convergência
   inversa sem popup de conflito ou nova identidade de registro.
6. Conferir revisões/recibos/cursor/operação e estados verdadeiros: aguardando
   internet/envio, sincronizando e sincronizado apenas depois de confirmação.
   Reintentos normais podem ocorrer dentro do orçamento, com o mesmo ID; não
   injetar perda de resposta nem reiniciar o aplicativo para recuperar a conexão.
7. Comparar integralmente as chaves anteriores. Se logout offline não conseguiu
   encerrar a sessão remota criada, o executor deve conservar privadamente essa
   referência para limpeza `scope=local` após reconexão; nunca revogar sessões
   do proprietário/AVD 2 para tentar limpar a sessão do ensaio.

P2 não homologa escritas nos 15 tipos: o escopo mínimo escreve turmas, bimestres,
matrícula, atividade e nota. Frequência/FJ/diário e demais tipos existentes são
conferidos na recuperação; extensões de escrita exigem fixtures e autorização
próprias. As provas locais dos 15 tipos continuam evidência separada.

### P3 — Popup, revisão concorrente e auditoria

Pré-condições: P2 aprovada e fixture reconhecida por IDs/recibos, inventário sem
conflitos anteriores, nova autorização incluindo escritas **e** decisões.

1. Em dois AVDs independentes autenticados como A, partir da mesma revisão
   confirmada da nota nova. Offline no AVD 1, alterar para **6**. No AVD 2,
   alterar para **8**, confirmar envio e reconectar o AVD 1.
2. Conferir popup com aluno, turma, atividade e valores 6/8. Fechar por Voltar/
   toque externo: nenhuma decisão/auditoria nova, cursor não avançado sobre o
   conflito e ambas as variantes persistentes. Reabrir pela conferência existente.
3. Escolher **Sim, manter 8** pela UI. Nesse arranjo a maior está na nuvem:
   KEEP_REMOTE não deve criar revisão de nota desnecessária, mas deve gravar
   **uma auditoria**. Exigir 8 nos dois dispositivos e versões originais no ledger.
4. Confirmar uma edição posterior normal sobre a revisão atual, sem conflito.
   Para o segundo conflito, AVD 1 offline registra 6 e AVD 2 registra **9**.
   Abrir a prévia 6/9 no AVD 1; com ela aberta, AVD 2 altera a mesma nota para
   **9,5** e confirma pelo fluxo Android normal.
5. Tentar decidir usando a prévia antiga: exigir bloqueio, valores preservados e
   nenhuma escrita baseada em versão obsoleta. Atualizar a prévia explicitamente.
6. Na prévia 6/9,5, escolher **Não, manter 6**. KEEP_LOCAL deve criar apenas a
   revisão autorizada dessa nota e **uma segunda auditoria**; ambos os AVDs
   convergem para 6. Nenhuma outra chave muda.
7. Verificar `noctua_decision_academic_e` e, com SELECT próprio permitido por RLS,
   `noctua_e_decisions`: escolha, variantes local/remota, revisões, resposta e
   identidade idempotente da decisão. Recibo sem conferir evidência não basta
   para declarar auditoria completa. Não publicar IDs/payloads dessa consulta.
8. Fechar/reabrir o app nos AVDs próprios, conferir convergência, filas e histórico
   mantidos. Corridas **pós-preflight**, resposta perdida e morte durante commit
   ficam nos testes descartáveis já aprovados, não serão forçadas no hospedado.

Notas iguais, nulas, exclusões, máximos divergentes e conjuntos dependentes
continuam nas regras seguras existentes; não forçar o popup numérico para
aprovar um caso inelegível. Nenhuma dessas variantes ganha aprovação hospedada
apenas pelos casos numéricos acima.

## 8. Credenciais, relatórios e controles do futuro workflow

- Reutilizar `NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON` (A/B/fixtureClassId existentes) e
  `NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`. `fixtureClassId` anterior será apenas
  testemunha de recuperação/isolamento; **nunca alvo das escritas P2/P3**.
- Sem senhas/tokens em inputs, argumentos, Git, logs, artefatos ou chat. Validar
  o Secret no Environment; passar apenas os nomes necessários explicitamente
  ao workflow reutilizável. Não copiar `secrets: inherit` para os novos runners.
- O build existente de homologação usa `secrets: inherit` no chamador e consome
  somente a publishable key; não foi modificado nesta preparação. Os novos jobs
  hospedados devem usar passagem restrita, como H01–H04/H05/H12.
- Segredos entram por stdin em arquivos 0600 no temporário privado e UID do app;
  só a publishable key é configuração pública incorporada ao APK.
- Nenhum dump de UI, logcat, SQLite, backup, sessão, relatório bruto JUnit ou
  screenshot hospedado em artefatos públicos. Publicar somente esquema redigido
  com casos/estágios/códigos fixos, status HTTP, contagens, hashes agregados,
  tempos, tetos/efeitos observados e conclusões. IDs/e-mails/UUIDs não são saída.
- Workflow apenas `workflow_dispatch`, branch fixa e Environment existente;
  `PREFLIGHT` deve ser padrão sem rede/sem AVD. Autorizações separadas para P1,
  P2 e P3, nenhuma herdada de H05/H12/H01–H04. O mecanismo de aprovação humana
  precisa ser explícito; não presumir reviewers obrigatórios no Environment atual.
- Uma execução hospedada por projeto/conta/campanha, sem cancelamento automático
  por outro push. Não iniciar duas autorizações concorrentes, não usar self-hosted
  ou serial/host ADB fornecido externamente.
- Falha, teto excedido ou resultado ignorado significa `NOT_APPROVED`, não sucesso.
  Reexecução integral exige nova autorização. Reintento idempotente normal dentro
  do mesmo ensaio e orçamento não equivale a autorização para repetir a suíte.

## 9. Contingência e critérios de aprovação

Interromper antes do envio se: projeto/identidade/escopo incorretos; Secret ou
chave ausente; APK antigo/incompatível; instalação não limpa; baseline incompleta;
conflito anterior; colisão de chaves; corpo fora do manifesto; delta antigo ou
exclusão; limite de recursos; auditoria não consultável; alteração alheia à campanha.

Em falha inesperada, parar produtores/sincronizadores dos AVDs **da CI**, encerrar
somente as sessões criadas e conservar diagnóstico sanitizado. Não apagar fixture,
revisões, staging, recibos ou auditorias do servidor; não restaurar o baseline
como rollback. Um commit remoto confirmado pode ter sido concluído mesmo sem
resposta ao cliente: registrar a incerteza e exigir análise de recibo autorizada,
sem inventar confirmação nem reenviar com ID novo.

Os AVDs são descartáveis. Se uma execução falhar e for encerrada, uma fixture
nova ainda não enviada pode não sobreviver ao descarte do runner; não confundir
isso com preservação comprovada de dados reais. As provas de reinício devem
concluir no mesmo AVD antes do descarte. Nunca exportar banco/sessão privado para
artefato público como tentativa de contingência.

Aprovar cada fase somente se todos os seus casos executarem, nenhum ignorado,
com inventário íntegro, hashes/revisões/referências iguais, filas/cursor corretos,
zero mudança em chaves anteriores e efeitos remotos dentro da autorização.
P1 aprovada não autoriza P2; P2 aprovada não autoriza P3. Nenhuma fase libera
produção, atualização do app oficial, migração do proprietário ou release estável.

## 10. Procedimento de autorização independente

Não há nova solicitação de autorização nesta entrega; os textos abaixo são
modelos para uso **após** candidato/adapter/guards aprovados e plano conferido.
Não executar comandos de H05/H12 como substituto dos novos casos.

**Build paralelo, sem teste hospedado:**

> Autorizo compilar e verificar um novo APK paralelo pelo homologacao.yml a partir
> do código aprovado b8206c3, sem login hospedado nem instalação nos meus dispositivos.

**P1 — somente leitura acadêmica:**

> Autorizo P1 exclusivamente em sscexzxtqdcsqdlcytum, com contas fictícias A/B,
> login/renovação, consultas E e recibos/auditorias próprios, dois novos AVDs da CI,
> recuperação automática e troca de conta; logout somente das sessões criadas.
> Zero escrita acadêmica, staging ou decisão.

**P2 — gravações limitadas à campanha nova:**

> Após P1 aprovada, autorizo P2 em sscexzxtqdcsqdlcytum, na conta A e dois novos
> AVDs, criando somente a fixture de oito registros novos e alterando somente
> sua nota para os ensaios offline/reconexão/logout/sincronização. Autorizo os
> metadados/recibos normais, nos limites deste plano. Não autorizo auditorias de
> conflito, exclusões ou mudanças em registros anteriores.

**P3 — notas e auditorias da fixture comprovada:**

> Após P2 aprovada, autorizo P3 em sscexzxtqdcsqdlcytum, exclusivamente na nota
> nova da campanha identificada pela execução P2, com cenários 6/8 e 6/9/9,5,
> escolhas maior/menor pela UI e até duas decisões aceitas, com no máximo uma
> recusa auditada excepcional, dentro dos limites. Sem modificar outras chaves,
> contas, RLS, escopos ou produção; sem limpeza automática dos dados criados.

Cada autorização futura deve indicar SHA do candidato, execução P1/P2 exigida,
modo exato, campanha e limites. Consentimento genérico “pode testar” não substitui
os escopos acima. Em qualquer bloqueio, informar o código; não relaxar guardas.

## 11. Ações mínimas do proprietário e próximos passos do Codex

**Proprietário — sem enviar credenciais pelo chat:**

1. Manter os dois Secrets já admitidos pelo PREFLIGHT #207. Não é necessário
   reenviar ou reconfigurar valores. Confirmar somente que continuam sendo as
   contas fictícias autorizadas; a associação efetiva da chave/projeto e o escopo
   atual serão verificados pelo login/status autorizado em P1. Não mudar
   produção/RLS/allowlists para satisfazer um teste.
2. Reservar uma janela sem edição dessas contas durante o ensaio e revisar os
   limites/efeitos da campanha. Não desinstalar/limpar seus emuladores.
3. Depois da preparação executável, autorizar build e P1; autorizar P2 e P3
   separadamente somente após as evidências das fases antecedentes.

**Codex — pode continuar localmente, sem acesso Supabase:** preparar adapter UI,
guards de duas instalações/manifesto, tetos de operações e relatório redigido;
validá-los com fixtures e PostgreSQL/PostgREST descartáveis. Em seguida conferir
novo APK paralelo e apresentar pré-verificação, antes de pedir execução hospedada.
O workflow atual H05 não basta para isso. Nesta tarefa não foi criado novo runner.
Houve somente despacho do PREFLIGHT offline existente; nenhum build, teste
hospedado, implantação ou instalação foi executado.

## 12. Pré-verificações efetivamente executadas nesta entrega

- Branch/working tree/HEAD/fetch e comparação com SHA aprovado; `.agents/`
  preexistente preservada, sem inclusão no commit.
- Leitura GitHub de CI/artefatos/Environment/política de branch; consultas de nomes
  de Secrets negadas (403), sem pedir valores. A validação privada foi feita pelo
  runner offline já existente, que só retorna o resumo redigido.
- [PREFLIGHT #207 / 43632066547](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632066547),
  SHA `388522747436cb106e559c5c65c164cb86b26978`, `success`:
  `PREREQUISITES_READY_NOT_EXECUTED`, `hosted_requests=false`. Todos os jobs/steps
  de build, AVD, regressão e execução hospedada ficaram desativados; não são
  testes aprovados. Artefato [h05-hospedado-sanitizado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632066547/artifacts/14128282785)
  conferido por SHA-256 `3bd3ca22a7bf0fba9eefe4c991927d6e6344c58d824847a0cb847c7a961b7c98`.
  O nome histórico do artefato não significa teste hospedado executado.
- `python3 scripts/test-hosted-auth-checks.py`: **16 aprovados**.
- `python3 scripts/test-h05-recovery.py`: **15 aprovados**.
- `python3 scripts/test-h12-recovery.py`: **14 aprovados**.
Esses 45 testes usam entradas/credenciais sintéticas e mocks; não abrem sessões
hospedadas. O PREFLIGHT #207, separadamente, validou os formatos dos Secrets no
Environment, mas não as credenciais contra o Supabase.

- `py_compile` dos helpers e `git diff --check`: aprovados.
- SHA-256 do ZIP/APK e inspeção local AAPT/apksigner/DEX das duas referências:
  aprovadas para identidade do artefato antigo, **sem aprovação do novo fluxo**.
- Nenhuma repetição da suíte longa; as evidências da CI 43627336525 permanecem
  válidas para o código inalterado. Nenhum teste ignorado foi contado como aprovado.

[Relatório estruturado desta preparação](evidence/PREPARACAO_AUTOMATICA_HOSPEDADA_0220.json).
[Implementação e provas locais anteriores](SINCRONIZACAO_AUTOMATICA_NOTAS_0220.md).

**Decisão:** preparação documental/local concluída; execução hospedada bloqueada
até novo APK, adapter/guards comprovados, elegibilidade remota conferida sob
autorização P1 e autorização independente das operações seguintes. Nenhuma homologação hospedada nova foi declarada.


Comando do PREFLIGHT **já executado**, sem autorização hospedada:

```bash
gh workflow run android.yml --ref sync/academic-complete-0220 \
  -f h05_only=true -f h05_mode=PREFLIGHT -f authorize_hosted_h05=false \
  -f hosted_auth_only=false -f homologacao_only=false -f h12_only=false \
  -f authorize_hosted_h12=false -f authorize_hosted_h01_h04=false
```

Esse modo só verifica preparação offline. Repeti-lo não comprova a recuperação
automática nem concede autorização para HOSTED.
