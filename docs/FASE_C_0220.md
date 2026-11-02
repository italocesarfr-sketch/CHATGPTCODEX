# 0.22.0 — fase C: avaliações e frequência

Base B validada `78aff91aa9d1`, consolidada em `aad093b`. Mesma branch
`sync/academic-complete-0220`; main e candidata 0.21.0 preservadas.

## Etapas e proteções

1. Ampliar o mesmo store, gatilhos e transporte para activities, grades, lessons,
   marks e recompositions. Manter Room v12, sem alteração das entidades físicas,
   do backup lógico 12 ou da criptografia. Gatilhos adicionais instalados no open.
2. Escopo C explícito por conta no servidor e estágio persistente local
   PREPARED_C/ACTIVE_C. Não enviar dados anteriormente locais só por atualizar o APK.
   Negociação valida cursor, outbox/inbox e revisões; consentimento antes da rede.
3. RPCs C reutilizam CAS, grafo, recibos e cursor do protocolo acadêmico v2. Escritores
   B são bloqueados depois da ampliação. Nada é sobrescrito ou removido na transição.
4. Recebimento inteiro e ordenação de dependências no mesmo Room. Nota zero, null,
   ausência de linha, P/F/FJ/NR e motivos continuam distintos. Validação acadêmica
   existente; nenhuma fórmula nova. Diário e movimentações permanecem fase D.
5. PostgreSQL/PostgREST descartáveis, dois AVDs, regressão completa e leitores de
   planilha. Sem aplicação hospedada, novas contas reais, release ou instalação
   no aparelho do proprietário.

## Estado

Implementação em andamento. Ainda não declarar C validada.

## Implementação e limites atuais

Desenvolvimento `0.22.0-dev-c` / código 42; nenhum APK release. Room continua v12 e
mesmo hash, com gatilhos adicionais instalados na abertura; entidades e migrações
anteriores intactas. Somente classes/terms/students são capturadas no estágio B.
PREPARED_C/ACTIVE_C ampliam para activities/grades/lessons/marks/recompositions após
consentimento. Outbox B deve estar vazia e sem conflitos antes da ampliação; a
revisão/cursor remoto são conferidos antes do servidor alterar o escopo.

O servidor passa a guardar academic_scope 2 ou 3, mantendo protocol=2. A nova RPC
`noctua_enable_academic_c` negocia sob os mesmos bloqueios; os clientes B são
bloqueados depois da conversão. RPCs `noctua_push_academic_c`, `noctua_pull_academic_c`
e `noctua_academic_status_c` usam o mesmo protocolo/CAS/recibos/versões/RLS. Não existe
fila paralela ou campo de proprietário escolhido pelo cliente. Segunda instalação
limpa reconhece o escopo C autorizado da conta e recebe os registros sem criar IDs.

Upserts aplicam pais antes dos dependentes, exclusões explícitas na ordem inversa.
O grafo completo é validado antes/depois da gravação. Cursor/recibo só avançam na
mesma transação que aplica o lote; nota inválida bloqueia também outros registros
no mesmo lote. Diário, horários e remanejamentos não são enviados. Matrículas com
cadeia de remanejamento continuam bloqueadas até a fase D. Não inventar uma
entidade de recuperação: categorias/avaliações e recomposição usam os modelos reais.
O limite inicial conservador de 256 linhas/1 MiB continua; coleções maiores exigem
staging posterior, sem envio parcial silencioso.

## Implantação futura — não executada

1. Confirmar scripts v1 e proteção de sessão já instalados; não reaplicá-los.
2. Após aprovação administrativa, aplicar A `202611010001_academic_protocol.sql`,
   B `202611020001_phase_b_fence.sql`, C `202611020002_phase_c_scope.sql`, nessa ordem,
   uma vez cada. Nunca reaplicar A/B depois de C, pois recriariam RPCs antigas.
3. Conferir existência das RPCs C, RLS de todas as tabelas, ausência de grants de
   escrita direta e privilégios privados; academic_scope deve ser 2 inicialmente.
4. Autorizar apenas UUIDs de contas de teste pelo mecanismo administrativo B.
   Instalar debug em dois emuladores limpos, sem dados reais. Ativar B, sincronizar
   pendências e aceitar ampliação C no painel existente. Confirmar indicadores reais.
5. Se ocorrer problema: pausar o teste; revogar allowed apenas da conta de teste no
   servidor, preservando perfis, versões, recibos e bancos locais. Não rebaixar escopo
   nem restaurar SQL antigo. Preservar evidências e corrigir com nova migração aditiva.
   Retomar apenas após revisão dos conflitos e nova validação descartável.

Nenhum script foi aplicado no projeto hospedado; nenhuma conta real adicional foi
criada. Atualização com certificado permanente e homologação física ainda pendentes.

## Testes até aqui

- 20 testes PostgreSQL C aprovados em `noctua_phase_c_test` descartável.
- Compilação debug/instrumentação e JVM: 247 casos, 246 aprovados, 1 condicional
  ignorado; zero falhas. O teste ignorado exige Supabase Auth real.
- Sondas HTTP locais: status C com formato correto e duas rejeições antes de
  consentimento; nove sondas B continuam aprovadas.
- Nove testes Room C e 13 fases HTTP Android em dois AVDs foram implementados;
  **ainda não executados/aprovados**. Não apresentar isso como integração concluída.
- A regressão completa B permanece obrigatória no CI, junto a leitores independentes
  dos exportadores. Verificação visual C ainda depende das novas capturas do emulador.

Se o head B mudar durante a ativação C, a tentativa fica preservada em PREPARED_C
e bloqueada por conflito, sem adotar a nova revisão silenciosamente. A reconciliação
assistida dessa transição concorrente ainda exige etapa posterior; não rebaixar o
estágio nem apagar metadados para contornar o bloqueio.

## Continuidade da validação

- Código C: `3d54d01c71f8031e47a62ea51a8b2f3cf679f56d`.
- Ajuste do serviço descartável no CI: `994afeb6e1c8d7dca8ebaa98c05a7ba475550a1a`.
- Execução em conferência: [41449090966](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41449090966).
  JVM, compilação, testes SQL e leitores independentes passaram; Android ainda em
  execução. Consultar o resultado real antes de classificar C como validada.
- A execução anterior `41448348517` parou por senha divergente do serviço PostgreSQL
  **descartável** no novo passo C. O ajuste alinhou ao serviço existente; nenhuma
  proteção de produto ou asserção foi removida.
- Ao retomar: conferir branch/HEAD/status, jobs e artefato `resultados-etapa6`,
  especialmente `phasec-focused.txt` (9 casos), `phasec-font-*.txt`,
  `phasec-network/summary.txt` (13 fases) e XML da regressão completa. Não repetir
  testes longos aprovados sem necessidade; se falhar, investigar antes de nova entrega.
- Não aplicar SQL hospedado, não instalar no celular principal, não promover main,
  não gerar release nem iniciar D. Preservar conflitos e capturas B.
