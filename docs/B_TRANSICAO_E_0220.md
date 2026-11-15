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
foi retirada antes de qualquer uso hospedado: era desnecessária. Não existe nova
migração SQL a implantar. Os scripts A–E originais e o pacote publicado não foram
alterados. O teste descartável confere recuos E→C, E→D e D→C recusados e preserva
ACL/RLS/SECURITY DEFINER/search_path. A definição hospedada não foi consultada nesta
tarefa; uma futura conferência administrativa deve avaliar a definição final D,
sem reaplicar o script C antigo isoladamente.

Não afirmar que houve vulnerabilidade no protocolo final A–E com base na definição
intermediária de C. O histórico dos commits conserva a hipótese e sua retirada.

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

Resultados aguardam execução; não considerar os cenários aprovados antes de
examinar o artefato sanitizado. Quatro guardas estáticas passaram; compilação da
instrumentação inicial passou. A regressão PostgreSQL C/D/E e as 33 regressões
Room selecionadas serão executadas junto da nova prova HTTP/Room.

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
4. Conferir que o ambiente utiliza as definições finais A–E já instaladas. Gerar prévia B→C, verificar
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

Não é necessária autorização para instalar nova migração: a candidata foi retirada.
Após revisar os inventários B e o ambiente/workspace seguro, o texto de autorização
proposto é:

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

Esse texto é uma proposta revisável, não autorizações recebidas. Ainda faltam
inventários locais, definição do workspace seguro e revisão da binding autenticada
e da barreira de comandos para operá-lo. Isso não é substituído por uma instalação
vazia na CI.


## Execuções intermediárias

- #28 / `43665602114`, fonte `bee1de3`: C 20/20; D 26/27, uma falha porque a fixture
  de exclusão com histórico ainda não havia sido gerada pela suíte JVM. Não se
  removeu essa asserção. [Evidência parcial](evidence/B_TRANSICAO_28.json).
- `c652992` corrigiu a ordem: JVM/fixture antes de PostgreSQL. Cada caso recebe a
  definição C efetiva de D; o teste legado que reaplica C continua com suas
  asserções originais, sem contaminar os casos seguintes.
- #29 / `43665975764` foi cancelada ao perceber que D já continha a guarda. Não
  contar a execução cancelada como aprovação. A candidata não será implantada.
- A nova rodada usará apenas as definições existentes, sem alteração remota.
