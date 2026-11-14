# APK de homologação funcional — execução #206

14/11/2026, America/Sao_Paulo. Branch `sync/academic-complete-0220`. Nenhuma funcionalidade, versão de código, workflow, chave ou regra acadêmica foi alterada para gerar este APK.

## Origem e execução

- Código exato: `4346d55c192e85b64e2c9322b683339a1601ae86`.
- [GitHub Actions #206 / 43614108749 — aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43614108749).
- Workflow existente `homologacao.yml`, acionado pelo dispatcher registrado `android.yml`: `homologacao_only=true`, `hosted_auth_only=false`, `h05_only=false`, `h12_only=false`. Os demais modos permaneceram desativados. Nenhum novo sistema de distribuição.
- Validada a configuração sem fallback, executados `testDebugUnitTest`, `testHomologacaoUnitTest`, `assembleHomologacao` e `scripts/validate-homologacao.py` sobre o APK.
- Antes da compilação, HEAD/branch conferidos, `git diff --check` sem erros; nenhuma alteração local em arquivos versionados. A pasta `.agents/` preexistente foi preservada sem inclusão no commit.
- Confirmados no fonte: P/F repetido exclui a ocorrência em transação e mantém tombstones; busca/ordenação e atividades independentes; automático por série bloqueado; manual limitado ao ano letivo da origem. Os nomes não são usados para deduzir série.

## Download e identidade

[Baixar `noctua-homologacao-paralela-debug`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43614108749/artifacts/14121343254). O ZIP contém `app-homologacao.apk`, `homologacao-identity.json` e `homologacao-apk.sha256`.

| Campo | Valor verificado |
|---|---|
| Nome | NOcTuA Homologação |
| applicationId | `br.com.diariodeclasse.homologacao` |
| Versão | `0.22.0-dev-e2-homologacao` |
| versionCode | 45, preservado do commit; é uma nova compilação, não incremento de versão |
| Arquivo | `app-homologacao.apk` |
| Tamanho do APK | 12.987.470 bytes |
| SHA-256 do APK | `513a6a93bdb08599f95491b9eb397ad1588ac59c511e7945d099ff1bc1929580` |
| SHA-256 do ZIP | `1e580f74e212b9ef72126b9095d5be2755643252c9741d6aac63715246d05ae1` |
| Endpoint | `https://sscexzxtqdcsqdlcytum.supabase.co` |
| Callback | `br.com.diariodeclasse.homologacao://auth/callback` |
| Provider | `br.com.diariodeclasse.homologacao.exports` |
| Assinatura | Android Debug, verificada por apksigner; não é o certificado permanente de produção |
| Certificado SHA-256 | `9864b5240c60d2557b77d86d8a429358d32c06c0a5d77de16915ced67dc1b21a` |

O APK foi baixado do Actions, e seu hash coincide com o SHA publicado e com o relatório de identidade da CI. AAPT confirmou package, label e versão. Apksigner confirmou a assinatura. A análise offline do DEX confirmou o endpoint de homologação, ausência da referência/chave de produção e dos marcadores proibidos de chave privada, segredo administrativo e aplicação de testes. Os valores de chaves não foram impressos ou registrados.

A URL/ref são fixas na variante, e a chave publishable vem do Secret de build já existente. A variante não aceita fallback para produção nem o override HTTP das fixtures. A configuração passou na guarda de build e no validador do APK; esta execução não fez login ou requisição ao Supabase para revalidar a associação da chave opaca ao projeto.

`sharedUserId` ausente, Auto Backup Android desativado e cleartext bloqueado. UID/diretório/Keystore próprios do pacote paralelo; isolamento por conta preservado. Nenhum dado ou sessão do aplicativo oficial é importado automaticamente.

## Testes

| Variante JVM | Casos | Aprovados | Falhas | Condicionais não executados |
|---|---:|---:|---:|---:|
| Debug | 309 | 304 | 0 | 5 |
| Homologação | 309 | 304 | 0 | 5 |

São os mesmos cenários compilados para duas variantes, não 608 cenários distintos. Os condicionais são H01–H04 hospedados e SupabaseLiveAuthTest, sem credenciais/autorização nesta execução. [XMLs e relatórios da CI](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43614108749/artifacts/14120949709).

A regressão Android funcional selecionada do código permanece comprovada na [CI 43597997812](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43597997812): 59 testes Android, três repetições com fonte 150%, dez fases HTTP/Room em dois AVDs e nove verificações HTTP locais. O commit `4346d55` acrescentou apenas documentação após esse código validado. A execução #206 não instalou o APK nem executou novamente esses testes Android na variante de homologação.

## Compatibilidade de assinatura — atualização bloqueada para a referência antiga

O APK anterior de homologação da execução `42438137774` foi baixado novamente; SHA do ZIP e do APK coincidem com a documentação histórica. Seu certificado também foi verificado por apksigner:

- Anterior: `b6dd351f4e3e1e633c7447e02f449316779e8ff0b14707f9981c80e363d92189`.
- Atual: `9864b5240c60d2557b77d86d8a429358d32c06c0a5d77de16915ced67dc1b21a`.

**Certificados diferentes: o APK atual não atualiza aquela instalação de homologação.** O workflow existente usa a chave Debug disponível em cada execução, sem continuidade garantida. Não foi tentada atualização em aparelho, não foi recuperada/substituída chave privada e a assinatura de produção não foi utilizada nem modificada. Outra instalação de homologação precisa ter seu certificado real comparado; não presumir compatibilidade somente por nome, package ou versionCode.

## Instalação segura e teste funcional

1. Use **outro emulador novo ou outro dispositivo de teste sem `br.com.diariodeclasse.homologacao` instalado em nenhum perfil**. Um perfil novo no mesmo aparelho não elimina a incompatibilidade de assinatura de um pacote já instalado. Preserve o aplicativo oficial e os dois emuladores existentes. Não desinstale, limpe dados ou restaure backups reais para contornar incompatibilidade de assinatura.
2. Baixe o artefato correto, extraia o APK e confira o SHA-256 acima. Para instalação inicial por ADB, indique explicitamente o dispositivo novo e não use `-r`:

   ```bash
   adb -s SERIAL_DO_NOVO_EMULADOR install app-homologacao.apk
   ```

   Se já existir homologação no destino, interrompa e escolha outro ambiente. Não corrigir `INSTALL_FAILED_UPDATE_INCOMPATIBLE` removendo a instalação anterior.
3. Abra **NOcTuA Homologação**, confira o banner e use somente as contas fictícias já autorizadas no projeto de homologação. As autorizações locais experimentais de sincronização continuam exigidas. Teste P/F → segundo toque vazio, busca/ordenação e seleção manual entre séries do mesmo ano. A seleção automática por série permanece indisponível; 2027 não deve aparecer nos destinos quando a origem é de 2026. Não usar contas, turmas ou backups reais.

Nenhuma instalação foi executada nesta entrega. A homologação física do novo APK permanece pendente. Não há autorização implícita para testar carga hospedada, alterar contas/RLS/migrações ou modificar `noctua-auth`.

[Relatório complementar de identidade, certificados, hashes e contagens](evidence/APK_HOMOLOGACAO_FUNCIONAL_206.json). O relatório de identidade original também está no artefato do Actions. O código do APK é `4346d55`; alterações documentais posteriores não mudam esse artefato.
