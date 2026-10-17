# Noctua: criar e conservar a assinatura permanente

Este procedimento é executado **pelo proprietário no próprio computador**. Não envie arquivos de chave, senhas, base64 ou backups escolares ao chat, a issues ou ao repositório. O projeto não cria a chave permanente. O CI gera chaves descartáveis apenas nos testes automatizados, que não servem à distribuição.

## 1. Prepare o computador e crie a chave uma única vez

Instale Android Studio com JDK 17 (ou um JDK completo 17). No Android Studio, o `keytool` também costuma estar em `jbr/bin`. Abra o Terminal ou PowerShell e confirme `keytool -help`. Se o comando não existir, use o caminho completo do `keytool` do JDK; no PowerShell, um executável com caminho entre aspas precisa de `&` antes dele. A geração da chave funciona sem Internet depois da instalação do JDK.

Crie uma pasta privada **fora da pasta do projeto**, por exemplo `Documentos/ChavesNoctua`. Abra o terminal nessa pasta. Confira se ainda não existe uma chave permanente do Noctua sob sua guarda; se existir, reutilize-a em vez de gerar outra. As chaves debug dos APKs antigos não são recuperáveis a partir do certificado público.

Execute o comando abaixo em uma só linha:

```sh
keytool -genkeypair -storetype PKCS12 -keystore noctua-release.p12 -alias noctua -keyalg RSA -keysize 3072 -validity 10000
```

No Windows, se `keytool` não for encontrado e o Android Studio estiver no local padrão, substitua `keytool` no início do comando por `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`. Ajuste o caminho se a instalação for diferente.

O comando pede a senha no próprio computador: escolha uma senha forte e guarde-a em um gerenciador de senhas. A digitação pode não aparecer na tela, o que é normal. Responda às perguntas do certificado com a identificação do proprietário/organização, sem dados de alunos, e confirme. Se o JDK usar a mesma senha para arquivo e chave PKCS12, use essa mesma senha nos dois secrets de senha abaixo. Não acrescente senhas à linha de comando.

O arquivo `noctua-release.p12` contém a chave privada. **Não execute o comando novamente para cada versão.** Validade de 10.000 dias é aproximadamente 27 anos; planeje a continuidade antes do vencimento. Perder a chave/senha pode impedir futuras atualizações com a mesma assinatura.

## 2. Anote o certificado público e guarde cópias

```sh
keytool -list -v -storetype PKCS12 -keystore noctua-release.p12 -alias noctua
```

Digite a senha quando solicitado. Anote o alias `noctua`, a validade e a linha **SHA256** da seção do certificado. Essa impressão digital é pública e tem 64 dígitos hexadecimais, eventualmente separados por `:`. Copie somente os dígitos/separadores, sem o rótulo `SHA256:` ou espaços. Ela não é o SHA-256 do arquivo APK, que muda a cada versão.

Guarde pelo menos duas cópias da chave em locais independentes e protegidos, por exemplo dois dispositivos criptografados, sendo um offline. Guarde a senha em gerenciador protegido, separadamente do arquivo quando possível. Em cada cópia, execute `keytool -list` para confirmar que a senha abre o arquivo e que o SHA256 é idêntico. Conserve também um registro do alias, da impressão digital e dos APKs releases publicados.

O `.gitignore` bloqueia extensões usuais de chaves, mas isso não é substituto para mantê-las fora da pasta do projeto. Base64 é uma codificação reversível, **não é criptografia**.

## 3. Configure o GitHub uma única vez

Entre como proprietário em [Settings do repositório](https://github.com/italocesarfr-sketch/CHATGPTCODEX/settings). Em **Environments**, crie/abra o ambiente chamado **release**. Se a sua conta/plano permitir, configure aprovação manual por responsável e limite às branches de distribuição. Sem regras de proteção, o nome do ambiente por si só não cria aprovação. Restrinja também quem pode alterar o código e os workflows; quem controla o workflow pode tentar acessar seus secrets.

Em **Secrets and variables → Actions**, configure os quatro **Repository secrets** e a **Repository variable** abaixo. Também é possível usar secrets do ambiente `release`, mas o local mais simples é a seção Actions do repositório. Não use variáveis públicas para as senhas ou para a chave.

| Tipo | Nome exato | Valor |
| --- | --- | --- |
| Secret | `DIARY_KEYSTORE_BASE64` | Arquivo `noctua-release.p12` em base64 |
| Secret | `DIARY_STORE_PASSWORD` | Senha do arquivo PKCS12 |
| Secret | `DIARY_KEY_ALIAS` | `noctua` (ou alias efetivamente usado) |
| Secret | `DIARY_KEY_PASSWORD` | Senha da chave; normalmente a mesma do arquivo PKCS12 |
| Variable | `DIARY_CERT_SHA256` | SHA256 público obtido no passo 2, com ou sem `:` |

Use **New repository secret** para cada uma das quatro linhas Secret; na aba **Variables**, use **New repository variable** para a última linha. Os nomes `DIARY_*` são mantidos por compatibilidade; o aplicativo já se chama Noctua. Para preparar o primeiro secret **localmente**, escolha o seu sistema:

**Windows / PowerShell**, dentro da pasta privada:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) 'noctua-release.p12'))) | Set-Clipboard
```

Isso copia a codificação para a área de transferência sem mostrá-la no terminal. Cole somente no campo do secret `DIARY_KEYSTORE_BASE64` e salve. Depois limpe a área de transferência e seu histórico/sincronização, se habilitados. Não cole no chat.

**Linux / macOS**, dentro da pasta privada:

```sh
umask 077
python3 -c "import base64,pathlib; pathlib.Path('noctua-release.base64').write_bytes(base64.b64encode(pathlib.Path('noctua-release.p12').read_bytes()))"
```

Abra esse arquivo local em um editor, copie todo o conteúdo e cole apenas no secret do GitHub. Após salvar o secret, remova o arquivo base64 e limpe a área de transferência/histórico. Ele é tão sensível quanto a chave. **Não apague o PKCS12 original nem suas cópias de segurança.**

O GitHub conserva uma cópia cifrada em Secrets e entrega uma cópia ao runner durante a compilação. O workflow decodifica-a somente no diretório temporário, limita permissões e remove-a ao terminar. Não inclua chaves ou senhas nos artefatos. O usuário é responsável pela guarda do original e pela proteção da conta (incluindo autenticação em dois fatores). Este chat não configura nem recebe esses valores.

## 4. Gere a primeira release permanente

Abra [Actions → Noctua — APK com assinatura estável](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/workflows/release.yml). Toque em **Run workflow**, selecione `main` e confirme. Se houver revisão do ambiente, aprove-a após conferir o commit que será compilado.

Espere a execução terminar com sucesso. Abra a execução e baixe **noctua-assinatura-estavel** em Artifacts. Extraia o ZIP: `app-release.apk` é o APK; o `.sha256` é a conferência do arquivo. **Se falhar, não substitua a chave e não desative a verificação**: confira os nomes, alias, senhas, base64 e impressão digital. A release é bloqueada se faltarem valores ou se o certificado não conferir. O workflow não usa debug como fallback.

O artefato dura 30 dias: guarde uma cópia do APK e do checksum. Não é criada automaticamente uma publicação na página Releases. Nenhuma primeira release permanente foi gerada na implementação deste incremento; isso depende dos seus secrets e da execução acima.

## 5. Confira o APK antes de instalar

No Android Studio, abra **SDK Manager** (no menu Tools ou More Actions), confira o campo **Android SDK Location** e instale **Android SDK Build-Tools 35.0.0**, se necessário. O programa `apksigner` fica dentro desse SDK em `build-tools/35.0.0` (no Windows, `apksigner.bat`). Dentro da pasta que contém o APK extraído:

```sh
/CAMINHO/Android/Sdk/build-tools/35.0.0/apksigner verify --verbose --print-certs app-release.apk
```

No Windows, use o caminho do `apksigner.bat`; no PowerShell, acrescente `&` antes de um caminho entre aspas. O comando deve terminar sem erro e mostrar **Signer #1 certificate SHA-256 digest** igual ao SHA256 do passo 2. `DOES NOT VERIFY` ou certificado diferente: não instale.

Para conferir o arquivo, compare o SHA-256 com o `.sha256` do mesmo artefato: no Windows, `Get-FileHash .\app-release.apk -Algorithm SHA256`; Linux, `sha256sum app-release.apk`; macOS, `shasum -a 256 app-release.apk`. Esse valor é diferente da impressão digital do certificado.

Para uma versão seguinte, compare também os dois APKs com a ferramenta somente leitura do projeto:

```sh
python3 scripts/check-apk-update.py antigo-release.apk novo-release.apk --build-tools /CAMINHO/Android/Sdk/build-tools/35.0.0
```

Exige assinaturas válidas, pacote igual, certificado igual e `versionCode` maior. Não instala nem apaga nada. Os testes de preservação do banco continuam necessários nas atualizações. Rotação de chave não está implementada.

## 6. Transição do debug 0.4.0 para a primeira release

O nome Noctua não cria outro app: o pacote continua `br.com.diariodeclasse`. Entretanto, uma nova chave permanente **não atualiza diretamente** o APK debug antigo com outro certificado. Não desinstale o aplicativo para contornar a mensagem do Android sem antes concluir a proteção dos dados.

1. No aplicativo antigo, espere a indicação de salvamento e use **Backup → Criar backup completo**. Salve em pasta local acessível fora do armazenamento privado do app. Faça uma segunda cópia protegida. Não publique dados escolares no GitHub.
2. Ainda mantendo a instalação antiga, selecione o arquivo em **Selecionar backup para restaurar** e confira a prévia/data/contagens, sem confirmar substituição. Isso valida formato e integridade, mas não comprova sozinho a restauração inteira. Cancele a prévia.
3. Preferencialmente teste a restauração numa instalação release em outro aparelho/emulador de sua confiança. Digite `RESTAURAR` somente nesse destino e confira turmas, alunos, bimestres, chamadas antigas, notas e seleção. Não envie backups reais ao CI. Se não conseguir validar o backup, mantenha a instalação antiga e solicite suporte. A cópia privada anterior à restauração não sobrevive à desinstalação.
4. **Somente depois de confirmar um backup válido preservado e conferido, ou de confirmar explicitamente que todos os registros atuais são descartáveis**, o proprietário pode escolher desinstalar a versão debug e instalar a release. A antiga autorização sobre dados descartáveis não vale automaticamente para novos registros da 0.4.0. A desinstalação apaga o banco e arquivos privados; o backup externo é essencial quando houver dados a manter.
5. Instale `app-release.apk` (autorize a fonte de instalação nas configurações Android se solicitado) e restaure o backup externo pelo botão Backup. Confira novamente os mesmos registros e guarde as cópias. Em caso de falha, não apague o backup nem use o novo app para novos registros até resolver.

Se não houver acesso ao backup no app antigo, existe o resgate **somente leitura** `scripts/rescue-debug-data.py` documentado em [ASSINATURA.md](ASSINATURA.md). Ele depende de ADB autorizado e APK depurável; não é uma recuperação universal. Nunca execute `scripts/ci/` em um celular real.

## 7. Todas as próximas versões

Conserve o mesmo arquivo PKCS12, alias, senhas e SHA256 configurados. Não gere outra chave para a próxima versão. O desenvolvimento deve manter o pacote e incrementar `versionCode` (este incremento usa 7), testar atualização com dados fictícios e aplicar somente migrações explícitas quando necessárias. Faça backup antes de instalar uma atualização.

Execute novamente o mesmo workflow release e verifique o certificado como no passo 5. Um APK de mesma assinatura com versão maior pode atualizar a instalação release preservando o armazenamento, sujeito à compatibilidade do banco testada naquela atualização. Instalar usando a opção **Atualizar**, sem desinstalar ou limpar dados. Nunca instalar um debug sobre a release por pressuposição de compatibilidade.

Se chave, senha ou certificado não conferirem, pare e recupere suas cópias. Não troque secrets por uma chave recém-gerada para resolver uma falha. Se a chave for comprometida/perdida, procure orientação específica: este projeto não fornece rotação de assinatura nem promete uma atualização automática nessa situação.
