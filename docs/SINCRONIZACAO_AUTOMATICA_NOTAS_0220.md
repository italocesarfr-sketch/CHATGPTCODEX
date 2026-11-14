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
