# H03 — diagnóstico local após a execução hospedada #193

Registro: **12/11/2026, America/Sao_Paulo**. Branch `sync/academic-complete-0220`.
Esta rodada não realizou chamadas Supabase nem disparou workflows. Nova execução
hospedada exige **nova autorização explícita**.

## Evidência histórica e limite do diagnóstico

A [execução #193](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43345594778)
executou o código `52f4d9b4c9ef5a55242fc3b55150eb3cb1ae8d47`:
H01, H02 e H04 passaram; H03 falhou, sem casos ignorados entre os quatro.
O [artefato sanitizado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43345594778/artifacts/14002020780)
contém apenas os resultados, sem a exceção/etapa. Logs Gradle e XML brutos foram
removidos pelo job, como previsto. Não há evidência preservada para afirmar qual
foi a **primeira** falha de H03, nem qual status HTTP o helper retornou em #193.
Não se presume que o PATCH chegou ao servidor ou que a negativa de permissão passou.

**Defeito confirmado localmente:** no Java 17/Temurin, usado no workflow,
`HttpsURLConnection.setRequestMethod("PATCH")` lança
`java.net.ProtocolException: Invalid HTTP method: PATCH`. Foi reproduzido sem
`connect()`, envio, DNS ou acesso ao Supabase. A linha antiga de `request()` era
incompatível com PATCH; H03 a alcançaria depois da leitura da fixture e do helper.
Isso confirma uma causa de falha no cliente, sem inventar o stack trace perdido
ou excluir uma eventual falha anterior no servidor. Java 21 também reproduziu.

## Correção restrita aos testes

- `HostedHttpsTransport` usa **OkHttp 4.12.0**, compatível com o classpath Android
  dos testes JVM. As APIs `java.net.http` não estão nesse classpath. OkHttp,
  MockWebServer e okhttp-tls são dependências **testImplementation**, não entram
  no APK nem alteram `SupabaseAuthClient` ou transporte acadêmico de produção.
- Endpoint hospedado fixo `https://sscexzxtqdcsqdlcytum.supabase.co`; HTTPS,
  verificação TLS/hostname padrão, sem redirecionamentos HTTP/HTTPS e sem retry
  automático. Conexão: 15 s; leitura: 20 s; chamada completa, incluindo corpo: 20 s.
- Resposta de sucesso limitada a **262.144 bytes**, inclusive se chunked; corpos
  de erro/redirecionamento não são retornados. Respostas e falhas têm descrição
  redigida, sem causa privada. Não há logger/interceptor de credenciais.
- Certificado de confiança customizado só é permitido no servidor TLS local
  descartável `localhost`; não se aplica ao projeto hospedado.
- O filtro continua exigindo **revisão positiva** e o mesmo predicado
  `(revision.eq.<revisão>,revision.lt.0)`. Essas condições contraditórias impedem
  alteração de linhas. IDs, titularidade, chave da fixture e corpo permanecem iguais.
- Critérios mantidos: fixture própria única e legível; helper recusado por
  401/403/404; PATCH recusado por **401/403**; fixture igual antes/depois.
  Um PATCH 200/204 continua falhando, mesmo que não altere linhas.
- Nenhuma alteração em SQL, RLS, permissões, contas, dados, Room, assinatura ou
  código em `app/src/main`.

## Diagnósticos sanitizados

H03 identifica as etapas INPUT, LOGIN, FIXTURE_BEFORE, REVISION, HELPER, PATCH,
FIXTURE_AFTER e COMPARE. O XML privado recebe apenas mensagens fixas como o
seguinte **exemplo de formato, não resultado hospedado**:

```text
H03_DIAG stage=PATCH status=403 code=HTTP
```

O resumo público admite somente etapas/códigos enumerados e status HTTP numéricos
(ou `null` quando não recebidos). Nunca copia mensagens arbitrárias, URLs, filtros,
fixture IDs, nomes, e-mails, senhas, tokens, respostas ou causas de exceções.
`h03_diagnostics` no `result.json` e o Job Summary registram os eventos permitidos.
Logs/relatórios brutos continuam privados, sem cache/publicação, e são removidos.
Os testes Python incluem mensagens privadas artificiais para verificar a redação.

## Validação realmente executada

| Verificação | Resultado |
|---|---|
| Reprodução do cliente antigo em Java 17 e 21, sem conexão | ProtocolException confirmada |
| Testes TLS locais `HostedHttpsTransportTest`, variante debug | **10 aprovados, 0 falhas, 0 ignorados** |
| Mesmos testes TLS, variante homologacao, sem chave/contas/opt-in | **10 aprovados, 0 falhas, 0 ignorados** |
| Suíte JVM debug completa | **298 casos: 293 aprovados, 5 condicionais ignorados, 0 falhas** |
| `scripts/test-hosted-auth-checks.py` | **16 aprovados, 0 falhas** |
| actionlint 1.7.7, dois workflows, sem ShellCheck/pyflakes | Aprovado |
| py_compile e git diff --check | Aprovados |
| homologacaoRuntimeClasspath | Novas dependências HTTP/fixtures ausentes |

Os 10 casos de transporte estão incluídos na suíte de 298; sua repetição na outra
variante não representa novos testes distintos. Os cinco condicionais são H01–H04
e `SupabaseLiveAuthTest`: **não aprovados nesta rodada**. Não houve teste Android
instrumentado nem instalação, pois a mudança está no executor JVM.

Foram testados PATCH real sobre TLS local e seus cabeçalhos/filtro, corpo privado
não retornado, revisão inválida, bloqueio de redirecionamentos, resposta chunked
acima do limite, prazo após headers/corpo e antes de headers, GET/JSON, rejeição de
HTTP/produção/outro projeto e diagnósticos sem informações privadas.

No executor, o Gradle precisou de permissões de socket local, ANDROID_USER_HOME
escrito em /workspace e proxy/CA fornecidos pelo ambiente para baixar dependências.
A CA foi importada somente numa **cópia temporária** do truststore de build; não foi
alterado o truststore do aplicativo/projeto ou desabilitada a validação TLS.
Erros iniciais de ambiente/classpath foram resolvidos antes dos resultados acima.

## Próxima execução: somente H03, ainda não autorizada

A entrada manual aceita `hosted_auth_case=ALL` (padrão preservado) ou `H03`.
Não aceita filtro Gradle arbitrário. A seleção H03 executa apenas o método H03,
com login/fixture/helper/PATCH/consulta final e logout local de sua sessão. Mantém
Environment, branch, autorização YES, contas confirmadas e consentimento no JSON.

Após nova autorização, iniciar **nova execução**, nunca rerun do SHA antigo:

```bash
# NÃO executado nesta rodada. Somente após nova autorização explícita.
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
  --ref sync/academic-complete-0220 -f hosted_auth_only=true \
  -f hosted_auth_case=H03 -f authorize_hosted_h01_h04=true \
  -f homologacao_only=false
```

O artefato identifica a seleção. H01/H02/H04 ficam **NOT_EXECUTED**, e
`all_four_passed` permanece **false**, mesmo se H03 passar. O status será
`PASSED_SELECTED_CASES` somente se H03 realmente executar/aprovar, com processo
bem-sucedido e nenhum skip; ausência, resultado extra ou contagem incoerente
bloqueia aprovação. Resultados anteriores permanecem históricos, não são copiados
como execuções do novo commit. Em caso de nova falha, a etapa/status sanitizados
orientarão o diagnóstico. Não alterar permissões para aprovar o teste.

A correção está pronta para essa repetição controlada após autorização, mas H03
hospedado continua **pendente**. Esta correção não homologa produção nem promove main.
