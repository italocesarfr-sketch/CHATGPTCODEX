# Produção 0.22.0 — revisão local do pacote A–E

Data: 15/11/2026, horário de Brasília. Branch: `temp/academic-production-phase1`.
Referência de código e SQL: `ac56867ef3c6093670fafd91ddd4f591bc589f8a`.

**Resultado: pacote preparado para revisão; implantação não autorizada nem executada.**
O inventário e o backup abaixo foram relatados pelo proprietário. Nesta revisão não
houve conexão ao Supabase, SQL executado, acesso a credenciais ou leitura do backup.
Não se declara a produção pronta nem a restauração comprovada.

## 1. Evidências e limites

| Origem | Evidência | Limite |
| --- | --- | --- |
| Administrador, produção `mpsgdchpgyxoqowfjoqm` | Quatro tabelas v1 com RLS; doze tabelas A–E ausentes; guarda de sessão presente; ativação e wrapper v2 ausentes | Relato de catálogo, sem nova consulta independente |
| Administrador | Quatro políticas `own_rows`; três funções SECURITY DEFINER com search_path seguro, anônimo negado e autenticado permitido | Assinaturas, privilégios efetivos completos e definições precisam ser conferidos conforme seção 3 |
| Administrador | Backup com pg_dump 17, 426.467 bytes, catálogo legível por `pg_restore --list`, incluindo v1 e Auth | Arquivo não recebido/inspecionado; checksum não informado; restauração integral não comprovada |
| Revisão local atual | Sete fontes, cabeçalhos, hashes, corpos e envelope do pacote conferidos; 18 verificações estáticas aprovadas | Não equivale a executar SQL ou a validar RLS com sessões reais |
| Histórico no repositório | Pacote idêntico ao publicado em `docs/homologacao/0220/homologacao-0220.sql` | O manifesto antigo tinha finalidade de homologação; não autoriza produção |

## 2. Pacote produzido e manifesto

Comando executado **localmente**, sem banco ou rede:

```bash
python3 scripts/prepare-phase-e-deployment.py --output build/producao-0220.sql
```

O destino não existia antes da geração. O gerador utiliza criação exclusiva e não
sobrescreve arquivos existentes.

- Arquivo: `build/producao-0220.sql`.
- Tamanho: **120.010 bytes**, **1.504 linhas**.
- SHA-256: `7fd28b6dbce5f72724abc1a2644041f19f373fc7675eb37a24bd17fa9a46659b`.
- Manifesto local: `build/producao-0220.manifest.json`.
- Manifesto versionado: [evidence/PRODUCAO_PACOTE_0220.json](evidence/PRODUCAO_PACOTE_0220.json).

Os arquivos de `build/` são locais e ignorados pelo Git. Para revisão externa, a
[cópia SQL já versionada](homologacao/0220/homologacao-0220.sql) tem os mesmos bytes;
o nome histórico não define um destino de execução. Nenhum SQL deve ser executado
a partir deste relatório sem autorização posterior específica.

Ordem e SHA-256 dos fontes, todos comparados ao manifesto auditado de
`HOMOLOGACAO_SUPABASE_0220.md`:

| Ordem | Migração | SHA-256 |
| --- | --- | --- |
| 1 | `202611010001_academic_protocol.sql` | `32e3e7f43e4c8e999c900bf9b4ae8307170cae42228261d0c4d461fcf3755120` |
| 2 | `202611020001_phase_b_fence.sql` | `680bbf805f30b4c15ebb3b8cda1ff286183083e2fe7437e4c8f625ffa4c4031a` |
| 3 | `202611020002_phase_c_scope.sql` | `8d6df95a1c1eb57e8603790fa6f93c8867f00ab3f1e4cf3e4d811c09a1b34d06` |
| 4 | `202611020003_phase_d_scope.sql` | `7e51af0f29843f02c93a34b1be8c005f96e5d27c70045c2b28fc40d9ba07f3df` |
| 5 | `202611030001_phase_e_paging.sql` | `0eb5a4bad62743a28c1cd42e28e70711fd197a1b317804cbcd64e7beb1eb9813` |
| 6 | `202611030002_phase_e_decisions.sql` | `f7785853b4438e72aea24a17b8b91812c4a250efd0a5e6ab8d61c8c3e44b54bf` |
| 7 | `202611030003_phase_e_indexed_graph.sql` | `9ecd918240458a9367f3acba286a79445999b1b7058ac0e48fe3eab8ccc6f918` |

Conferências efetivamente realizadas:

1. Sete hashes e ordem coincidentes com o manifesto histórico.
2. Cada corpo coincide exatamente com seu fonte, retirados somente os marcadores
   standalone `BEGIN;`/`COMMIT;` de cada migração. Sem edição de RLS, escopos,
   predicados, recibos ou guardas.
3. Um único `BEGIN;` externo e um único `COMMIT;` externo final; blocos
   PL/pgSQL `BEGIN` não foram confundidos com transações.
4. Preflight antes das migrações: exige objetos v1/guarda e rejeita objetos v2
   preexistentes. Examina referências à sessão e confirmação no principal.
   É uma barreira adicional, não substitui a revisão completa do inventário.
5. `SET LOCAL lock_timeout='5s'`; nenhuma mudança de `statement_timeout`, de
   configuração de papel ou banco. `NOTIFY pgrst, 'reload schema'` antes do commit,
   com entrega da notificação condicionada à confirmação da transação.
6. Doze definições de tabelas novas. Na A, um laço habilita RLS, revoga privilégios
   e cria política de proprietário nas sete tabelas; B/E fazem isso nas cinco
   restantes. A revisão é estática; permissões efetivas serão conferidas no catálogo.
7. Principal original não redefinido; v1 e guarda não reaplicados. A fase B cria
   wrappers/barreiras e privatiza helpers, alterando definições globais de RPC.
8. Ausentes `202611130001_h13_registry_cache.sql`,
   `202611150002_scope_conflict_http.sql` e `PT409`. O helper E4 `noctua_d_index`
   pertence ao pacote original e não é a candidata H13.
9. Ausentes `DROP TABLE` e `TRUNCATE`. Substituições previstas de políticas e do
   CHECK de escopo permanecem exatamente nos fontes auditados.

O pacote cria sete tabelas de registros/versões/grafo/recibos, duas de
elegibilidade/protocolo e três de transferência/partes/decisões. Com as quatro v1,
o inventário esperado após uma futura implantação é de **16 tabelas** protegidas.
Ele define RPCs A–E, revisões, paginação, decisões e reconciliação; **não chama a
ativação, não autoriza contas e não migra automaticamente dados de usuários**.

Não se repetiram benchmarks, testes Android ou suítes SQL já documentados. Esta
rodada fez comparação de bytes, hashes e revisão estrutural; não houve nova
análise sintática por parser SQL nem execução dos corpos PL/pgSQL em banco.

## 3. Verificações ainda necessárias antes de autorizar DDL

O relato é compatível com o baseline nos pontos informados. Não permite concluir
sozinho que todo o baseline está certificado. O administrador deve conservar e
revisar os resultados completos, somente de catálogo, do inventário original:

- Nas quatro tabelas v1, confirmar `anonymous_read=false`, `direct_write=false`,
  RLS e políticas SELECT por proprietário, sem políticas adicionais permissivas.
  RLS e existência de `own_rows` não demonstram por si só os privilégios efetivos.
- Confirmar assinaturas, proprietário, ACL e search_path das três funções:
  `noctua_sync_principal()`,
  `noctua_push_class(uuid,text,bigint,boolean,jsonb)` e
  `noctua_pull_classes(bigint)`. Comparar definições com v1 + guarda auditadas,
  sem reaplicar scripts para tentar corrigir divergências.
- Confirmar o helper `noctua_validate_class(text,jsonb)`: esperado sem SECURITY
  DEFINER e sem EXECUTE concedido a PUBLIC/anon/authenticated. O relato de três
  funções SECURITY DEFINER não afirma que só há três funções no total.
- Confirmar os três resultados da guarda: consulta sessões, verifica confirmação
  e proprietário da função pode ler `auth.sessions`. A definição deve preservar
  UID/role, expiração JWT, session_id, sessão ativa, confirmação e não anonimato.
  Conferir também presença/tipos das colunas Auth exigidas pelo script.
- Conferir ausência de outros objetos acadêmicos parciais, inclusive funções,
  wrappers/helpers privados e restrições, além das doze tabelas conhecidas.
  **Qualquer divergência bloqueia o pacote padrão; não tentar repará-la.**
- Registrar versão efetiva do servidor, limites de sessão/papel/API e janela de
  implantação. A versão do cliente pg_dump não comprova a versão do servidor.
- Concluir a avaliação de recuperabilidade da seção 5. Não interpretar catálogo
  legível ou tamanho do dump como restauração integral aprovada.

Nenhum desses dados foi consultado remotamente nesta tarefa. Não encaminhar
conteúdo de Auth, dados acadêmicos ou credenciais para realizar a revisão.

## 4. Riscos e orçamento de tempo

| Risco | Situação / proteção |
| --- | --- |
| Backup sem restauração comprovada | Pendente; checklist privado abaixo antes da decisão de implantação |
| Baseline/ACL parcialmente relatados | Conferir catálogo completo; interromper em qualquer divergência |
| Alteração global de RPCs | B renomeia helpers e cria wrappers; usar transação única e janela administrativa aprovada |
| Locks / timeout do SQL Editor | `lock_timeout=5s` é local; não limita duração total. Timeout administrativo efetivo não foi verificado |
| Resposta de commit perdida | Não repetir; inventário somente leitura para distinguir rollback de commit confirmado |
| Desempenho futuro da API | Referência documentada do Supabase: authenticated 8s; valor efetivo em produção não verificado. Benchmark histórico A–E registrou 8,647s para commit grande de 5.171 registros/41 partes em ambiente local |
| Custos e recuperação gerenciada | Não presumir PITR/backups automáticos disponíveis no plano Free, nem contratar recursos nesta etapa |

O tempo de uma futura RPC autenticada é diferente do timeout administrativo de
DDL no SQL Editor, do timeout HTTP Android e do tempo total de transferência.
Os 120.010 bytes do pacote não demonstram capacidade para grandes acervos. Não
aumentar timeouts, fracionar movimentações ou reduzir validadores para contornar
limites. As candidatas H13/PT409 continuam fora desta preparação.

## 5. Checklist privado de restauração e contingência

### Backup atualizado — antes de qualquer autorização de implantação

- [ ] Administrador registra data/hora, origem e escopo do dump de 426.467 bytes,
  versão exata do pg_dump e comando utilizado, sem divulgar credenciais.
- [ ] Calcula SHA-256 local do **backup**, ainda não informado, e verifica as
  cópias privadas cifradas fora do projeto. O hash do SQL não é o hash do backup.
- [ ] Revisa catálogo e logs: schemas, funções, políticas/ACL, índices,
  constraints, sequências e entradas **TABLE DATA** de v1 e Auth. Verifica erros,
  exclusões/filtros do dump e cobertura do histórico de migrações quando existir.
- [ ] Registra separadamente roles/globais e configurações externas Auth/API
  necessárias. Um pg_dump de banco não inclui todos os objetos globais do cluster,
  configurações do painel, credenciais externas nem arquivos binários de Storage.
- [ ] Prepara um ensaio de restauração integral em PostgreSQL 17 compatível,
  vazio, privado e isolado, com extensões/roles requeridos e procedimento suportado
  para schemas gerenciados do Supabase. Compatibilidade do catálogo não garante
  privilégios suficientes para restaurar Auth e proprietários.
- [ ] Obtém autorização específica para esse ensaio se utilizar o backup com
  dados reais; não o envia ao Codex, GitHub, CI ou projeto de homologação. Nenhum
  ensaio com dados reais está autorizado ou executado por este documento.
- [ ] Desabilita saídas de rede, jobs, webhooks e envio de e-mail no ambiente
  privado de restauração. Usa parada no primeiro erro e destino comprovadamente
  vazio, sem limpeza destrutiva de bancos existentes.
- [ ] Confere privadamente a restauração: erros zero, estrutura/ACL, integridade
  referencial, sequências, cobertura de dados e consistência v1/Auth. Guarda
  evidências sem conteúdo pessoal. Até então, mantém **restauração não comprovada**.

O backup lógico cifrado do aplicativo permanece disponível e inalterado. Não
substitui o backup administrativo nem resolve por si só restauração de Auth/API.

### Futura execução — depende de nova autorização, fora desta tarefa

1. Confirmar com o proprietário o projeto aberto **antes de cada execução**:
   produção `mpsgdchpgyxoqowfjoqm`; não homologação `sscexzxtqdcsqdlcytum`.
2. Confirmar backup atual e baseline completo. Se a revisão/espera permitir novas
   gravações, avaliar atualização do backup e nova conferência antes do DDL.
3. Somente após autorização explícita, executar o pacote inteiro, em uma sessão
   do SQL Editor; não executar fontes separados nem reaplicar v1/guarda.
4. Se houver erro, timeout ou resultado incerto, **não repetir**. Após confirmar o
   projeto, repetir `supabase/checks/prehomo_0220_readonly.sql` para examinar o
   catálogo. Não executar fixtures ou RPCs de escrita como diagnóstico.
5. Baseline intacto com quatro tabelas e ausência de v2 indica rollback compatível
   com a transação; não é autorização automática para tentar outra vez. Presença
   das 16 exige conferência completa; estado parcial/incerto exige parada e revisão.
6. Depois de sucesso inequívoco, repetir inventário e
   `supabase/checks/phase_e_readonly.sql`: 16 tabelas com RLS/políticas de
   proprietário, sem SELECT anônimo e sem DML direto autenticado. Conferir helpers
   privados, RPCs e escopos. Diferenças bloqueiam a conclusão de prontidão.
7. Não reverter um commit confirmado reaplicando v1, derrubando tabelas A–E ou
   restaurando cegamente o dump antigo. Preservar o estado e obter autorização
   administrativa separada para qualquer reparo/restauração. Uma restauração pode
   perder gravações legítimas posteriores ao backup; exige reconciliação e janela.
8. Allowlist de uma conta fictícia, uso do protocolo e candidato APK são etapas
   separadas, cada uma com autorização própria. Não autorizar conta legada ou
   remover o conflito preservado do proprietário.

## 6. Próximo ponto de decisão

Disponibilizar este relatório, o manifesto e o SQL para revisão. Antes de solicitar
autorização de implantação, concluir as conferências pendentes de catálogo e a
avaliação do backup. Não executar Supabase, assinatura, APK ou mudanças no aplicativo
como parte desta preparação. `main` e `sync/academic-complete-0220` permanecem intactas.
