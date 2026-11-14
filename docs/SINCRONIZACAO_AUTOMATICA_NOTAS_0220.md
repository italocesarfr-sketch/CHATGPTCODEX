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

## Etapa 2

A implementar somente depois da validação e commit independente da etapa 1.
Nenhum APK ou release oficial é produzido nesta tarefa.
