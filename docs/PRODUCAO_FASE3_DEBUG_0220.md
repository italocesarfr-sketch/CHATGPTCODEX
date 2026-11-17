# Produção — Fase 3: APK debug e primeiro ensaio isolado

Data: 17/11/2026, America/Sao_Paulo.
Branch: `temp/academic-production-phase1`.
Referência compilada: `5a3ca2cf1ca89bac0d3dc8120595a6599ffccc94`.
O código de aplicação nessa referência é o de `ac56867`; os commits posteriores
até essa referência alteram apenas documentação.

**Escopo: preparar o APK e o roteiro. Nenhuma sessão ou operação hospedada foi
iniciada nesta tarefa. Não instalar no celular real nem nos emuladores existentes.**

## 1. Estado informado pelo proprietário

- A–E implantado em produção `noctua-auth`, `mpsgdchpgyxoqowfjoqm`;
  16 tabelas com RLS e auditorias de segurança aprovadas.
- Conta fictícia **Aron Danone Teste** confirmada e autorizada individualmente
  (`allowed=true`), com uma turma v1, sem registros v2 ou ativação acadêmica anterior.
- O aplicativo oficial 0.22.0/c45 no celular real contém dados escolares importantes
  e permanece intocado. Sua conta legada não será migrada neste ensaio.

Esses pontos são relato do proprietário, não consulta independente desta tarefa.
Não reaplicar pacote A–E, guardas, H13 ou PT409. Não alterar SQL, allowlist ou Auth.

## 2. Variante e identidade

Usado o build existente **debug**, não `homologacao`, sem criar flavor, chave
substituta de produção, nova funcionalidade ou incrementar versão:

| Item | Configuração |
| --- | --- |
| applicationId / callback scheme | `br.com.diariodeclasse` |
| Versão | `0.22.0-dev-e2` / versionCode **45** |
| BuildConfig | `DEBUG=true`, `HOMOLOGACAO=false`, `ACADEMIC_PRODUCTION=false` |
| Supabase | `https://mpsgdchpgyxoqowfjoqm.supabase.co` |
| Public key | Publishable pública de produção já existente; nenhuma chave administrativa |
| Assinatura | Debug descartável gerada no diretório privado da compilação; não é a permanente |
| Destino permitido | Dois AVDs novos, vazios e descartáveis, sem aplicações prévias do proprietário |

**Este APK não é paralelo no mesmo aparelho.** Compartilha o package e callback do
oficial, mas usa outro certificado. Nunca tentar instalá-lo sobre o oficial, remover
o aplicativo existente ou contornar `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

`AuthConfig`, `SupabaseAuthClient`, `SupabaseClassSync` e `SupabaseAcademicSync`
usam a configuração do build. O construtor padrão do sincronizador acadêmico usa
o endpoint Auth; não foi fornecido override de teste. O debug mantém a exceção
local existente de HTTP exclusivamente para `10.0.2.2`; produção continua HTTPS,
sem seguir redirecionamentos. Não usar gateways/proxies que registrem credenciais.

`academicTestEligible` exige `(DEBUG || ACADEMIC_PRODUCTION)`, workspace não legado
e usuário diferente do proprietário legado fixado no código. A sessão válida,
titularidade, allowlist e escopo continuam exigidos pelo servidor. Debug **não**
autoriza qualquer conta a sincronizar e não permite adotar o banco real do proprietário.

## 3. Compilação e evidências

Somente `:app:assembleDebug` e `:app:testDebugUnitTest`, em modo offline Gradle;
variáveis de assinatura release e sondas hospedadas removidas do processo. Não
executado workflow, caminho de assinatura oficial ou task release.

O ambiente tinha SDK 35 e cache Gradle 8.9. O primeiro build encontrou um runtime
Java sem `jlink`; foi preparado JDK completo Temurin 17 em diretório local, com
SHA-256 do arquivo conferido contra o metadado oficial. Nenhuma mudança no projeto
foi necessária. A permissão de rede do processo permitiu sockets locais Gradle/testes;
o Gradle permaneceu `--offline`, sem credenciais hospedadas.

Identidade, hash do APK, contagens e verificações efetivas:
[evidence/PRODUCAO_FASE3_DEBUG_0220.json](evidence/PRODUCAO_FASE3_DEBUG_0220.json).
Os arquivos para entrega ficam em `build/phase3/entrega/`, sem keystore, senha,
tokens, logs de sessão, APK de instrumentação ou dados escolares.

| Resultado efetivo | Evidência |
| --- | --- |
| APK gerado | `build/phase3/entrega/noctua-debug-producao-c45-somente-avd.apk` — **13.020.722 bytes** |
| SHA-256 do APK | `aba6c3b2d66b942253a4fa75ce3ebe800e29d4b7ac6c4e406f8a58c2452c4da2` |
| Certificado público debug SHA-256 | `2f0af2d3f03c506bd3e9b17de97290a0c102b48fe09dce6349c7fd88506c154b` — diferente do permanente |
| Assinatura e conteúdo | `apksigner verify`: válido, um assinante, esquema v2; `validate-auth-apk.py`: aprovado; endpoint/constantes debug e marcadores proibidos também conferidos no APK |
| JVM | **318 casos / 44 suítes: 313 aprovados, 5 ignorados, 0 falhas, 0 erros** |
| Ignorados | Quatro H01–H04 da variante hospedada e uma sonda Auth legada; não foram executados nem aprovados |
| Compilação bem-sucedida | 1 min 55 s no comando final; sem mudança de código |

ZIP opcional: `build/phase3/noctua-debug-producao-c45-somente-avd.zip`, contendo
somente APK, checksum, identidade e aviso de instalação. SHA-256 do ZIP:
`421d2d4338e1b362eaec6d12b0579f394a26a564436a13598d2188105bce2bde`.
Os binários ficam locais para entrega; não foram adicionados ao Git nem publicados
como release. O commit desta preparação contém documentação/evidências sanitizadas.

Sem emulador, imagem de sistema ou `/dev/kvm` neste executor: não se declarou
teste Android instrumentado, instalação, captura ou teste hospedado aprovado.
Os testes condicionais/ignorados ficam separados dos aprovados. As guardas de
Room/consentimento foram revisadas por código; não confundir com prova Android
executada nesta rodada.

## 4. Criar e instalar somente em dois AVDs novos

No Android Studio/Device Manager, criar **dois novos dispositivos**, por exemplo:

- `noctua-prod-fase3-A-20261117`;
- `noctua-prod-fase3-B-20261117`.

Usar imagem Android API 35 compatível com o host (minSdk do APK é 26), armazenamento
novo e sem importar snapshots, backups ou contas de outros aparelhos. Se o nome
já existir, escolher outro nome novo; **não** reutilizar, limpar, apagar ou reinstalar
nos emuladores anteriores. Não é necessário login Google.

Antes de instalar, identificar o serial específico de A. Exemplo, se A for
`emulator-5554`:

```bash
adb -s emulator-5554 shell getprop ro.kernel.qemu
adb -s emulator-5554 emu avd name
adb -s emulator-5554 shell pm path br.com.diariodeclasse
adb -s emulator-5554 shell pm path br.com.diariodeclasse.homologacao
```

Exigir `ro.kernel.qemu=1`, nome do AVD novo esperado e **ausência** dos dois packages.
Se qualquer condição divergir, parar. Não usar `adb install` sem `-s`, `-r`,
`uninstall`, `pm clear` ou wipe-data.

Depois dessas verificações, instalar o APK entregue **somente em A**:

```bash
adb -s emulator-5554 install noctua-debug-producao-c45-somente-avd.apk
```

Para B, repetir as verificações usando seu próprio serial/nome antes de instalar
o mesmo arquivo. Esses comandos são roteiro para o proprietário, não foram
executados pelo Codex. Não abrir o aplicativo online nem autenticar sem autorizar
a etapa hospedada correspondente. Manter B sem login até A concluir E.

## 5. Primeiro ensaio em A — separar os consentimentos

Antes da transição, confirmar que outros dispositivos da **conta fictícia** não
possuem operações locais pendentes ou conflitos a revisar e interromper edições
concorrentes nela durante a transição. Um AVD vazio não prova ausência de pendências
nos outros dispositivos. Não mexer no aplicativo real para produzir essa prova;
se houver dúvida, interromper a transição e esclarecer o estado da conta de teste.

O proprietário digita credenciais da conta fictícia na interface do novo AVD.
Não compartilhar senha, e-mail, UUID, link de callback, tokens ou dump/logcat. Usar
as credenciais existentes, sem criar usuário ou recuperar senha nesta preparação.

| Etapa futura, somente após autorização correspondente | Ação pela interface | Conferência / efeito |
| --- | --- | --- |
| Login + recuperação v1 | Entrar como Aron; abrir menu da conta → **Sincronização de turmas • teste**; usar **Ativar teste nesta conta**, sem criar/editar/inscrever novas turmas | Cria controle local; em A vazio não há operações locais a enviar. O job v1 consulta a turma existente. Aguardar **Turmas sincronizadas** e conferir turma e quatro bimestres. Não é ativação v2 |
| Consentimento B | **Testar turmas e alunos** → conferir aviso, marcar somente dados fictícios → **Iniciar teste** | RPC legítima verifica inventário/sequence v1 e cria protocolo v2 junto com o seed da turma/bimestres existentes. Preserva IDs, versões e recibos v1; os clientes v1 dessa conta deixam de sincronizar |
| Consentimento C | Depois de B sem pendências/conflitos: **Incluir notas e chamadas no teste** → confirmar dados fictícios → **Ampliar teste** | Autoriza escopo C e inclui dados locais correspondentes se existirem |
| Consentimento D | Depois de C consistente: **Incluir diário e históricos no teste** → confirmar → **Ampliar teste** | Autoriza D e mantém validações de históricos, auditorias e movimentações |
| Consentimento E | Depois de D consistente: **Testar recuperação de acervos maiores** → confirmar → **Ampliar teste** | Autoriza E; paginação e decisões existentes, sem pular etapas |

O primeiro login pode mostrar **Ativação inicial necessária**: é o resultado seguro
para uma conta ainda em v1, não permissão para ativação automática. Abrir o painel
não autoriza clicar nos consentimentos seguintes.

Na fase v1, não criar outra turma com o mesmo nome nem usar **Participar** para
recriar o cadastro. Se não recuperar a turma ou houver diferença/conflito, parar.
A primeira conferência deve preservar turma, bimestres e IDs; igualdade visual
sozinha não comprova IDs. Não mostrar identificadores privados em relatórios
públicos; guardar a comparação localmente no aparelho de teste.

**A preparação atual não autoriza executar nenhuma linha da tabela hospedada.**
Login/consulta usam Auth e status/pull; a transição B e os consentimentos C/D/E
escrevem protocolo, seed e metadados acadêmicos no servidor. Precisam de autorização
específica, não são testes somente leitura. Não usar SQL direto, helpers privados,
UPDATE de escopo ou alteração de allowlist para avançar.

Se surgir erro, conflito, SETUP, sessão recusada ou condição inesperada, preservar
os dados/filas, registrar apenas etapa/mensagem sanitizada e parar. Não reexecutar
consentimentos às cegas, corrigir escopo por SQL ou aplicar candidata PT409.

## 6. Segundo AVD e sincronização automática — etapa posterior

Somente depois de A estar no escopo E, com consentimentos confirmados e sem
pendências/conflitos, autorizar login da mesma conta em **B novo**. Não ativar v1
em B nem restaurar backup. O startup `recoverAuthorizedE()` aceita somente conta
já V2/E/autorizada e workspace sem controle/fila/restauração anterior; não chama
ativação remota. Recupera as páginas antes de preparar envios, preservando IDs.

Comparar turma, quatro bimestres, quantidades e identificadores antes de criar
quaisquer dados. Para cenário mínimo informado, espera-se preservar a turma e
seus quatro bimestres; não inventar alunos/notas ausentes. Uma instalação vazia
não gera exclusões remotas por ausência local.

Criação/edição fictícia, offline/reconexão, nota, frequência, FJ, diário ou conflito
são etapas futuras de escrita com autorização própria e limites definidos. Nenhum
ensaio de carga é parte deste início. As regras de remanejamento permanecem iguais.

## 7. Limites e próxima autorização

- O APK será destinado ao ensaio em AVD novo, não à atualização oficial c45.
- A instalação e os fluxos Android reais ficam pendentes neste executor sem AVD.
- O estado hospedado é informado pelo proprietário; elegibilidade atual só pode
  ser consultada no ensaio autorizado, não foi revalidada aqui.
- A ativação muda o protocolo da conta fictícia; não resolve conflito legado,
  não habilita a conta principal e não testa a migração dos dados escolares reais.
- Compartilhar o APK debug não o torna apropriado a produção institucional: é
  debuggable, usa certificado descartável e tem a exceção local de testes citada.

Próximo passo mínimo: criar AVD A vazio, verificar/instalar o APK e autorizar **login
e recuperação v1 somente da conta fictícia**, sem consentimentos B/C/D/E ainda.
Depois de conferir a turma, autorizar explicitamente a transição controlada da
conta de teste, preservando os pontos de parada da seção 5.
