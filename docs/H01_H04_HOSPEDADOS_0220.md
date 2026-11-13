# H01–H04 — credenciais privadas e execução hospedada autorizada

**Atualização de 12/11:** a execução hospedada #193 aprovou H01/H02/H04 e falhou
em H03. A incompatibilidade PATCH do cliente antigo foi reproduzida/corrigida
localmente. Ver [diagnóstico, testes e seleção exclusiva H03](H03_DIAGNOSTICO_0220.md).
Nenhuma nova execução hospedada foi autorizada ou disparada nesta correção.

Registro histórico da preparação em **10/11/2026, America/Sao_Paulo**. Branch exclusiva
`sync/academic-complete-0220`. **Nenhuma requisição Supabase foi executada nesta
preparação; naquele momento H01–H04 estavam NÃO EXECUTADOS.**

### Correção da pré-verificação #191

A [execução #191](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/42935303698),
no SHA `ff0b8bc6fc0dc1c1dfe679a3aab1a9ecfda5949c`, falhou no passo de
pré-requisitos; Java, compilação e testes hospedados foram pulados. O proprietário
informou `ACCOUNTS_SECRET_MISSING` e confirmou Environment, restrição de branch e
Secret existentes. O artefato não pôde ser baixado nesta revisão; esse código
específico permanece evidência relatada, não conteúdo de artefato inspecionado.

O chamador não repassava `NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON`. A
[documentação atual do GitHub](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows#using-inputs-and-secrets-in-a-reusable-workflow)
exige o repasse por nome mesmo quando o Secret existe somente no Environment:
"The caller workflow must still pass the secret." Sem esse repasse, ele pode
resolver para string vazia no reutilizável. Foi acrescentado somente esse
mapeamento à chave pública já encaminhada, sem `secrets: inherit` no job H01–H04.
O Environment permanece no job reutilizável e seu valor tem precedência.
`required: true` agora impede omissão do parâmetro pelo chamador, mas não verifica
se o valor está preenchido: o preflight continua bloqueando conteúdo ausente.

Esta correção foi validada apenas localmente. Para conferir o repasse real,
iniciar **nova execução** da pré-verificação conforme o comando abaixo; não usar
"Re-run jobs" de #191, pois ele reutiliza o código antigo. Manter
`authorize_hosted_h01_h04=false`. Nenhum novo workflow foi disparado nesta revisão.

O proprietário relatou aprovação prática com duas contas distintas, troca de conta
no mesmo emulador, sincronização separada e recuperação do workspace original.
Isso é confirmação manual da experiência de isolamento, não execução automatizada
de Auth/REST/RLS/revogação. A cobertura prática foi registrada separadamente da API.

## Opção recomendada: Environment Secrets do GitHub Actions

O repositório foi conferido como **público**. Environment/Secrets podem ser usados
sem contratar plano pago. O executor atual do Codex não tem chave/arquivo de contas
ou autorização de execução configurados; a ferramenta de configuração segura de
secret no executor não está disponível nesta sessão. A rota concreta preparada usa
um runner GitHub descartável e o Environment **`noctua-homologacao-h01-h04`**.

- Senhas entram somente no formulário **Secrets** do GitHub, nunca no chat, Git,
  variáveis públicas, inputs de workflow, linha de comando ou arquivos anexados.
- O JSON é disponibilizado apenas ao passo de preparação. Em execução autorizada,
  gera arquivo fora do checkout em `RUNNER_TEMP`, diretório 0700/arquivo 0600.
- O teste continua lendo `NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE`, sem alterar sua
  arquitetura. Caminho não é credencial; conteúdo nunca é impresso.
- Logs Gradle ficam privados no temporário; relatórios XML/HTML brutos não são
  publicados. Não há cache Gradle deste job. A limpeza remove temporário e
  relatórios brutos; o runner descartável também é encerrado ao final.
- **Único artefato permitido:** `h01-h04-resumo-redigido`, contendo apenas
  `build/hosted-h01-h04/result.json`. Não inclui e-mails, senhas, UUIDs das contas,
  fixture ID, tokens, stack traces, corpos de resposta ou streams dos testes.
- Preflight só valida configuração local. Verde nesse modo **não aprova testes**:
  as quatro linhas continuam `NOT_EXECUTED`, `all_four_passed=false`.

## Ações exatas do proprietário

### 1. Reunir os dados no computador/cofre privado

No projeto **noctua-homologacao** (`sscexzxtqdcsqdlcytum`), escolher as duas contas
fictícias já existentes/confirmadas/não anônimas/allowlisted. Não criar usuários,
resetar senhas ou alterar allowlist/RLS para esta preparação.

- **A e B:** e-mail/senha já conhecidos e UUID correspondente em Authentication →
  Users. `expectedId` é o UUID Auth normalizado; os dois devem ser distintos e não
  podem ser a identidade proprietária legada. Não colar esses dados no chat.
- **fixtureClassId:** escolher turma **fictícia já sincronizada por A**. Em Table
  Editor → `noctua_academic_records`, filtrar `owner_id = UUID de A`,
  `kind = classes`, `deleted = false`. Copiar somente o valor `id` do JSON `key`.
  É o ID persistente da turma, não seu nome, número da chamada ou UUID da conta.
  Essa consulta administrativa só localiza a fixture; não é prova de RLS.
- Manter essa turma sem edição durante os poucos minutos do ensaio. Não limpar,
  reinstalar ou desconectar os dois emuladores existentes.

Preencher **privadamente** o JSON abaixo. Os marcadores não são valores utilizáveis:

```json
{
  "accounts": [
    {"email": "<EMAIL_A>", "password": "<SENHA_A>", "expectedId": "<UUID_AUTH_A>"},
    {"email": "<EMAIL_B>", "password": "<SENHA_B>", "expectedId": "<UUID_AUTH_B>"}
  ],
  "fixtureClassId": "<VALOR_ID_DA_KEY_DA_TURMA_DE_A>",
  "allowZeroRowPermissionProbe": true
}
```

O último campo permite preparar H03, mas **não autoriza requisições sozinho**.
H03 envia PATCH com revisão positiva E revisão menor que zero, condição
contraditória que não pode afetar linhas. Não há DELETE ou atualização acadêmica
real. A opção exige autorização específica junto com a execução.

### 2. Configurar somente o GitHub, sem modificar Supabase

No repositório: **Settings → Environments → New environment** (ou abrir o existente)
→ nome **`noctua-homologacao-h01-h04`**.

- Em **Environment secrets**, adicionar `NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON`, com o
  JSON privado preenchido. Não usar Variables nem salvar o JSON no repositório.
- Confirmar a chave **publishable de noctua-homologacao** em
  `NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY`: pode permanecer no repository secret já
  usado pelo build paralelo, ou ser definida como Environment secret desse nome.
  Não reutilizar chave de noctua-auth; não usar `service_role`, `sb_secret_`, JWT
  legado ou token de sessão. A chave opaca só é vinculada efetivamente ao projeto
  quando Auth real responde; o preflight confirma formato/ausência da chave oficial.
- Restringir Deployment branches/tags à branch `sync/academic-complete-0220`.
  Se houver revisão obrigatória configurada, aprovar o deployment depois de
  conferir o commit/escopo; não é necessário habilitar recurso pago.

Na preparação anterior, o GET desse Environment retornou **404** (não encontrado/disponível
à integração); a consulta **apenas de metadados** do repository secret retornou
**403, Resource not accessible by integration**. Nenhum valor foi lido. Portanto,
o proprietário precisava confirmar no painel a existência/configuração. Na
retomada, ele confirmou Environment e Secret JSON presentes; não é necessário
recriá-los ou copiar as senhas para repository secrets. A chave anteriormente
usada não foi lida nem alterada.

### 3. Confirmar configuração e autorizar o escopo, sem enviar valores

Para só conferir configuração, informar que os Secrets estão prontos. Codex poderá
executar preflight **sem chamadas Supabase**. Para autorizar também os testes,
usar uma confirmação explícita, por exemplo:

> Autorizo H01–H04 exclusivamente em noctua-homologacao, com as duas contas fictícias
> configuradas, incluindo login, uma senha incorreta por conta, refresh, consultas,
> logout somente das sessões criadas pelo ensaio e o PATCH contraditório sem linhas
> de H03. Não autorizo alterações acadêmicas, contas, RLS ou produção.

Uma única confirmação pode reunir “Secrets configurados” e essa autorização.
Não enviar JSON, senhas, códigos, tokens, links de recuperação ou chaves no chat.
Se o Environment exigir Review, haverá a aprovação do deployment no GitHub.

## Entrada manual preparada — não foi disparada

O novo `.github/workflows/hosted-auth.yml` é reutilizável; a entrada é o
**android.yml já registrado**, sem modificar main. Ele não roda em push/PR.
`hosted_auth_case=ALL` mantém os quatro casos; `H03` seleciona apenas H03.
`hosted_auth_only=true` executa somente esse job, sem APK, emulador, fixtures SQL,
regressão longa ou release. A autorização tem padrão **false**.

Comandos públicos que o Codex pode executar, após a configuração/autorização:

```bash
# Somente pré-requisitos: NÃO conecta ao Supabase.
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
  --ref sync/academic-complete-0220 -f hosted_auth_only=true

# Somente DEPOIS da autorização específica acima:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
  --ref sync/academic-complete-0220 -f hosted_auth_only=true \
  -f authorize_hosted_h01_h04=true
```

Não é necessário que o proprietário instale CLI: o Codex pode disparar esses
comandos depois dos passos acima. Manter as contas no Environment protegido.
O caller repassa explicitamente somente a chave pública e o Secret JSON por nome,
sem herdar os demais Secrets. Não é necessário duplicar o Secret JSON no
repositório; o Environment do job resolve seu valor. O job tem concorrência separada, sem cancelamento
por um push da regressão comum. Não iniciar várias execuções nem editar a fixture
em paralelo; rate limit/timeout não deve gerar aprovação ou relaxamento de segurança.

## Pré-requisitos e critérios do resultado

| Item | Verificado sem rede | Exige execução real futura |
|---|---|---|
| Projeto/variante | URL fixa sscexzxtqdcsqdlcytum; variante homologacao; guardas AuthEnvironment preservados. JSON não aceita URL/owner alternativo. | Chave correta e comunicação válida. |
| Contas | Duas identidades normalizadas/distintas, e-mails/senhas válidos em formato, exclusão do owner legado. | Confirmação, existência e UUID real retornado pelo servidor. |
| Fixture | ID preenchido e seguro, sem criar registros. | A deve ler exatamente uma linha própria antes da negativa B; ausência não prova isolamento. |
| Autorização | Apenas workflow_dispatch nessa branch; modo preflight não executa Java/HTTP. | Run explícito + flag YES + consentimento H03 no JSON. |
| Resultado | XML sanitizado por lista fechada H01–H04; mensagens privadas nunca copiadas. | Exatamente quatro casos executados/aprovados e processo bem-sucedido; skip/ausência/duplicidade não aprovam o conjunto. |

| Caso | O que o runner realmente cobre | Estado na preparação original (antes de #193) |
|---|---|---|
| H01 | Login confirmado A/B, identidade, consulta user, refresh, senha incorreta. | **NÃO EXECUTADO** |
| H02 | Leitura A positiva da fixture; RPC de estado positiva B; B não lê a linha A conhecida. | **NÃO EXECUTADO** |
| H03 | Helper privado e PATCH contraditório recusados; fixture conferida antes/depois. | **NÃO EXECUTADO** |
| H04 | RPC aceita sessão válida e recusa ausência/JWT inválido/revogação; refresh revogado recusado. Logout usa `scope=local` da sessão do ensaio. | **NÃO EXECUTADO** |

Não revogar sessões dos emuladores; o runner só encerra as sessões novas criadas
por ele. Não muda senha/e-mail/perfil, não cria usuários, não escreve fixtures,
não modifica registros acadêmicos e não executa SQL. JWT realmente expirado,
conta não confirmada, escrita RPC cruzada e auditoria de ACLs de todas as tabelas
continuam provas adicionais, não são preenchidos por simulação nem por esses
quatro casos. A aprovação manual já relatada não aprova automaticamente a API.
Se houver cancelamento/timeout depois do início, o relatório fica indeterminado
(`NOT_REPORTED`/contato não confirmado), não inventa zero requisições nem quatro
aprovações. O logout normal é local às sessões de teste; uma interrupção abrupta
pode deixar uma sessão efêmera ativa, sem tokens nos artefatos. Não revogar sessões
globalmente para “limpar” a execução.

## Validação local da preparação original (antes da correção H03)

- `python3 scripts/test-hosted-auth-checks.py`: **13 aprovados**, zero falhas;
  somente entradas/XML fictícios e arquivos temporários locais. Não chama ADB,
  Gradle, Supabase ou qualquer rede. Inclui regressão do repasse por nome,
  ausência de herança ampla, Environment e restrições de branch/autorização.
- `python3 -m py_compile scripts/hosted-auth-checks.py scripts/test-hosted-auth-checks.py`.
- YAML e guardas da entrada manual conferidos localmente; `actionlint` 1.7.7
  aprovado nos dois workflows alterados (sem ShellCheck/pyflakes).
- Nenhuma mudança em Kotlin/Room/migrações/app oficial/assinatura; nenhuma
  reexecução desnecessária da regressão Android já aprovada em 42740837445.
- Esta preparação/correção não iniciou workflow hospedado nem criou Environment/Secret.
  O repasse corrigido no GitHub ainda exige nova pré-verificação manual.
