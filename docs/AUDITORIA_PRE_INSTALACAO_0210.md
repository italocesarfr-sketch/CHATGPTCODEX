# Auditoria pré-instalação da 0.21.0

## Decisão sobre o APK anterior

**REPROVADO — CORREÇÃO NECESSÁRIA**, para o candidato **0.21.0/código 39** do commit `9ca36a32f69c2b98dfc6baea2afe298d1cab9fb2`.

O arquivo é íntegro, assinado com o certificado permanente e compatível com as assinaturas RPC. A reprovação decorre de dois problemas reproduzidos na auditoria, e não de falha de assinatura ou perda de dados:

1. O engine verificava se o teste estava habilitado somente na entrada. Uma pausa durante um envio não impedia novas requisições do mesmo lote. A resposta já em trânsito não era confirmada no Room pausado, mas o próximo envio ainda podia acontecer.
2. A migração inicial valida identidade/e-mail confirmado, porém não a existência da sessão Auth. Um JWT ainda não expirado pode continuar válido no PostgREST após logout; sem conferir `auth.sessions`, a sincronização não identifica essa revogação online.

Não foi encontrada evidência de leitura pública ou de acesso de uma conta a outra. Isso **não** equivale a isolamento hospedado homologado entre duas contas comuns.

Foi necessária uma correção do cliente; portanto o substituto mantém **versionName 0.21.0**, com **versionCode 40**. Não houve mudança visual, de Room, do backup ou de regra acadêmica nesta auditoria. O SQL complementar também deverá ser aplicado antes do ensaio. **CI do substituto: aguardando execução; não instalar enquanto não for aprovado.**

## Evidências existentes conferidas

- Branch `sync/academic-foundation-0210`, documentação inicial `4f3b2f212742b5d8419811ec595c76723e5effd5`.
- `main` preservada na 0.19.1; base de autenticação preservada na 0.20.0. O proprietário relata homologação física de autenticação/acesso na 0.20.0; esta auditoria não repetiu nem invalidou esse teste.
- [CI do código 39](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40988203896): três jobs aprovados. XMLs: 214 JVM aprovados/1 condicional ignorado; 346 Android aprovados/7 condicionais fora da suíte comum; 23 PostgreSQL aprovados. As sondas específicas e as 9 fases assinadas estão documentadas na [fundação](SINCRONIZACAO_ACADEMICA_0210.md).
- APK código 39: SHA-256 `717f98a9a34c6ea43dfd6f670a380f263cb2857df93579e94446f5eb75706750`, igual ao checksum publicado e a `apk-tested.sha256`.
- Pacote `br.com.diariodeclasse`; assinatura v2 válida, um assinante, certificado SHA-256 `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- A referência original 0.20.0/código 38 tem SHA-256 `31dd1dfdd740658137aa2de7529070a1c7bce4a7342e620e10874310fb77353a`, mesmo certificado. O ensaio assinado código 38→39 aprovou preservação, reinício, backup/restauração, vinculação e isolamento com dados fictícios.
- Scanner do APK não detectou chaves administrativas, clientes de teste ou entrada demonstrativa. Esse scanner é complementar à revisão do código; não prova ausência de todo defeito possível.

A aprovação anterior não cobria os novos casos de pausa e sessão revogada; os testes foram acrescentados sem retirar ou relaxar as verificações anteriores.

## Compatibilidade Kotlin / PostgreSQL

| Item | Cliente e servidor verificados |
|---|---|
| Origem | `https://mpsgdchpgyxoqowfjoqm.supabase.co`; sem redirecionamentos |
| Chave | `AuthConfig.KEY`, mesma chave pública `sb_publishable_…` autorizada; nenhuma chave administrativa |
| Autenticação | `Authorization: Bearer` da conta admitida, não UUID arbitrário. JWT não é persistido na fila |
| Envio | POST `/rest/v1/rpc/noctua_push_class`; JSON `p_operation` UUID, `p_id` texto, `p_base` inteiro, `p_deleted` booleano, `p_payload` objeto ou null |
| Consulta | POST `/rest/v1/rpc/noctua_pull_classes`; `p_after` inteiro; até 100 registros por página |
| Configuração | `school` com os 13 campos reais de `SchoolClass`; `terms` com quatro períodos e a mesma identidade da turma |
| Identidade | `owner_id` não é parâmetro RPC. O servidor deriva o usuário de `auth.uid()`; o novo guard verifica a sessão desse mesmo usuário |
| Resposta | `PushReply(operationId,accepted,record)`; registro com `id,revision,sequence,deleted,payload`. `payload` é texto JSON, como espera o Kotlin |
| Conflito | Revisão esperada e recibo idempotente, nunca last-write-wins. Local/base/operação/remoto preservados |
| Limites | Servidor limita agregado a 16 KiB; cliente limita resposta a 2 MiB e tempo de conexão/leitura a 15/20 s. Erro não confirma fila |

`noctua_validate_class` é auxiliar invocadora, sem EXECUTE para usuários comuns. `noctua_sync_principal` retorna somente a própria identidade autorizada ou null. Nenhuma delas é usada para o cliente escolher titularidade. Nomes, tipos, assinatura e retorno dos RPCs permanecem iguais na migração complementar; a correção SQL não exige novos parâmetros no APK.

A fila registra a versão suja na transação acadêmica e materializa operação imutável antes do envio. UUID de operação e revisão esperada sobrevivem ao processo. A aceitação exige operação/ID/revisão/payload correspondentes e só confirma a versão efetivamente enviada. Edição posterior continua pendente. Aplicação remota e cursor compartilham transação; registros relacionados protegem contra sobrescrita/exclusão. A restauração continua pausando a sincronização, sem apagar sua auditoria.

## Reproduções e correções

### Pausa

A mesma lógica do engine do código 39 foi exercitada em quatro testes JVM, com uma fronteira pequena de fila substituível. **Antes: 4 falhas/4 casos. Depois da correção: 4 aprovados.** O Room real continua implementando a fila; não foi incluído modo fictício em produção.

O engine agora revalida admissão **e habilitação** após obter a sessão, antes de cada requisição, após registrar a tentativa e antes de aceitar respostas/consultar a próxima página. Uma requisição que já começou pode terminar no servidor; pausa não desfaz retroativamente uma gravação em trânsito. Nenhuma nova requisição é iniciada depois que a pausa é observada. O recibo permite retomar com o mesmo UUID sem duplicar dados.

Foi adicionado teste Android com duas turmas fictícias: pausar durante o primeiro envio, conservar as duas operações, impedir o segundo envio e retomar ambas sem duplicidade ou mudança acadêmica. Precisa passar no CI do código 40.

### Revogação online

No PostgreSQL local descartável, uma identidade fictícia confirmada com `SET ROLE authenticated`, mas sem sessão ativa, obteve principal, envio aceito e consulta no SQL antigo. É uma reprodução da **lacuna da autorização SQL**, não um login real nem JWT validado pelo Supabase hospedado.

A documentação oficial do [Supabase sobre sessões](https://supabase.com/docs/guides/auth/sessions) explica que cada JWT possui `session_id` e que a existência desse ID em `auth.sessions` deve ser conferida quando são necessárias garantias de revogação antes de `exp`. Validar assinatura/expiração do JWT por si só não revoga instantaneamente uma sessão encerrada.

Correção adicional: [`202610310001_sync_session_guard.sql`](../supabase/migrations/202610310001_sync_session_guard.sql). Ela:

- verifica que o esquema Auth e o acesso de leitura do proprietário da função existem, abortando integralmente se não existirem;
- exige role autenticada, `exp` válido, `session_id` bem formado, sessão existente do mesmo UUID, e-mail confirmado, usuário não anônimo e `not_after` não ultrapassado;
- usa o mesmo guard nos dois RPCs e, através das políticas existentes, no SELECT direto;
- mantém `search_path=pg_catalog`, referências qualificadas e funções auxiliares sem privilégios públicos;
- não altera nem apaga usuários, sessões, turmas, versões, recibos ou dados locais;
- não ativa limites pagos de sessão. `not_after` nulo continua permitido; nenhum domínio/SMTP adicional é necessário.

A revogação é conferida para novas requisições após a remoção/expiração da sessão no servidor. Uma operação já autorizada e em trânsito pode concluir. Logout offline não equivale a confirmação remota de revogação. O acesso acadêmico local offline conserva a política da 0.20.0; não foi transformado em bypass de rede.

## Auditoria RLS

O proprietário informou quatro tabelas protegidas, RPCs disponíveis e somente quatro políticas `own_rows` SELECT/authenticated. Essa é evidência fornecida da instalação do SQL inicial, não uma verificação independente do catálogo remoto por esta execução.

Revisão do SQL versionado e testes PostgreSQL com papel comum:

- RLS habilitada nas quatro tabelas; SELECT limitado à identidade confirmada e, após o complemento, sessão ativa;
- nenhuma política pública permissiva; sem INSERT/UPDATE/DELETE/TRUNCATE direto para `anon`, `authenticated` ou PUBLIC;
- chaves compostas por proprietário e ID; conta B usando o mesmo ID cria somente seu próprio espaço, nunca o de A;
- RPCs sem parâmetro de proprietário, acesso anônimo recusado;
- `SECURITY DEFINER` restrito a funções com guard explícito; `noctua_validate_class` não é definidora;
- `search_path` fixo, sem SQL dinâmico ou nomes de objetos fornecidos pelo cliente;
- verificação de e-mail no servidor, não `user_metadata` escolhido pelo cliente;
- sessão de outra pessoa, sessão encerrada, JWT expirado/malformado e falta de claims bloqueados;
- revogar um dispositivo não bloqueia outra sessão válida do mesmo usuário;
- versões e dados acadêmicos permanecem após recusa; rollback e idempotência mantidos.

**30 testes SQL aprovados localmente**. O bootstrap cria somente Auth de fixture no banco local explicitamente descartável `noctua_sync_test`, com validação de host. As verificações de acesso usam `SET ROLE authenticated/anon`; não usam privilégio de administrador para obter um resultado de usuário comum. Isso ainda **não verifica assinatura JWT nem duas contas reais hospedadas**.

### Provas hospedadas efetivamente executadas

O script [`audit-sync-hosted.py`](../scripts/audit-sync-hosted.py) usa somente chave pública e um JWT intencionalmente inválido. Nenhuma credencial de usuário é solicitada. Não segue redirecionamentos, não imprime tokens e não lê registros acadêmicos.

**12 recusas HTTP 401 aprovadas** no projeto autorizado:

- quatro RPCs com somente chave pública: `42501`, acesso recusado;
- os quatro RPCs com JWT inválido: `PGRST301`, token recusado;
- HEAD direto às quatro tabelas com `limit=0`: acesso anônimo recusado.

A sondagem de envio usa operação inválida (`p_base=-1`), que não pode gravar registros. A consulta usa cursor máximo, e HEAD não retorna linhas. Esses testes mostram que a API está instalada e não aceita acesso anônimo/JWT inválido; não comprovam isolamento entre A/B autenticados.

### Inspeção remota ainda necessária

Executar no SQL Editor **somente a consulta de leitura** [`20261031_readonly.sql`](../supabase/audit/20261031_readonly.sql). Ela lista RLS/políticas/grants de tabela e coluna, proprietário/ACL/search_path/assinatura/hash das funções e a presença dos guards. Não consulta linhas acadêmicas nem tokens ou senhas.

Depois da migração complementar, esperar:

- quatro tabelas com `rls_enabled=true`, somente as quatro políticas de leitura próprias;
- `anon` sem SELECT/escrita; `authenticated` com SELECT e sem escrita direta; consulta de privilégios de coluna vazia;
- RPCs/principal: EXECUTE authenticated=true, anon/PUBLIC=false; validador: EXECUTE authenticated/anon/PUBLIC=false;
- `security_definer=true` somente nos RPCs/principal, `search_path=pg_catalog`;
- `active_session_guard_present=true` nos RPCs/principal, e `owner_can_read_sessions=true` no principal;
- nenhum CREATE de objetos Auth por usuário comum.

O SQL Editor é administrativo e pode ignorar RLS. **Não executar SET ROLE/JWT fictício no projeto hospedado e apresentar isso como login real.** Não enviar senhas, tokens, links de recuperação ou credenciais no chat. Se o catálogo divergir, interromper o ensaio e revisar a divergência.

## Cenários A–F e limites da prova

| Cenário | Evidência local/CI existente | Hospedado com usuário comum |
|---|---|---|
| A — criar online | Room→fila→engine→confirmação/indicador, transporte fictício; SQL real local | Pendente |
| B — offline/reconectar | Persistência, tentativa recusada, retomada e mesma operação | Pendente no aparelho |
| C — segunda instalação | Segundo Room, mesmo ID/quatro termos/sem duplicação | Pendente |
| D — outra conta | Dois papéis comuns no PostgreSQL local; isolamento local Auth/Room | Pendente com duas contas reais autorizadas |
| E — conflito | Duas revisões, CAS, payloads e estado preservados | Pendente em dois dispositivos |
| F — falhas | Perda de ACK, repetição, morte de processo, JWT/role inválidos, pausa; rollback | JWT inválido/anônimo hospedados aprovados; timeout/revogação com sessão real pendentes |

Não há credenciais privadas de teste disponíveis nesta execução, nem foi solicitado seu envio. Não foram criadas contas do piloto. Cadastro/login não foram simulados como prova de nuvem; os testes de backend Auth da etapa anterior não substituem sincronização hospedada.

## Preservação e atualização

Nesta correção, Room permanece **v11**, sem alteração de entidade ou migração local. Backup acadêmico **formato 12**, envelope criptografado, senha e vinculação legada permanecem intactos. Cada conta conserva seu banco e UUID; nenhuma turma entra na sincronização automaticamente. Nenhum aluno, nota, frequência, justificativa, diário ou matrícula é transmitido.

O APK anterior já tinha atualização assinada 0.20.0→0.21.0/c39 aprovada com dados fictícios. O novo código 40 deve repetir a atualização assinada antes da entrega. A referência 0.19.1 tem mesmo pacote/certificado e Room v10, mas **não declarar 0.19.1→c40 instalado e aprovado sem teste direto**. O ensaio 0.19.1→0.20.0 antigo é evidência histórica, não prova transitiva suficiente.

Nenhuma instalação em aparelho do proprietário, restauração de banco real ou migração destrutiva foi executada por esta auditoria.

## Roteiro seguro do primeiro ensaio

1. Aguardar o CI do código 40 e o artefato assinado indicado ao final deste relatório. Não usar o código 39 neste teste.
2. Como o SQL inicial já está instalado, aplicar **apenas a nova migração `202610310001_sync_session_guard.sql`**, em transação, pelo proprietário. Não reaplicar a migração antiga depois da nova: ela reinstalaria os guards antigos. Usar os arquivos na ordem versionada em instalações novas.
3. Rodar a inspeção somente de leitura e conferir os resultados acima. Não é necessário compartilhar credenciais administrativas.
4. Usar **dois ambientes separados do aparelho de trabalho**, sem dados acadêmicos reais. Não desinstalar/limpar a instalação 0.20.0 do proprietário.
5. Na instalação A, autenticar conta autorizada, criar turma **AUDITORIA FICTICIA 0210**, sem alunos, notas, frequência ou diário. Em Conta → Sincronização de turmas • teste, ativar e confirmar participação somente dessa turma.
6. Confirmar o envio e os quatro bimestres no servidor; no aplicativo, sincronizado somente após confirmação. Anotar apenas ID/revisão da turma fictícia para comparação.
7. Offline, alterar somente o nome dessa turma. Confirmar persistência/pendência, reabrir, reconectar e aguardar o agendador (o Android pode adiar) ou usar Tentar sincronizar.
8. Na instalação B, entrar com a mesma conta, ativar o teste e conferir mesmo ID, termos e ausência de duplicação. Não se espera recuperar alunos/notas/diário.
9. Fazer edições concorrentes na mesma turma fictícia; conferir conflito preservado. Não editar filas/SQL manualmente para forçar sobrescrita.
10. Em conta B **comum, distinta e autorizada**, conferir ausência da turma de A. Para prova completa, testar também SELECT/PATCH/DELETE filtrados pelo ID fictício de A e RPCs usando esse ID, com a sessão própria de B em ferramenta local, sem expor tokens. O usuário não pode escolher `owner_id`; criar configuração própria com o mesmo ID não pode afetar A. Não usar chave administrativa.
11. Encerrar uma sessão de teste e conferir que o JWT anterior não permite novas leituras/escritas de sincronização, mesmo antes de expirar. Não confundir revogar uma sessão com encerrar todas as sessões do proprietário.
12. Não restaurar backups ou apagar dados reais para testar. Manter o backup protegido atual; sincronização parcial não substitui recuperação integral.

Pendente: ações A–F hospedadas com duas contas comuns; ampliação nativa da fonte do novo diálogo/TalkBack e teste de fabricantes. Nenhuma delas é declarada aprovada por esta auditoria.
