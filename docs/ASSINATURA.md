# Assinatura e compatibilidade de atualização — Etapa 1

## Referência comprovada e limite de acesso

O APK original de referência é o artefato `12054798579`, execução [38922054797](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38922054797), código `a157c79c0823f898c63133ac6ff35bf5793b5219`, versão 0.1.0 (`versionCode=1`). Sua assinatura foi verificada no CI original. Metadados públicos imutáveis estão em [baseline/versao-0.1.0.json](baseline/versao-0.1.0.json), e o certificado público DER em `baseline/apk-0.1.0-certificado.der`.

- Pacote: `br.com.diariodeclasse`; banco: `diario.db`; esquema Room: 1.
- SHA-256 do APK: `d7a2dcffc72a25a940d60805fa82f57f49de549728713fcae9dfb03835c87caa`.
- SHA-256 do certificado: `3d813f8da9670fd464e84de267fbf6ac572dafd744b277d85f2fc100687dc6fb`.

**Não tivemos acesso ao celular para identificar qual APK/certificado está instalado.** A chave privada desse APK de referência não foi fornecida nem recuperada nesta execução. O pipeline antigo usava debug keystore temporário do runner, sem preservação configurada. O certificado público não permite recriar sua chave privada.

Nesta etapa a aplicação passa a 0.1.1 (`versionCode=2`) e mantém pacote/banco/esquema. Isso por si só **não garante** instalação por cima: certificado também precisa ser compatível.

## Política de build

- `android.yml` gera **APK debug de teste** e relatório `compatibilidade-baseline.json`. A assinatura debug de um runner novo pode divergir da referência; esses APKs não são anunciados como atualização garantida de instalações anteriores.
- `release.yml` gera **APK com assinatura estável** somente por execução manual, usando chave PKCS12 externa e fingerprint esperado. Sem as configurações, o build falha claramente; não cria chave temporária e não disponibiliza APK unsigned como release.
- A chave é reconstruída apenas em `RUNNER_TEMP`, com modo 0600; não entra no código/artefatos e é removida ao fim. O build compara o certificado com o fingerprint antes de assinar e volta a verificar o APK antes do upload.
- Também é possível compilar release localmente com as mesmas variáveis. A chave estável deve ser guardada pelo responsável em cópias offline protegidas. Não gerar outra chave em cada execução.

### Configuração única pelo responsável

Não é necessário enviar a chave ao chat. Gere/guarde a chave **no seu computador**, com JDK completo, usando prompts interativos para as senhas:

```sh
keytool -genkeypair -storetype PKCS12 -keystore diario-signing.p12 -alias diario -keyalg RSA -keysize 3072 -validity 10000
keytool -list -v -keystore diario-signing.p12 -alias diario
```

Se a chave original estiver disponível, avalie reutilizá-la para manter compatibilidade; não gere chave nova antes dessa verificação. Use a política de senhas do PKCS12 do seu JDK (normalmente a mesma senha de armazenamento/chave). Não coloque senhas na linha de comando ou em arquivos versionados.

Em **Settings → Secrets and variables → Actions**, configure:

| Tipo | Nome | Conteúdo |
| --- | --- | --- |
| Secret | `DIARY_KEYSTORE_BASE64` | Bytes do PKCS12 codificados em base64, preparados localmente sem publicar saída |
| Secret | `DIARY_STORE_PASSWORD` | Senha do armazenamento |
| Secret | `DIARY_KEY_ALIAS` | Alias da chave |
| Secret | `DIARY_KEY_PASSWORD` | Senha da chave |
| Variable pública | `DIARY_CERT_SHA256` | SHA-256 do certificado conferido no keytool, com ou sem dois-pontos |

Restrinja acesso de escrita ao repositório e proteja alterações do workflow que utiliza secrets. É recomendável aprovação de ambiente para releases; a configuração dos responsáveis/revisores depende do proprietário do repositório.

Para build local, o Gradle lê `DIARY_RELEASE_STORE_FILE`, `DIARY_RELEASE_STORE_PASSWORD`, `DIARY_RELEASE_KEY_ALIAS`, `DIARY_RELEASE_KEY_PASSWORD`, `DIARY_RELEASE_CERT_SHA256`. Não há senhas/chaves no repositório. A integração desta execução não tem ferramenta para configurar esses secrets; a assinatura estável permanece **bloqueada até essa configuração externa**. Não confundir o sucesso do debug com validação do release estável.

## Verificar uma atualização sem instalar

Com Android Build Tools disponíveis e ambos os APKs locais:

```sh
python3 scripts/check-apk-update.py antigo.apk candidato.apk --build-tools /caminho/Android/Sdk/build-tools/35.0.0
```

O script verifica as assinaturas, lê pacote/versão e compara certificados, sem instalar/desinstalar. Divergência retorna código 2 e bloqueia a conclusão de compatibilidade. Igualdade de identidade ainda precisa do teste de preservação do banco. A rotação de certificado não é implementada nesta etapa; o verificador exige o mesmo certificado.

Para obter o APK instalado, `adb shell pm path br.com.diariodeclasse` informa o caminho, e `adb pull CAMINHO antigo.apk` o copia localmente. Isso não exporta o banco. Nunca envie um banco do professor para este repositório.

## Quando o certificado diverge

1. Mantenha a instalação antiga. Não desinstale, não limpe os dados e não use ferramentas que contornem a proteção de assinatura do Android.
2. Se a chave original puder ser recuperada de posse legítima do responsável, compare seu certificado antes de usá-la.
3. Caso contrário, para APK **debug/depurável** com USB/ADB autorizado, faça resgate local somente leitura:

```sh
python3 scripts/rescue-debug-data.py --serial IDENTIFICADOR_ADB --output /pasta/nova/privada/resgate-diario
```

4. Guarde o material local; confira integridade e, idealmente, valide `resgate-diario.json` numa instalação separada de teste/outro aparelho com esta versão, confirmando todos os dados. Só depois o responsável pode decidir como migrar para uma instalação com chave estável. O script não desinstala nem toma essa decisão.
5. Se o app não for depurável, ADB não estiver disponível ou o esquema não for reconhecido, o resgate será recusado sem alteração da origem. É um impedimento real; não prometer atualização automática ou recuperação universal.

## O que o CI comprova

O teste de atualização compila o código legado fixado e o candidato com **a mesma chave descartável de teste**, injeta uma fixture v1 sintética no emulador, abre a versão antiga, verifica cada campo das oito tabelas, instala o candidato por cima sem desinstalar e verifica novamente cada campo. Também ensaia o resgate local sintético e compara as oito tabelas.

Esse cenário valida o caminho de atualização **quando o certificado é o mesmo**. Não recupera a chave privada do APK original e não comprova compatibilidade com o celular do professor. Relatórios e resultados ficam nos artefatos do Actions; nenhum banco real ou chave privada é publicado.
