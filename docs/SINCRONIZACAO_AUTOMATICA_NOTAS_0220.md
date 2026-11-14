# Sincronização automática e conflitos de notas — 0.22.0 experimental

## Etapa 1 — implementação

Base inspecionada: `b39b66aae55fe271200c4e785bb2e6ad09838d05`, branch
`sync/academic-complete-0220`. Sem alteração de main, endpoint, certificado,
Room 14, backup lógico 12, SQL, RLS ou contrato A–E.

- Uma instalação de conta autenticada pode adotar automaticamente o **escopo E
  já autorizado no servidor**. A recuperação verifica a sessão e `testAllowed`,
  exige protocolo 2/escopo 5, e reutiliza captura, paginação e aplicação transacional.
  Não chama RPC de ativação ou ampliação de escopo.
- Nenhum controle local existente é substituído: pausa, restauração, transição,
  decisões, inbox/outbox órfãs e experimento v1 impedem essa adoção.
  O conflito legado não é resolvido nem removido.
- Dados locais já pertencentes ao workspace autenticado são semeados com seus
  IDs. A ausência de registros locais continua sem representar exclusão remota.
  Recebimento precede envio e colisões são preservadas pelo protocolo existente.
- Gravações locais, conectividade, primeiro plano e JobScheduler convergem no
  mutex por workspace e fila durável existentes. Reintentos transitórios usam
  sinal coalescido e backoff limitado; sucesso cancela o timer antigo.
- Logout aguarda a barreira de gravações Room e verifica novamente falhas de
  salvamento. Não exige conexão nem esvazia a fila de envio.
- Estado discreto abaixo do cabeçalho utiliza os mesmos dados Room do painel;
  escopos B/C/D são identificados como parciais, sem anunciar cobertura integral.

### Limite de ativação preservado

Não é liberação geral de sincronização em produção. `academicTestEligible`
continua exigindo DEBUG, workspace não legado e identidade não pertencente ao
proprietário legado; o servidor exige conta de teste autorizada. Contas novas
sem autorização E e instalações com v1/dados legados continuam exigindo o
procedimento inicial de autorização/migração já existente. Restauração continua
pausada até reconciliação explícita H12. Não há inscrição automática de dados
reais, ampliação silenciosa B→E nem remoção de barreiras para cumprir a rotina.
Liberar contas gerais/banco legado demanda avaliação de compatibilidade e
homologação específica; não foi feita alteração remota nesta tarefa.

### Validação e continuidade

- `testDebugUnitTest`: 304 aprovados, 5 condicionais hospedados ignorados,
  nenhuma falha (não contabilizados como aprovação hospedada).
- `scripts/test-phase-e-decisions.py`: 15 aprovados em PostgreSQL 17 local
  descartável, identidades sintéticas. Preserva auditorias, revisão e replay.
- Novos testes Room: adoção E sem ativação remota; recusa de conta/escopo;
  preservação de pausa/restauração/transição; barreira de v1/fila órfã;
  dados offline com IDs e operação persistentes.
- Prova HTTP/Android preparada: duas instalações CI independentes, 15 tipos,
  recuperação sem ativação/botão, edição inversa, logout offline, reinício,
  resposta perdida e replay. Nenhuma chamada a Supabase hospedado.
- Primeira CI `43623031779` / `a4e01aa`: 304 JVM aprovados, cinco
  condicionais ignorados; 64 Android funcionais e três compactos aprovados;
  nove verificações HTTP de segurança aprovadas. Dez fases HTTP funcionais e
  sete novas fases automáticas passaram. A oitava falhou ao comparar uma
  fixture não ordenada com snapshot canônico. Correção do oráculo: canonicalizar
  ambos os lados, preservando comparação exata (incluindo motivos e IDs).
  A execução geral falhou e não representa aprovação integral.
- Segunda CI `43623719336` / `3461033`: compilação, nove verificações HTTP,
  64 Android funcionais e três compactos aprovados. A infraestrutura do segundo
  AVD retornou `settings: Broken pipe` antes de qualquer fase de rede. A prontidão
  agora exige boot, Settings e Package observados, em polling limitado. Não há
  retry automático de testes falhos nem redução de asserções. Reexecutar.

A injeção `automaticAcademicRecovery()` da Application permite manter os antigos
ensaios de consentimento/transferência controlados. Só a Application de testes
seleciona esse controle; a Application real usa o caminho automático. Os novos
ensaios ativam o caminho real, sem chamar `run`/`request` depois de editar ou
reconectar.

### Aprovação local da etapa 1

CI [43624510478](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43624510478),
código `a4e01aa03da81c603f7c5d14c1d0858cd86c6c4a`, testes consolidados em
`c4387b66721797c70b9e511875a1736cc3ebf75b`: **success**.
304 JVM aprovados, cinco condicionais hospedados ignorados; 64 Android
funcionais + três compactos aprovados, nenhum ignorado/falho nessas seleções;
18 fases HTTP/Android (dez anteriores + oito automáticas) aprovadas em dois
AVDs independentes; nove verificações HTTP de segurança aprovadas.
Recuperação dos 15 tipos com 36 matrículas na origem, edição inversa, logout
com gravação offline, reinício, perda de resposta e replay foram executados.
PostgreSQL local adicional: 27 regras D + 15 decisões E aprovadas.
Não é execução da suíte Android integral nem homologação hospedada nova.

Commits da etapa: `a4e01aa` (produto e provas), `3461033` (oráculo canônico),
`c4387b6` (prontidão dos serviços do segundo AVD).

## Etapa 2

Implementação após a aprovação local da etapa 1, reutilizando
`AcademicReconciliation`/decisões E; nenhuma mudança remota de contrato, esquema,
RLS, máximos ou cálculo acadêmico.

- Popup automático de **um conflito isolado de nota numérica válida**, com nome,
  turma, atividade, valores local/remoto e duas decisões explícitas. Maior nota
  é só sugestão; não há opção pré-confirmada nem política automática de máximo.
- Ambos os candidatos passam pelos validadores existentes e pelas proteções de
  histórico. Alterações de matrícula/atividade/máximo, múltiplos conflitos,
  conjuntos dependentes, restauração, nota nula, exclusão, valor inválido ou
  igualdade não usam essa sugestão. A conferência existente permanece disponível.
  Zero continua sendo nota; ausência e `null` não são zero.
- Fechar só guarda a dispensa na interface por conjunto; não modifica a fila,
  cursor, notas ou variantes. A conferência pode ser reaberta pelo estado de
  sincronização. Reinício recupera os conflitos/decisões no Room.
- Confirmação de nota reconsulta contexto/cabeça remota antes de criar a decisão
  durável. A RPC continua revalidando atomicamente a revisão depois disso.
  Se houver terceira edição, a prévia é bloqueada e exige atualização explícita.
- Auditoria existente conserva valores originais, base, variantes, escolha,
  requisição congelada, filas anteriores e recibo. Resposta perdida reutiliza
  o identificador persistido. Frequência, FJ, diário, restauração e conjuntos
  consistentes continuam com a política de resolução anterior.
- Exibição decimal preserva valores distinguíveis, sem arredondar duas variantes
  diferentes para o mesmo rótulo.

Validações em curso: nove testes JVM novos; dois testes Room de auditoria e
revalidação, quatro testes UI normais/compactos; nove fases HTTP/Android novas
sobre o mesmo par de AVDs (27 no total), incluindo dispensa, maior/menor nota,
edição posterior normal, terceira revisão, resposta de decisão perdida e reinício.
18 testes PostgreSQL de decisões passaram localmente (15 anteriores + três novos).
O teste inicial de fixture com valores inválidos foi corrigido no oráculo:
o backup já rejeitava a fixture antes do teste; agora o helper é exercitado com
payload inválido explícito. Não houve flexibilização do validador.
Resultados Android/CI da etapa 2 ainda pendentes; compilação não é aprovação.
Nenhum APK ou release oficial é produzido nesta tarefa.


## Limitações e próxima homologação

- Nada foi executado contra Supabase hospedado nesta tarefa. As aprovações
  hospedadas H01–H05/H12 anteriores não validam automaticamente este novo código.
- A nova prova utiliza 171 registros sintéticos dos 15 tipos e 36 matrículas na
  origem. Não amplia a capacidade institucional nem os limites H13/8 segundos.
- A atualização de assinatura permanente/0.21.0 e a instalação em dispositivos
  do proprietário não foram realizadas. Não instalar o debug de testes sobre
  aplicações existentes. Não há APK oficial nem mudança de versão/assinatura.
- Campos nulos/exclusões e múltiplas divergências mantêm revisão de conjunto,
  sem sugerir uma nota máxima. Uma terceira edição após o preflight ainda é
  rejeitada atomicamente pela RPC, mantendo decisão/variantes para nova análise.
- Falta futura homologação Android hospedada autorizada com variante paralela
  e certificado compatível ou instalação nova isolada. Três testes prioritários:
  1. Login em instalação nova da conta fictícia já autorizada em E; conferir
     recuperação sem botão/backup e ausência de duplicações.
  2. Alteração offline, saída/reabertura e login válido; reconexão e conferência
     da alteração no outro dispositivo, sem reiniciar para reenviar.
  3. Conflito de nota: dispensar, reabrir, escolher maior e menor; provocar nova
     revisão antes de confirmar e conferir bloqueio/auditoria.

Os comandos locais usados foram `testDebugUnitTest assembleDebug
assembleDebugAndroidTest`, `scripts/test-phase-d-postgres.py` (27),
`scripts/test-phase-e-decisions.py` (18 após os casos novos), `bash -n`,
`git diff --check` e detector Impeccable (sem achados; não substitui renderização).
A execução CI usa o despacho FUNCTIONAL existente e PostgreSQL/PostgREST/AVDs
descartáveis, sem Environment Secrets hospedados ou candidatos H13 rejeitados.


### Primeira CI da etapa 2

`43626412238` / `a841e59`: 313 JVM aprovados e cinco condicionais ignorados;
18 PostgreSQL de decisões e nove HTTP de segurança aprovados; **27 fases reais
HTTP/Android aprovadas** em dois AVDs, incluindo ambas as escolhas, dispensa,
terceira revisão, resposta perdida e reaplicação após reinício. A execução geral
falhou na seleção Android: 95 aprovados / um reprovado, de 96 executados.
O teste antigo de exportação anual tentou abrir “Salvar arquivo” sem a folha
estar na árvore Compose após retornar do seletor de documentos. A automação
passa a aguardar janela ativa do app, controle habilitado e abertura efetiva da
folha/opção. Não há alteração no exportador, fórmulas ou asserção de integridade.
A variante compacta dessa rodada não executou, pois a seleção anterior falhou.
A revisão também acrescentou o bloqueio do popup automático durante pausa
explícita e sua prova de pausa/retomada, mantendo ambas as variantes/cursor.
Reexecutar antes de declarar aprovação da etapa 2.


## Resultado final local — etapa 2

[CI 43627336525](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43627336525)
**aprovada**, SHA `b8206c39188cebf2bf0baa28c4c61164e08e85c5`.
Commits da etapa: `a841e59` (popup e provas), `b8206c3` (pausa explícita e espera
por controles acionáveis na regressão de exportação). A documentação posterior
não altera o código validado.

| Verificação | Resultado efetivamente executado |
| --- | --- |
| JVM completa | 313 aprovados, 5 condicionais hospedados ignorados, 0 falhas |
| Android selecionada | 96 aprovados, 0 ignorados, 0 falhas |
| Compacto 360×640 dp / fonte nativa 150% | 8 aprovados, 0 ignorados, 0 falhas |
| HTTP/Android em duas instalações independentes | 27 fases aprovadas |
| PostgreSQL de decisões | 18 aprovados |
| HTTP de segurança/isolamento | 9 verificações aprovadas |
| Regras D em PostgreSQL local nesta tarefa | 27 aprovadas; nenhuma alteração SQL |

Os oito testes compactos são repetições de cenários de UI; as 27 fases são
invocações de integração, não 27 novos métodos de teste. A seleção Android
inclui isolamento de contas, migrações 12→13→14, backup criptografado com conta
incorreta, restauração pausada em banco independente, auditoria/reconciliação,
partes persistentes e regressões de chamada, FJ, atividades, notas e exportações.
Não foi executada novamente toda a suíte Android histórica.

Provas entre AVDs: 171 registros / 15 tipos, 37 matrículas vivas (36 na origem),
recuperação automática sem ativação local no receptor, IDs preservados, mudança
inversa, logout offline aguardando gravação, reinício, retomada e recibos sem
nova operação. Nota concorrente 6/8: dispensa sem decisão e escolha explícita
de 8. Segundo conflito 6/9: terceira revisão 9,5 injetada por HTTP com a sessão
sintética do segundo dispositivo, confirmação antiga bloqueada, prévia renovada,
escolha explícita de 6, resposta perdida, force-stop e reaplicação da mesma
decisão. A edição normal 8→8,5 sobre a revisão atual convergiu sem conflito.
A pausa ocultou o popup; a retomada reapresentou as mesmas variantes sem avançar
o cursor. As auditorias originais e os
históricos de frequência/remanejamento continuaram preservados.

Artefato [functional-improvements-evidence](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43627336525/artifacts/14126539209):
SHA-256 `9cf39f6788fe2373b0381660e581dcf349b47e7cc7be32d48dbe16e6bf769793`.
Os XMLs, saídas de instrumentação, resumo das 27 fases e screenshots foram
baixados e conferidos. [Manifesto](evidence/AUTOMATICA_NOTAS_0220.json) registra
os hashes e resultados, inclusive rodadas anteriores reprovadas.

Capturas reais inspecionadas do SHA aprovado, sem alteração da imagem:

- [Popup no aplicativo](evidence/AUTOMATICA_NOTAS_20261114/popup-nota.png).
- [Terceira revisão bloqueada](evidence/AUTOMATICA_NOTAS_20261114/previa-desatualizada.png).
- [Tela compacta / fonte 150%](evidence/AUTOMATICA_NOTAS_20261114/popup-fonte-150.png).
- [Prévia desatualizada / fonte 150%](evidence/AUTOMATICA_NOTAS_20261114/bloqueio-fonte-150.png).

Os botões continuam acessíveis; o corpo do aviso pode rolar na fonte ampliada.
As capturas compactas usam fixture de UI; as duas primeiras vêm da integração
no aplicativo com backend local real. São dados fictícios, não dados hospedados.

**Consolidação técnica local aprovada.** A elegibilidade experimental permanece;
nenhuma aprovação de produção, conta geral ou migração do conflito legado está
implícita. Permanecem pendentes a homologação hospedada/Android do novo fluxo,
a avaliação da ativação geral e a atualização assinada em ambiente autorizado.
Main, projetos Supabase e aparelhos existentes não foram alterados.

### Arquivos modificados nesta tarefa

- `.github/workflows/functional-improvements.yml`
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicAutomaticNetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicAutomaticRoomTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/AcademicGradeConflictUiTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/AuthTestRunner.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseBNetworkHarness.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseENetworkProbeTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PhaseEReconciliationTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PromptTwoUiInstrumentedTest.kt`
- `app/src/main/java/br/com/diariodeclasse/DiaryApplication.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/SyncJobs.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/SyncPanel.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicAutomaticStatus.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicEngine.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicGradeConflict.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicGradeDialog.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicReconciliation.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicReviewDialog.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt`
- `app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicStore.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DiaryApp.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DiaryViewModel.kt`
- `app/src/test/java/br/com/diariodeclasse/AcademicNumericGradeTest.kt`
- `docs/SINCRONIZACAO_AUTOMATICA_NOTAS_0220.md`
- `scripts/ci/test-functional-android.sh`
- `scripts/ci/test-functional-network.sh`
- `scripts/test-phase-e-decisions.py`

Além desses arquivos, este relatório inclui o manifesto e as quatro capturas acima.
