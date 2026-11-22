# Fase 3.3 — consolidação automatizada da sincronização comercial

Branch dedicada: `sync/academic-commercial-final-033`, derivada de
`sync/academic-commercial-auto-031` em
`3bf93d4834e4eb51eaefc6fe0cf6b84fc27485be`. Início: 21/11/2026, America/Sao_Paulo.
Não altera produção, main, migrações, direitos comerciais hospedados, conta Aron
Danone, conta comercial de homologação ou instalações existentes.

## Fases 3.1 e 3.2 e relatos hospedados

A implementação 3.1 continua preservada: inicialização transacional/idempotente
V2/E autorizada no servidor, quinze tipos acadêmicos, pull antes de push,
BOOTSTRAP_E persistente, filas/recibos/revisões, restauração pausada, RLS e
entitlements separados da allowlist de testes. Room permanece 14, sem nova migração.
Referências: `docs/FASE_31_AUTOMATICA_0220.md`,
`docs/HOMOLOGACAO_COMERCIAL_032_0220.md` e suas evidências.

A CI 3.1 `44228928532` aprovou 20 testes PostgreSQL comerciais, 27 de regressão D,
35 verificações HTTP e 60 testes Android em dois AVDs. A preparação 3.2 #209
`44299568876` gerou o APK paralelo, sem operações hospedadas; cada configuração
JVM aprovou 317 casos, com cinco ignorados, sem falhas.

**Relato do usuário nesta execução, sem logs automatizados correspondentes:**
a migração comercial foi aplicada em noctua-homologacao, uma conta fictícia nova
foi autorizada, e inicialização sem menus A–E, recuperação A→B, B→A e persistência
offline/reconexão funcionaram em dois AVDs. O estado hospedado não é consultado
nesta fase. As pendências históricas do documento 3.2 devem ser interpretadas com
esse novo relato, não como uma inspeção técnica hospedada realizada pelo Codex.

| Cenário 1–8 | Situação hospedada |
| --- | --- |
| 1. Atividades/notas, zero, null e edição entre aparelhos | Aprovado manualmente, conforme relato do usuário |
| 2. Chamada/FJ | Aprovado manualmente, conforme relato do usuário |
| 3. Diário | Aprovado manualmente, conforme relato do usuário |
| 4. Bimestres, calendário e horários | Aprovado manualmente, conforme relato do usuário |
| 5. Recuperação/recomposição | Aprovado manualmente, conforme relato do usuário |
| 6. Remanejamentos aceitos/bloqueados e históricos | Aprovado manualmente, conforme relato do usuário |
| 7. Relatórios/exportações | Aprovado manualmente, conforme relato do usuário |
| 8. Edição/arquivamento de turmas | Aprovado manualmente, conforme relato do usuário |

Não atribuir a esses relatos uma execução automatizada ou uma auditoria completa
de Auth/RLS hospedado. Regras de remanejamento (disciplina e data dos lançamentos)
continuam inalteradas.

## Automação 9–13 preparada nesta branch

Reutiliza o workflow `commercial-auto-local.yml`, PostgreSQL 17/PostgREST local,
fixtures e os dois AVDs próprios do runner comercial. Não existe modalidade
hospedada nem acesso a Secrets/Environment. A branch comercial anterior continua
executando sua prova original; a nova branch acrescenta 29 fases após as seis
fases comerciais já existentes, sem criar outro sincronizador.

| Cenário | Prova automatizada preparada |
| --- | --- |
| 9 | Nota offline em A e diário distinto enviado por B; convergência, repetida após force-stop de A, com operação persistida |
| 10 | 4 offline versus 9 remoto; dispensa preserva variantes/cursor; escolha explícita de 4; terceiro valor invalida prévia; escolha remota, resposta de decisão perdida e replay após reinício |
| 11 | Cofre Keystore real com cliente Auth sintético; restauração de sessão após force-stop; logout com fila, interface bloqueada, novo login online, resposta de push perdida e replay sem duplicação |
| 12 | Identidades locais distintas: comercial A e B sem direito; A→B→A pela UI, bancos separados, negativa de bootstrap/pull; HTTP/RLS real nega escrita, acesso cruzado e sessões inválidas/expiradas/revogadas |
| 13 | Backup AccountBackup criptografado fictício, validação, rejeição por outra conta, fila local preservada, backup antigo restaurado em AVD descartável, RESTORE até decisão explícita KEEP_REMOTE, aluno remoto posterior e históricos preservados |

A sugestão numérica já existe no código aprovado; este trabalho não introduz
política de maior nota nem confirmação automática. Os testes escolhem explicitamente
a menor e a maior variante e preservam auditorias.

As fases do runner terminam realmente os processos antes de reiniciar a
instrumentação. Na Fase 3.3, a sessão é lida do KeystoreAuthVault, não injetada
novamente em memória em cada processo. Login/verify são sintéticos e não provam
GoTrue, refresh ou revogação de Auth hospedado. A sessão offline já validada é
restaurada pela política real; novo login offline é recusado pela fixture.

Os hashes finais canônicos devem coincidir nos dois Room. As identidades e
revisões de todos os registros são comparadas também com o PostgreSQL, não apenas
entre dispositivos. Filas/inbox devem estar vazias somente após confirmação;
recibos únicos, decisões e cursores são inspecionados. Tar de evidências contém
somente relatórios de contagens/hashes/revisões e capturas fictícias, sem tokens,
cofre de sessão ou backup criptografado. A coleta ocorre também em falha.

## Verificação local inicial e continuidade

- Compilação debug e instrumentação com opt-in comercial: aprovada localmente.
- JVM: 322 casos, 317 aprovados, cinco ignorados (H01–H04 hospedados e sonda Auth
  legada), zero falhas/erros. Ignorados não são aprovação.
- PostgreSQL 17.11 novo em loopback: 20 testes comerciais aprovados.
- PostgREST 12.2.3 local: **43 verificações aprovadas**, 171 registros/quinze tipos,
  incluindo oito negativas adicionais e imutabilidade dos dados durante tentativas.
- A primeira chamada local ocorreu antes do cache de schema ficar pronto e recebeu
  503. Após observar GET / pronto, a suíte passou. O runner CI já aguarda prontidão;
  não foi alterado para ignorar 503 nem repetir mutações sem controle.
- Um erro de compilação no teste (RoomDatabase não implementa use/Closeable) foi
  corrigido usando try/finally; não foi defeito do aplicativo.

**Prova Android 3.3 ainda pendente no checkpoint inicial. Não declarar 9–13
aprovados antes de conferir o artefato e os códigos nativos da CI.**

Comandos: `python3 scripts/test-commercial-bootstrap.py` e
`python3 scripts/test-commercial-http.py` exigem fixture local explicitamente
selecionada; Gradle com `-PnoctuaAcademicCommercial=true` compila/testa o opt-in.
O push da branch dedicada aciona a CI local, sem instalar em aparelhos existentes.

Pendências externas: repetir o conjunto necessário em Auth real/infraestrutura
hospedada somente após autorização independente, capacidade e orçamento de oito
segundos sob carga institucional, compatibilidade assinada/física da distribuição
oficial e política de provisionamento comercial definitivo. Não aplicar H13/PT409
ou ampliar timeouts para transformar limitações em aprovação.

## Diagnóstico da primeira execução Android

Run `44967475626` (commit `8c617d97b3ce1ac8eb6c1efa680130340144d504`):
PostgreSQL/HTTP e builds aprovados. Seis fases comerciais originais e as nove fases
do cenário 9 passaram. O cenário 10 chegou ao diálogo com local 4/remoto 9,
dispensou sem mutação e reabriu corretamente, mas o helper novo falhou ao tentar
`performScrollTo()` em um botão fixo do rodapé, sem ancestral rolável.
A captura real confirma ambos os botões inteiramente visíveis. Não foi timeout,
resolução automática ou perda de variante. Corrigido apenas o gesto instrumental:
`assertIsDisplayed().assertIsEnabled().performClick()`, conforme helper anterior.
O botão de confirmação genérico também é fixo; a mesma correção se aplica a ele.
As opções dentro do conteúdo rolável mantêm performScrollTo. Nenhuma asserção
acadêmica/segurança foi removida. Artefato `14731669349` preserva a falha e a imagem.

A coleta do primeiro artefato também mostrou um checkpoint já convergido que
entrou legitimamente em SYNCING enquanto as consultas separadas do diagnóstico
eram lidas (polling periódico). A coleta agora usa o syncMutex existente para
registrar snapshot, cursor, filas e revisões de um mesmo intervalo consistente.
Não suprime SYNCING, não força status de sucesso e não aciona a engine manualmente.
As asserções finais de fila vazia, mensagem vazia e revisões confirmadas permanecem.

## Resultado final conferido — aprovação técnica local

**Fase 3.3 aprovada no ambiente automatizado descartável**, código
`82fb87edcf8f3f0a52299a7c2016275154b18384`.
[Execução #4 / 44969167679](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44969167679),
job `137044918348`, **SUCCESS**. O artefato foi efetivamente baixado, seus XMLs,
códigos nativos, relatórios e capturas foram inspecionados.

[Artefato commercial-auto-local-evidence](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44969167679/artifacts/14733142927),
ID `14733142927`, 293.947 bytes, retenção de 14 dias. ZIP SHA-256:
`57efe873a4380f4925841b7b99f119fa50be518eeecfbadc4bac35698bb8852a`.
[Evidência consolidada preservada no repositório](evidence/FASE_33_CI_0220.json).

A execução intermediária
[44968412058](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44968412058),
código `7517004da3011d097d90781a22830fa6ffc66d9e`, também completou integralmente
os cenários e gerou o mesmo hash acadêmico final. Não somar as repetições como
cobertura única. A falha inicial `44967475626` permanece registrada acima, sem
atribuir aprovação aos passos que ela não alcançou.

| Verificação final | Aprovações | Ignorados | Falhas/erros |
| --- | ---: | ---: | ---: |
| JVM padrão, flag comercial desligado | 317 de 322 | 5 | 0 |
| JVM com opt-in comercial | 317 de 322 | 5 | 0 |
| PostgreSQL comercial | 20 | 0 | 0 |
| PostgreSQL regressão D original após A–E/comercial | 27 | 0 | 0 |
| HTTP real local | 43 verificações | 0 | 0 |
| Android rede/startup comercial original | 6 fases | 0 | 0 |
| Android cenários 9–13 adicionais | 29 fases | 0 | 0 |
| Android Room/UI/Auth e migração | 72 casos | 0 | 0 |
| Android fonte nativa 1,6 | 2 casos | 0 | 0 |

**109 execuções de casos instrumentados aprovadas** na execução final: 35 fases
rede/lifecycle, 72 casos Room/UI/Auth e duas execuções de UI com fonte ampliada.
Os dois conjuntos JVM repetem os mesmos casos sob flags diferentes; não representam
634 testes únicos. Os cinco ignorados em cada conjunto são H01–H04 hospedados e
uma sonda Auth legada, desabilitados por falta de opt-in/credenciais nessa CI local.
Nenhum cenário 9–13 nem teste Android foi considerado aprovado por ter sido ignorado.
A regressão reutiliza as classes B/C/D, inicialização comercial, recuperação automática,
reconciliação E e isolamento Auth; não afirma reexecução de toda suíte Android histórica.

| Cenário | Resultado individual e evidência |
| --- | --- |
| 9 — alterações offline simultâneas | **APROVADO LOCALMENTE.** Nove fases: nota em A e diário distinto em B convergem; repetição com encerramento/reabertura offline restaura sessão do Keystore e a mesma operação pendente. Sem conflitos desnecessários, sem duplicação. |
| 10 — conflito da mesma nota | **APROVADO LOCALMENTE.** Nove fases: local 4/remoto 9 preservados; dispensa mantém cursor/fila; decisão explícita mantém 4. Terceira revisão invalida prévia, botões ficam bloqueados; nova prévia e decisão remota são auditadas. Resposta da decisão perdida, reinício e replay concluem o mesmo ID. |
| 11 — reinício/logout/recuperação | **APROVADO LOCALMENTE.** Cinco fases adicionais, além da inicialização com resposta perdida já reutilizada: logout bloqueia a UI e não apaga fila; processo novo mantém logout e a operação no disco; login online, resposta de push perdida e novo processo confirmam a mesma operação sem outra revisão. |
| 12 — isolamento | **APROVADO LOCALMENTE.** A→B→A pela interface; B não autorizado permanece sem protocolo E e sem registros remotos. Dados locais de B ficam em seu banco; A recupera hash/cursor originais. As 43 verificações HTTP e SQL complementam RLS/ACL, negação de escrita direta/helper e sessões inválidas/expiradas/revogadas. |
| 13 — backup/restauração | **APROVADO LOCALMENTE.** Cinco fases: criptografia, abertura/integridade e outra conta rejeitada; backup antigo restaurado com outbox preservada em RESTORE. Só KEEP_REMOTE explícito retoma; aluno remoto posterior sobrevive. Corpo da fila anterior permanece no audit de decisão; registros/históricos e IDs convergem. |

A bateria HTTP preserva também a prova anterior com dois proprietários autorizados,
em outra dupla de fixtures. **B do cenário Android comercial permanece sem concessão
durante todo o ensaio**, confirmado por zero protocolos e zero registros remotos.
Não foi necessário autorizar B para fazer a negativa passar.

## Estado acadêmico final e continuidade

Hash SHA-256 do **snapshot acadêmico canônico**, igual nos dois Room e nas duas
execuções completas independentes:

```text
dbee2693e1fca7b1688f19d850fc5b9dc1efa6a4732d6475ea585abf593b58cd
```

É hash lógico acadêmico, não hash byte a byte do arquivo SQLite/WAL ou do backup
criptografado, cujos metadados/counters/nonces são diferentes entre instalações.
A comparação final verifica todos os campos dos quinze tipos contra a fixture
original mais as alterações autorizadas pelo teste, não apenas igualdade A/B.
IDs e revisões dos 172 registros também coincidem exatamente com o PostgreSQL.

- Ambos: `ACTIVE_E`, cursor **13**, mensagem vazia, outbox/inbox vazias, todos os
  counters locais reconhecidos. Pós-reinício, nenhuma inscrição manual A–E.
- Servidor: head **13**, **13 recibos distintos**, **três decisões auditadas**;
  nenhuma duplicação após perda de respostas.
- Nota-alvo: revisão **10**, histórico completo
  `null → 0 → 1,5 → 2,5 → 9 → 4 → 8 → 9,5 → 7,5 → 8,5`.
  Null e zero seguem distintos; escolhas e variantes anteriores não desapareceram.
- 171 registros originais e um aluno posterior ao backup: **172 registros finais**.

| Tipo | Quantidade final |
| --- | ---: |
| classes | 2 |
| terms | 8 |
| students | 38 |
| activities | 2 |
| grades | 36 |
| lessons | 3 |
| marks | 71 |
| recompositions | 1 |
| lesson_records | 2 |
| relocations | 1 |
| weekly_timetables | 2 |
| class_archives | 1 |
| preserved_histories | 1 |
| export_rows | 3 |
| import_drafts | 1 |

Roundtrips locais observados no Android, incluindo transporte/processamento do
cliente (não EXPLAIN ou tempo SQL isolado): pushE mediana **181,103 ms**, máximo
**379,362 ms**; pullE mediana **6,663 ms**, máximo **171,606 ms**; resolveE mediana
**10,776 ms**, máximo **128,976 ms**. Não extrapolar esse acervo pequeno para volume
institucional nem para o orçamento efetivo do Supabase hospedado.

Capturas reais inspecionadas, dados fictícios, tema escuro:
[escolha 4/9](evidence/FASE_33_CONFLITO_4_9.png),
[decisão obsoleta bloqueada](evidence/FASE_33_DECISAO_OBSOLETA.png),
[restauração aguardando decisão](evidence/FASE_33_RESTAURACAO.png),
[sincronização comercial com fonte 1,6](evidence/FASE_33_FONTE_16.png).
O teste de fonte ampliada cobre o painel comercial existente; as capturas dos
conflitos/restauração acima usam fonte padrão. Não atribuir a elas validação de
fonte ampliada que não ocorreu.

## Commits, arquivos e comandos

- `8c617d97b3ce1ac8eb6c1efa680130340144d504`: 29 fases novas no runner comercial,
  cliente Auth sintético com cofre persistente, negativos HTTP e conferência final.
- `7517004da3011d097d90781a22830fa6ffc66d9e`: correção do gesto em ações fixas e
  comparação integral de snapshot/histórico de notas; CI intermediária aprovada.
- `82fb87edcf8f3f0a52299a7c2016275154b18384`: coleta consistente sob o mutex já
  existente; **código final validado**. Commits posteriores desta rodada são documentais.

Arquivos de automação: `.github/workflows/commercial-auto-local.yml`,
`app/src/androidTest/java/br/com/diariodeclasse/{AuthTestRunner,PhaseBNetworkHarness,
CommercialFinalAuthClient,CommercialFinalNetworkProbeTest}.kt`,
`scripts/ci/{test-commercial-network,test-commercial-final-phases}.sh`,
`scripts/{test-commercial-http,test-commercial-final-evidence}.py`.
Documentação: este arquivo, `docs/evidence/FASE_33_CI_0220.json` e quatro PNGs.

Executados localmente: Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest`
com `-PnoctuaAcademicCommercial=true`; `test-commercial-bootstrap.py`,
`test-commercial-regression.py`, `test-commercial-http.py` sob fixtures locais,
`test-commercial-homologation-preparation.py` (oito estáticos), `py_compile`,
`bash -n`, YAML/guardas e `git diff --check`. Os ensaios PostgreSQL do host usaram
cluster novo em `build/phase33-pg`, parado depois das verificações; não se apagaram
bancos, filas ou backups anteriores. CI repete SQL/HTTP e executa os AVDs.

Na CI: o push da branch dedicada aciona o workflow existente ampliado. São usados
PostgreSQL 17, PostgREST 12.2.3 e dois AVDs com nomes ligados ao run ID/attempt,
sem opção hospedada. Guardas recusam outro banco/porta, um serial não emulador,
AVD primário que não pertença ao run ou serial secundário ocupado. Cleanup só
encerra a instância secundária criada por esse job. Não há Secrets de contas,
Environment release, assinatura oficial ou instaladores para dispositivos do usuário.

## Limitações e recomendação de implantação controlada

1. **Auth sintético**: UI, AuthController e Keystore reais foram exercitados, mas
   login/verify/refresh usam fixture. GoTrue, rotação/revogação de sessões e erros
   específicos da API hospedada precisam de prova autorizada própria. A negativa
   de JWT/sessão SQL foi real em PostgREST local, não auditoria hospedada.
2. Os cenários hospedados relatados pelo usuário permanecem **manuais**. Não se
   executou SQL ou requisição Supabase nesta rodada; o estado atual do catálogo,
   permissões e limites hospedados não foi reinspecionado.
3. Nenhum ensaio com 5.171 registros ou capacidade institucional ampla foi feito.
   Os limites/relatórios H13 anteriores continuam válidos; candidatas H13/PT409
   não foram aplicadas. Timeout, memória e quotas devem orientar implantação gradual.
4. A assinatura permanente, upgrade oficial e hardware físico não foram usados.
   Contas legadas/v1 e dados do proprietário permanecem fora da admissão automática.
   Não reclassificar filas/conflitos antigos como contas comerciais novas.

**Recomendação:** a consolidação local permite avançar para uma homologação
comercial hospedada/controlada de contas novas elegíveis, com backup verificável,
limites e monitoramento. A liberação geral e a migração da conta principal continuam
condicionadas a autorização, homologação Auth/hospedada pertinente, capacidade e
atualização assinada/física. Não há necessidade de repetir manualmente os cinco
ensaios locais nos AVDs atuais do proprietário.

Próxima ação: preparar, sob autorização independente, o conjunto hospedado mínimo
que cubra os efeitos externos ainda não comprovados. As futuras escritas e decisões
precisam de limites explícitos, contas/dados fictícios isolados e credenciais somente
em Secrets protegidos. Não executar scripts de fixtures, reset de banco ou os
runners locais contra Supabase. Nenhuma concessão, rollout, migração ou instalação
é autorizada por esta aprovação local. Reversão desta rodada consiste em manter
as branches preservadas; não há alteração de banco/app de produção a desfazer.

Estado final: branch dedicada com automação e documentação; aplicação, Gradle,
Room 14, SQL A–E/comercial, backups, RLS e assinatura oficial sem alterações.
Migração comercial continua com SHA-256
`1d5c48c0b55a24edbae948407a8775030490338c8a3b0c6a64b2a98e5bf051e2`.
