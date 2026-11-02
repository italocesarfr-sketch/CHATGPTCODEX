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

Implementação e integração C passaram no ambiente automatizado local em
`41450420056` / `9eb5c8ae85275523a493ba8985018e5135a6c189`. A revisão das capturas
identificou um estado visual de conflito corrigido depois; sua nova validação (incluindo normalização numérica) está
em andamento. Não há homologação hospedada/física nem release estável.

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

## Conferência administrativa somente de leitura

Após uma futura implantação aprovada, executar
[`supabase/checks/phase_c_readonly.sql`](../supabase/checks/phase_c_readonly.sql).
Ele abre transação somente de leitura e lista RLS, políticas, grants e RPCs, sem
conteúdo acadêmico, UUIDs de contas ou segredos. Esperado: todas as tabelas protegidas,
nenhum INSERT/UPDATE/DELETE direto para authenticated, RPCs públicas C executáveis
somente pelo cliente autenticado, aliases privados não executáveis por ele, nenhum
EXECUTE anônimo e search_path fixo. academic_scope deve começar em 2; só o consentimento
C o eleva a 3. O script foi executado apenas no PostgreSQL C descartável.

Esse inventário administrativo **não comprova isolamento hospedado**. Ainda será
necessário repetir com duas sessões Auth comuns autorizadas, incluindo SELECT direto
na API e chamadas RPC. Não solicitar tokens, senhas nem chaves administrativas no chat.

## Evidência C aprovada e correção identificada na revisão visual

Workflow [41450420056](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41450420056)
aprovado, SHA `9eb5c8ae85275523a493ba8985018e5135a6c189`.
Artefato [13145816453](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41450420056/artifacts/13145816453),
SHA-256 ZIP `41d2ad6a84705c8a60350a98fa09ee08b7967a98ed676f0f14e487b3e0b06d81`.

- JVM XML: 247 casos, 246 aprovados, 1 condicional ignorado, zero falhas.
- Android XML: 382 casos, 373 aprovados, 9 condicionais ignorados, zero falhas.
- Room C focado: 9/9; fontes reais 1,3 e 1,6: aprovadas.
- PostgreSQL C: 20/20; SQL A/B e regressão v1 também aprovados.
- Rede Android B: 10/10 fases; C: 13/13 fases em dois AVDs independentes.
- C enviou/recuperou 153 registros das oito entidades: turma, quatro bimestres,
  36 matrículas, duas aulas, duas atividades, 36 notas (incluindo null/zero),
  71 frequências (P/F/FJ/NR, com uma ocorrência ausente) e uma recomposição zero.
- Reconexão no mesmo processo, resposta perdida/reenvio, edição inversa, edições
  independentes, conflito de nota e diário local preservado foram comprovados.
- Leitores independentes XLSX/CSV/TSV, backup/restauração, atualização da referência
  com chave descartável e regressão anterior passaram. Certificado permanente não
  foi usado para atualização: essa prova continua pendente.

Os nove condicionais Android incluem o novo PhaseCNetworkProbe, executado nas 13
invocações próprias, e os oito probes listados na auditoria B. Não somar novamente
os testes focados à quantidade de casos únicos da regressão.

A captura de conflito revelou um defeito alcançável no engine compartilhado: depois
de preservar a inbox, uma nova chamada definia SYNCING e saía no bloqueio sem repor
CONFLICT. Os dados/cursor estavam protegidos, mas o spinner podia ficar ativo e o
botão aparentar ocupado. A correção `36f86f16b8af9c65243182e20ef3a53eda29d14c`
reconhece o bloqueio antes de anunciar sincronização e mantém CONFLICT em tentativas
repetidas. Regressões Room B/C conferem duas tentativas sem alterar variantes/cursor;
a prova HTTP C confere também o estado e botão no painel real.

Nova validação: [41454219396](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41454219396),
SHA `36f86f16b8af9c65243182e20ef3a53eda29d14c`, ainda em execução.
As imagens anteriores comprovam os dados, mas a captura com spinner não deve ser
usada como resultado visual final; conferir as novas capturas antes de encerrar.

## Último estado para retomada

Código mais recente: `f76a18e265ad79ffcef4e2319eb2ea671a004412`.
CI atual: [41456687871](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/41456687871).
Substitui a validação `41454219396`, cancelada automaticamente pelo novo código.
A execução aprovada anterior `41450420056` continua evidência da integração das oito
entidades, mas ainda não cobre as duas correções de auditoria posteriores.

Além do spinner, uma sonda HTTP real local reproduziu outro defeito: PostgreSQL
JSONB devolve `1.0E-4` como `0.00010`. A comparação JSON literal podia bloquear uma
operação semanticamente idêntica. `PhaseBGuard.normalized` primeiro exige todos os
campos e seus tipos; depois usa os próprios serializers Room para comparar o payload.
Não gera identidades, reescreve textos nem muda cálculos. O lote bruto continua
intacto na inbox e no digest/recibo; o algoritmo de hash histórico não foi alterado.

Um novo teste JVM preserva números/texto/identidade; um teste Room verifica resposta
perdida, recepção repetida com digest bruto e recuperação em banco independente.
A nota 0,0001 também passou a integrar os 153 registros da prova HTTP entre AVDs.
Teste JVM focado (3 casos) e compilação da instrumentação passaram localmente.
A suíte Room C agora tem **10 casos**; verificar `phasec-focused.txt` e as 13 fases
HTTP no novo CI. Não usar as contagens esperadas como aprovação antes dos XMLs.
