# Fase 3.1 — inicialização comercial automática do protocolo E

Branch: `sync/academic-commercial-auto-031`.
Base preservada: `temp/academic-production-phase1`,
`9ede184a035dceb8e240e6a56dcb4c649aa68149`.
Data de início: 17/11/2026, America/Sao_Paulo.

**Desenvolvimento experimental: não implantado em Supabase, sem APK oficial,
assinatura permanente, migração da conta proprietária ou alteração de Aron Danone.**

## Implementado

1. `-PnoctuaAcademicCommercial=true` gera `BuildConfig.ACADEMIC_COMMERCIAL=true`.
   O padrão é **false**, inclusive em debug. Não altera `ACADEMIC_PRODUCTION`,
   workflows de assinatura ou o APK oficial instalado.
2. Uma conta autenticada, não legada e sem metadados prévios recebe localmente
   `BOOTSTRAP_E`, desabilitado para envio. Os triggers Room existentes capturam
   as quinze entidades também nesse estágio. A criação do controle, seed dos dados
   offline e alterações posteriores/counters são transacionais. Nenhuma fila velha
   é descartada ou adotada.
3. Startup, primeiro plano, alteração local, reconexão e JobScheduler convergem no
   mutex da conta. Com conexão/sessão válidas, `noctua_ensure_academic_e()` verifica
   a autorização comercial **no servidor**, sem owner escolhido pelo cliente.
4. Inicialização remota cria protocolo V2/E, head acadêmico e marcador temporal
   conjuntamente. Duas sessões ou resposta perdida repetem o mesmo resultado;
   nenhuma fase intermediária é exposta. Não chama os consentimentos de testes.
5. Após confirmar a resposta, o Room revalida o estado local e ativa E. A engine
   existente recebe todas as páginas antes de preparar envios. Uma instalação
   vazia não produz exclusões por ausência; dados locais novos/offline continuam
   dirty até confirmação. Divergências conservam o merge/CAS e a revisão assistida.
6. O caminho comercial normal mostra status/conflitos e esconde controles A–E,
   participação por turma, envio, pausa e retomada manuais. A revisão de conflito e
   reconciliação pós-backup permanecem acessíveis quando necessárias. O modo
   experimental anterior continua disponível com o novo flag desligado.

## Autorização comercial e compatibilidade remota

Migração nova, **somente preparada**:
`supabase/migrations/202611170001_commercial_bootstrap.sql`.
Exige a base v1 + guarda e as sete migrações A–E originais, sem H13/PT409.

- `noctua_commercial_rollout`: botão global, criado **desligado**.
- `noctua_commercial_accounts`: direito individual administrado no servidor,
  `enabled=false` por padrão; `initialized_at`/`policy_version` registram a admissão.
- Ambas as tabelas têm RLS e nenhum acesso direto dos papéis comuns, inclusive
  leitura. Ausência de políticas nessas tabelas administrativas significa deny-all,
  não permissão aberta. Não há autorização por domínio nem concessão pelo Android.
- Novas RPCs `noctua_commercial_status()` (leitura) e `noctua_ensure_academic_e()`
  (inicialização) exigem sessão ativa e confirmada; aceitam **zero parâmetros**.
- A allowlist de testes não é escrita, copiada ou reinterpretada. O antigo campo
  `testAllowed` continua falso para uma conta exclusivamente comercial. O contrato
  antigo de status permanece intacto; a autorização comercial tem DTO próprio.
- `noctua_e_authorized()` é helper privado: mantém a permissão de testes anterior
  OU valida a admissão comercial vigente. Só essa autorização é ampliada no
  principal E e no escritor guardado. Grafo integral, histórico, auditorias,
  revisões, recibos e RPCs de estágio/commit/pull/decisão são reaproveitados.
- A migração exige exatamente a expressão de autorização esperada no escritor;
  aborta ante definição inesperada. A comparação automatizada reconstrói a definição
  original substituindo a nova expressão: todos os demais bytes são iguais.

O direito comercial precisa ser concedido por administração/provisionamento
confiável, fora do cliente. **Não foi implementado um serviço automático de
provisionamento para todo cadastro Auth.** O professor elegível não configura
protocolos, mas esta entrega também não autoriza automaticamente todo e-mail novo.
Na homologação seguinte, o administrador autorizará individualmente uma conta
fictícia nova; as contas atuais não ganham direitos por esta migração.

## Bloqueios de preservação

- UUID do proprietário legado negado também no servidor, mesmo se houver concessão
  administrativa equivocada; workspace legado excluído no Android.
- Qualquer inscrição na allowlist de testes, dados v1 (inclusive tombstones,
  versões ou recibos), protocolo parcial ou registros v2 sem marcador comercial
  bloqueiam a nova inicialização. Não se migra a turma de Aron Danone por esse fluxo.
- Controle/fila v1 local, outbox/inbox órfãos, recibos aplicados, partes pendentes,
  decisões, RESTORE ou consentimento experimental anterior bloqueiam admissão
  automática local. Estados PREPARED antigos não são tratados como autorização
  comercial. Sem limpeza, renumeração ou troca de titularidade.
- `BOOTSTRAP_E` persiste após falha/reinício/logout. A resposta do servidor não
  pode substituir uma restauração ocorrida durante a chamada.
- Contas já ativas continuam suas filas no protocolo autorizado; não são
  reinicializadas. O teste de preservação de Aron utiliza **análogo sintético local**,
  não a conta hospedada do proprietário.
- Room permanece na **versão real 14** desta base, sem nova tabela/coluna/migração
  Android. Mudam apenas captura por estágio e consultas DAO. Backup e criptografia
  não foram alterados. A migração de servidor é aditiva, sem apagar dados.

## Verificações realizadas nesta rodada

| Prova | Resultado observado |
| --- | --- |
| PostgreSQL 17 local, cluster novo em 127.0.0.1:55431 | **20 testes aprovados**, zero falhas na execução final |
| Regressão D original após a migração comercial | **27 testes aprovados**, sem alterar as asserções anteriores |
| PostgREST 12.2.3 local, JWTs sintéticos/sessões SQL fictícias | **35 verificações aprovadas**; 171 registros, quinze tipos, paginação/IDs/recibos/isolamento |
| Comparação do escritor E | Mesma definição, exceto expressão de autorização; nenhum validador retirado |
| JVM, configuração padrão desligada | **322 casos: 317 aprovados, 5 ignorados, zero falhas/erros** |
| APK de instrumentação, configuração padrão | Compilado; testes Room e testes existentes compilados; não executados neste host |
| JVM/build com opt-in comercial | **322 casos: 317 aprovados, 5 ignorados, zero falhas/erros**; APKs debug/aplicação e instrumentação compilados; scanner de credenciais/demo aprovado |
| Android real em dois AVDs descartáveis na CI | **60 aprovações, zero falhas/ignorados**: seis fases HTTP/startup, 52 testes Room/UI/regressão e dois de UI com fonte 1,6 |

Os cinco ignorados são quatro provas hospedadas H01–H04 e uma sonda Auth legada;
não contam como aprovados. O primeiro reset SQL falhou pela FK nova e foi corrigido
para incluí-la no mesmo TRUNCATE da fixture **descartável**, sem CASCADE. A prova de
quinze tipos passou a conferir todas as páginas, mantendo a asserção dos quinze.
Uma chamada de fixture foi corrigida para usar a revisão corrente antes de E,
preservando o bloqueio de cursor antigo. Não se alteraram regras para aprovar testes.

Commits de implementação: `a1c97044a601d6743b32eff9280e446981bee1c8` (servidor)
e `ed550105301832f76780186e1740df9bd989dbb4` (Android/CI). Checkpoint documental:
`4695463`. Execução aprovada:
[44228928532](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44228928532),
SHA **`ed550105301832f76780186e1740df9bd989dbb4`**, job `134548903329`, **SUCCESS**.
Artefato [commercial-auto-local-evidence](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/44228928532/artifacts/14393738292),
ID `14393738292`, 124.462 bytes. A cópia foi baixada e inspecionada; os códigos
nativos confirmam 60 conclusões de sucesso e zero casos ignorados/assumidos/falhos.
Os dois relatórios JVM confirmam 317 aprovados e cinco ignorados **em cada configuração**;
não somar as repetições como cobertura única.

Hash canônico final, **idêntico** nos dois bancos Room:
`f6aa05dca254ece8d0060d16692c817d03d21645269f125e215a947626a3a004`.
Foram preservados os IDs dos 171 registros dos quinze tipos, inclusive movimentação,
FJ/motivos e históricos; a edição no AVD B apareceu em A, e a nota zero feita offline
em A sobreviveu ao reinício e apareceu em B após reconexão, na revisão 2/cursor 3.
A fase de resposta de inicialização perdida conservou `BOOTSTRAP_E` e os dados locais;
o processo seguinte retomou sem criar outro protocolo ou duplicar registros.

Captura real de AVD com fonte 1,6, inspecionada sem sobreposição e sem controles A–E:
[FASE_31_FONT16.png](evidence/FASE_31_FONT16.png).
Consolidação sanitizada: [FASE_31_CI_0220.json](evidence/FASE_31_CI_0220.json).
O [registro local inicial](evidence/FASE_31_LOCAL_0220.json) permanece histórico;
seu estado `ANDROID_IN_PROGRESS_NOT_APPROVED` foi superado por esta execução concluída.

**Resultado: fluxo de inicialização/recuperação comercial validado no ambiente
automatizado descartável. Não é homologação hospedada, Auth real, instalação física
ou liberação do aplicativo oficial.** A concorrência de duas sessões foi comprovada
em PostgreSQL; não se afirma cadastro/login simultâneo de contas Auth reais.

## Executor CI, somente infraestrutura descartável

`.github/workflows/commercial-auto-local.yml`: branch dedicada, permissões apenas
`contents:read`, sem Environment/Secrets/assinatura oficial. Usa PostgreSQL 17,
PostgREST e dois AVDs próprios com nomes vinculados à execução. Não há opção hospedada.

- Reutiliza `AuthTestRunner`, `PhaseBNetworkHarness`, engine, coordenador, Room e
  fixtures de A–E. Auth é simulado localmente; os JWTs são assinados para o
  PostgREST descartável e não representam login real no Supabase Auth.
- Seis fases Android novas: resposta de inicialização perdida; retomada e envio dos
  quinze tipos; recuperação em B; edição B→A; alteração offline/reinício em A;
  reconexão e recebimento em B. Nenhuma chama activate/run/request nem botão de envio.
- Compara hashes finais canônicos dos dois bancos, IDs, revisões e ausência de
  duplicação. Inclui testes Room de filas/rollback/restauração e UI sem menus
  técnicos, também com fonte 1,6. Apenas os AVDs desse job podem ser encerrados.
- Não importa os fixtures antigos que aplicam H13. A prova usa exatamente A–E e
  a nova migração comercial.

## Implantação gradual — autorização futura obrigatória

1. Revisar esta migração separadamente do pacote A–E já implantado; não reaplicar
   o pacote original. Backup administrativo, inventário e contingência de produção
   continuam obrigatórios. Esta tarefa não realiza nenhuma dessas ações hospedadas.
2. Depois de CI aprovada, pedir autorização para aplicar **somente a migração nova**
   em `noctua-homologacao`, nunca produção nesta rodada. Confirmar A–E/guardas/ACL e
   zero efeito sobre a conta de teste antiga. A migração não concede direito a ninguém.
   Conferência de catálogo preparada: `supabase/checks/commercial_031_readonly.sql`.
   Após essa migração, são 16 tabelas A–E/v1 anteriores mais duas administrativas:
   **18 tabelas**. As duas novas devem ter RLS, sem acesso direto anon/authenticated;
   somente as duas RPCs comerciais públicas permitem EXECUTE authenticated. Helpers
   continuam privados. Não confundir ausência de política nessas duas tabelas
   deny-all com as políticas de proprietário das dezesseis anteriores.
3. Mediante autorização administrativa separada, conceder direito comercial a
   **uma conta fictícia nova**, confirmada, sem histórico v1/v2 e fora da allowlist
   temporária. Habilitar o rollout no projeto de teste. Não usar Aron Danone ou
   o proprietário legado, nem cadastrar usuários/alterar allowlist automaticamente.
4. Preparar build debug **paralelo de homologação** com o opt-in comercial, endpoint
   exclusivo daquele projeto e chave pública correspondente. Não usar o debug de
   produção já entregue. Instalar somente em dois AVDs novos, com autorização de
   login/escritas fictícias. Sem assinatura permanente ou sobreposição ao app real.
5. Pela interface normal: login, trabalhar em A, login em B, conferir quinze tipos,
   IDs e revisões, editar nos dois sentidos, offline/reinício, conflito e restore.
   Nenhum menu A–E deve ser necessário. Provar Auth real, não transportar a aprovação
   dos JWTs sintéticos para essa etapa. Só depois avaliar liberação gradual.

## Reversão e limites

Sob autorização administrativa, desligar o rollout comercial ou o direito da conta
interrompe novas operações comerciais; registros, protocolos, versões, recibos e
filas ficam preservados. O caminho antigo de testes mantém sua própria permissão.
Não apagar tabelas, retornar para v1, limpar Room ou restaurar backup antigo como
reversão automática. Preservar os estados BOOTSTRAP_E e ACTIVE_E em qualquer
reversão do aplicativo e suspender o servidor antes de depender só de flags do cliente.

Permanecem pendentes: homologação Auth/Supabase real dessa admissão, ensaio de
atualização assinada, aparelhos físicos, fonte de provisionamento comercial em
escala e orçamento de capacidade já documentado em H13. Não houve cargas novas de
5.171 registros nem ajuste de timeout, RLS, notas, frequência ou remanejamento.

**Não se declara o produto pronto para produção por esta implementação local.**
