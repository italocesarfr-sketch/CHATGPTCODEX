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
