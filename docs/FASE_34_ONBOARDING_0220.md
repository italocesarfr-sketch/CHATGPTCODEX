# Fase 3.4 — regressão completa aprovada localmente (23/11/2026)

**Gate automatizado local cumprido. Não é homologação hospedada de OTP nem
liberação de produção.** Branch `sync/academic-commercial-onboarding-034`.
Código validado `553771d04f210c8cb2f4ff27246148c727ebbc22`.

- CI completa: [45274547988](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45274547988),
  **SUCCESS**, 12m10s. Não foi substituída pelo diagnóstico isolado.
- Diagnóstico dirigido: [45273425576](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45273425576),
  **SUCCESS**, quatro etapas Android e launcher, em 5m43s.
- APK paralelo: [45276808241](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45276808241),
  **SUCCESS**, 5m42s, mesmo SHA do código validado. Identidade, hash, assinatura e
  abertura real pelo ícone conferidos. Exclusivo para AVD novo, sem instalar sobre
  versões existentes.
- CLI GitHub autenticada nesta retomada. Os três arquivos locais, já publicados
  pelo conector na sessão anterior, eram idênticos aos remotos; foram preservados
  e incorporados por fast-forward, sem reset, clean ou descarte.

## Causa comprovada da falha `45111406970`

A linha 185 do SHA `d6efa2c` reunia `assertTrue(classes.isEmpty())` e
`assertFalse(store.active())`. O stack trace contém `Assert.assertTrue:42/53`,
**sem** as chamadas `Assert.assertFalse`: a primeira condição falhou.

O cenário anterior `e-auto-final-isolation` cria deliberadamente, pelo repositório
real, uma turma **local de B**, `other-local-only`, e seus quatro bimestres. Depois
retorna a A, sem apagar B. O novo teste repetido tinha sido validado isoladamente
sobre B vazia, mas a bateria completa o executava **depois** dessa preparação.
Exigir novamente B vazia era uma expectativa incorreta da fixture. Não foi
necessário corrigir AuthController, workspaces, Room ou sincronização do produto.

A reprodução agora executa primeiro o isolamento anterior e só depois as três
alternâncias. O checkpoint da **primeira entrada de B, rodada 0**, comprova:

| Verificação | Resultado |
| --- | --- |
| Sessão / workspace / controle | B / B / B |
| Estado de sessão | Autenticada, sem inicialização/carregamento pendente |
| Registros locais | 1 turma legítima + 4 bimestres; exatamente 5 registros |
| Predicado antigo `classes.isEmpty()` | false |
| `store.active()` | false: a segunda condição de segurança continua satisfeita |
| Escopo local / cursor | BOOTSTRAP_E, INELIGIBLE / 0 |
| Registros, protocolos, claims e direitos comerciais remotos de B | 0 / 0 / 0 / 0 |
| Dados, identidades ou estados acadêmicos de A em B | Ausentes |

O log antigo não guardava um contador de rodada. A reprodução confirma a falha já
na rodada 0 sob a mesma preparação; não se inventou um índice a partir do log.
Nas três rodadas, o hash de B permanece
`7efb46c55adbeb878ec2d18f47357b5fb7ee1e7c38f4979d180ddc69f30a149a`.

## Correção e verificações mantidas

A correção parcial `c3d29f8`, antes apenas compilada, foi reaproveitada e reforçada:

- Exige a fixture local **exata**, sem limpeza: turma original e quatro bimestres,
  hash registrado na criação anterior, snapshots e revisões preservados nas visitas.
- Compara identidades dos **quinze tipos**, impedindo interseção de dados A/B;
  confere titularidade de sessão, workspace, controle e estados persistentes.
- Mantém B inelegível/inativa, cursor zero e sem decisões. Confere no PostgreSQL
  descartável que B continua sem protocolo, registros remotos, claim ou direito.
- Retorna a A pela interface de login real; compara snapshot completo, cursor e
  ausência da turma própria de B. Nenhum login direto, activate/run ou botão de
  sincronização substitui a recuperação automática.
- Grava checkpoints antes das asserções e coleta-os mesmo quando um teste nativo
  falha. Proprietários são rótulos A/B/OTHER/NONE; não se registram tokens ou UUIDs.
- Nenhuma asserção do cenário original de isolamento foi removida. Sua exigência
  de B vazia **antes da criação legítima** continua existindo.

## Contagens conferidas da CI completa

| Bateria | Aprovados | Falhos | Ignorados / condicionais não executados |
| --- | ---: | ---: | ---: |
| JVM padrão | 332 de 337 | 0 | 5 |
| JVM comercial | 332 de 337 | 0 | 5 |
| PostgreSQL comercial | 20 | 0 | 0 |
| PostgreSQL regras D | 27 | 0 | 0 |
| PostgreSQL institucional | 18 | 0 | 0 |
| HTTP/PostgREST | 43 verificações | 0 | 0 |
| Geometria do launcher | 4 | 0 | 0 |
| Android instrumentado | **123** | **0** | **0** |
| Prova real pelo ícone, Application normal | 1 | 0 | 0 |

Android: 6 fases originais + 29 acadêmicas finais + 1 cenário de três ciclos
A/B/A + 84 Room/UI/Auth + 3 execuções com fonte 1,6. Os códigos nativos de aprovação
somam 123, sem -2/-3/-4; os scripts também verificam saída do processo/timeout.

Os cinco condicionais JVM por configuração são H01–H04 hospedados e
`SupabaseLiveAuthTest`. Não receberam credenciais nem autorização nesta execução;
**não contam como aprovados**. O job opcional de diagnóstico separado também fica
condicionalmente fora da execução completa; isso não é teste Android ignorado.
Os quinze testes JVM OTP fazem parte das suítes completas, não são aprovação de
envio de e-mail ou de GoTrue real.

## Integridade e comparação com a Fase 3.3

- Dois AVDs novos, 172 registros finais, quinze tipos; filas/inbox vazias ao concluir.
- Hash canônico dos dois dispositivos:
  `dbee2693e1fca7b1688f19d850fc5b9dc1efa6a4732d6475ea585abf593b58cd`.
- Cursor/head 13; 13 recibos distintos; três decisões APPLIED; revisão da nota 10.
- Histórico da nota: null, 0, 1.5, 2.5, 9, 4, 8, 9.5, 7.5, 8.5.
- IDs e revisões dos dois Rooms conferidos com o servidor e com a evidência
  `FASE_33_CI_0220.json`. Hashes e campos agregados do servidor são idênticos à 3.3.
  IDs aleatórios de operações/decisões de execuções diferentes não são confundidos
  com identidades acadêmicas; a unicidade dos recibos foi verificada dentro da CI.
- As três alternâncias adicionais mantêm o hash final, cursor, recibos e auditorias.
  B conserva seus cinco registros locais, sem qualquer registro/protocolo remoto.
- Provisionamento local: um claim, vínculo ao roster confiável e inicialização E.
- Backup criptografado, bloqueio de conta diferente, restauração antiga, pausa,
  reconciliação explícita, rejeição de decisão obsoleta, replay e offline preservados.
- Launcher: clique real, rede desligada, 11,943s incluindo instalação; PNG/XML
  inspecionados. Telas de OTP e painel com fonte 1,6 também inspecionadas.

Evidências redigidas:

- [Falha anterior](evidence/phase34/failure-45111406970.json).
- [Diagnóstico dirigido](evidence/phase34/diagnostic-45273425576.json).
- [CI completa e contagens nativas](evidence/phase34/full-45274547988.json).
- [Login pelo ícone](evidence/phase34/launcher-45274547988.png).
- [Retorno a A](evidence/phase34/accounts-return-A-45274547988.png).
- [OTP com fonte ampliada](evidence/phase34/otp-font16-45274547988.png).
- [Artefato completo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45274547988/artifacts/14868410445),
  ZIP SHA-256 `502ac5018f5810e37fc9c221b1d325b3e8dec6145ddb3e9773b6eaaa72a23d84`.
- [Artefato dirigido](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45273425576/artifacts/14865409854),
  ZIP SHA-256 `4705be042db579a17314667024274eee677988db99d827b8547e96301eaaf391`.

## Commits e arquivos desta correção final

- `1d5c6c4`: reprodução da preparação real de B, verificações fortes de isolamento,
  checkpoints e conferência local do servidor.
- `553771d`: mensagens de proprietário redigidas e evidência da falha anterior;
  SHA completo aprovado indicado acima.
- `CommercialFinalNetworkProbeTest.kt`: expectativas/diagnósticos; nenhum código
  de produção alterado. `test-commercial-ui-diagnostic.sh`: preparação equivalente.
  `test-commercial-android.sh`: coleta de falhas e gate da prova adicional.
  `test-commercial-ui-evidence.py`: confere checkpoints e contagens do servidor local.
- Documentação e evidências neste arquivo e `docs/evidence/phase34/`.

## APK paralelo gerado após o gate

[Baixar o artefato `noctua-homologacao-comercial-debug`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45276808241/artifacts/14867084367).
No GitHub autenticado, clicar no artefato para baixar o ZIP e extrair
`app-homologacao.apk`. Não é release oficial.

| Propriedade | Resultado verificado |
| --- | --- |
| Código do APK e da CI completa | 553771d04f210c8cb2f4ff27246148c727ebbc22 |
| applicationId | br.com.diariodeclasse.homologacao |
| Versão / código | 0.22.0-dev-e2-homologacao / 45 |
| Projeto exclusivo | sscexzxtqdcsqdlcytum; sem fallback de produção |
| Callback PKCE | br.com.diariodeclasse.homologacao://auth/callback |
| Inicialização comercial | Habilitada no build; autorização permanece no servidor |
| Tamanho do APK | 13.053.006 bytes |
| SHA-256 do APK | b0eade2b913052a95410f07b8a387073acafb759e8eafe3f48c279ad2f1da757 |
| Certificado debug SHA-256 | 64937a78fde546e5f62a10c76734f96cd04a6e8bb2ad10ab29ec4c9006d998d2 |
| Launcher real, variante paralela | Aprovado, 12,033s incluindo instalação |

O certificado é Android Debug e **não** o permanente. A compatibilidade de
assinatura com APKs antigos de homologação não foi comprovada: instalar somente em
AVD recém-criado, sem usar `-r`, desinstalar ou limpar aplicativos existentes.
Não instalar no celular principal ou sobre o aplicativo oficial. A instalação de
CI ocorreu somente em outro AVD descartável e vazio.

A conferência de identidade/DEX/manifest passou no workflow, e `apksigner verify`
foi repetido localmente sobre o APK baixado. O SHA coincide com o manifesto publicado.
Chave pública configurada, HTTP claro proibido, sem sharedUserId, armazenamento e
provider isolados, nenhuma credencial administrativa/fixture no app.
O APK usa o mesmo código aprovado; os commits posteriores desta entrega são apenas
documentais. Não houve alteração de package/assinatura do aplicativo oficial.

As duas suítes JVM do workflow do APK (Debug e Homologacao) tiveram, **cada uma**,
337 casos: 332 aprovados, cinco condicionais hospedados não executados e zero falhas.
Não são uma nova bateria Android de recuperação; a prova nativa completa é a CI
45274547988. Os demais jobs do caller, inclusive assinatura/atualização oficial e
ensaios hospedados, foram corretamente não selecionados.

[Identidade e resultados redigidos](evidence/phase34/apk-45276808241.json),
[captura do launcher paralelo](evidence/phase34/homologacao-launcher-45276808241.png).
ZIP SHA-256:
`d43f273ec3925cab379cb4ec7ce91a63efee70f0386100093073d47de08a363a`.
As capturas desta entrega contêm somente telas vazias ou fixtures fictícias.

## OTP, provisionamento e homologação mínima pendente

A implementação OTP preservada permite consultar o e-mail em qualquer aparelho e
digitar o código no app. Após confirmar, fecha somente a sessão temporária obtida
na verificação e exige login com senha; não admite workspace por simples confirmação.
Cadastro, expiração/erros, reenvio controlado, PKCE e preservação de sessão/workspace
estão cobertos localmente. Entrega real, expiração e uso único no GoTrue hospedado
precisam de autorização e teste próprios; não foram substituídos por mocks silenciosos.

A migração `202611220001_institutional_provisioning.sql` e o template
`supabase/templates/confirm_signup_otp.html` continuam **preparados, não aplicados**.
SHA-256 da migração:
`934bc08a8448cea5b6e73c4cef488472940838195420236a3aeaae283ea341aa`.
Direitos comerciais continuam distintos de Auth, confirmação e associação
institucional. Não foi concedido direito ou rollout em ambiente hospedado.

Procedimento mínimo futuro, mediante autorização administrativa separada:

1. Somente em `noctua-homologacao` (`sscexzxtqdcsqdlcytum`), revisar o template
   Confirm signup para mostrar `{{ .Token }}`; preservar confirmação obrigatória,
   expiração, limites oficiais e o link oficial opcional para clientes antigos.
   Usar conta fictícia inédita com caixa autorizada. Em AVD novo, cadastrar, consultar
   o e-mail em outro aparelho, digitar o código, comprovar que a confirmação exige
   login com senha e então entrar. Não compartilhar códigos, links ou senhas.
   Limitações do mailer/SMTP continuam pendentes; não prometer entrega universal.
2. Após revisão do check somente leitura e backup administrativo, autorizar
   separadamente a migração institucional, dependente do A–E e bootstrap comercial
   originais, e **uma** entrada de roster fictício com prazo. Não reaplicar migrações
   anteriores ou conceder por domínio. Verificar E automático e recuperação num
   segundo AVD novo. Não repetir os oito testes acadêmicos manuais já relatados.

Produção, Supabase hospedados, contas existentes, AVDs do proprietário, main e
assinatura permanente permaneceram intocados. Não houve H13/PT409, release oficial,
instalação em dispositivo existente ou alteração de regras acadêmicas.

---

# Histórico dos checkpoints anteriores (não representam o estado atual)

# Fase 3.4 — estado consolidado da retomada final (23/11/2026)

**Ainda não aprovada integralmente.** A regressão completa precisa ser conferida
antes de gerar o APK paralelo. Nenhuma operação hospedada foi autorizada/executada.
Os checkpoints históricos abaixo devem ser lidos com suas próprias datas e SHAs.

## Evidências desta retomada

- Working tree inicialmente limpa, branch `sync/academic-commercial-onboarding-034`,
  HEAD `d4751cc`; nenhuma implementação anterior descartada/recriada.
- Última falha completa revisada: `45078704183`, código `eff20b1`.
  Os XMLs JVM mostram 337 casos por configuração (332 aprovados, cinco condicionais
  não executados, zero falhas). Android: 29 etapas aprovadas, uma falha em isolamento;
  a bateria posterior não executou. O launcher também falhou.
- ZIP da falha: SHA-256 `94c994574f409dc7fed306a4e9ead67f6561deb9b88876982b25d7de6484d0e7`.
- Diagnóstico isolado [45110657467](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45110657467),
  código `8201b29b2e2785f34a2c95dd862cc13914ab17f1`: **sucesso**, 4m35s.
  Quatro testes de geometria, 18 PostgreSQL institucionais, 43 verificações HTTP,
  três etapas Android executadas/aprovadas, zero falhas/ignorados nessas etapas.
  As duas primeiras etapas preparam/confirmam a fixture e a terceira testa a UI.
  A suíte JVM e a regressão completa foram intencionalmente omitidas nesse modo;
  não são contabilizadas como aprovação do diagnóstico.
- Artefato do diagnóstico: `commercial-auto-local-evidence`, ID `14789522575`,
  ZIP SHA-256 `bfff75cf9f12df75847ed9d3388bdf9efeb74e2818167f80a53cb5361a057094`.
- Abertura real pelo ícone: 12,968s incluindo instalação, rede desligada,
  Application normal, sem instrumentação/atalho de Activity. Dois layouts frescos
  iguais; centro medido (269,273), bounds [233,216][306,330]. PNG e XML inspecionados.
- Três ciclos A → B → A: login por campos/botão nativos, logout real, B inelegível
  e vazia, retorno de A com seus 171 registros dos quinze tipos, cursor 1 e nenhuma
  outbox/inbox pendente. Hash idêntico nas três voltas:
  `c7e8b5f2c1f49b4be0a01b9ec029c25ba11ce1a3f0ce09adad7774c7b26f356f`.
  A captura da terceira volta foi inspecionada. Auth é **sintético local**, HTTP e
  Room reais em infraestrutura descartável; não comprova GoTrue/SMTP hospedado.

## Duas falhas: causa observada, correção e limites do diagnóstico

**Launcher:** a captura e `window-focus.txt` da falha mostram o aplicativo Telefone,
embora a captura anterior mostre o NOcTuA no drawer. O teste utilizava a primeira
ocorrência do rótulo e reutilizava sua geometria depois de outras operações ADB,
sem conferir estabilidade da grade após instalação/animação. Isso permitia tocar
um destino diferente sem verificar a geometria imediatamente precedente. O novo
executor exige duas árvores recém-geradas com a mesma grade de ícones clicáveis,
recusa alvo ambíguo e registra XML/bounds/centro usados. Remove o XML anterior antes
de cada dump para impedir leitura de arquivo antigo. A janela de animação normal
necessária ao Launcher3 continua restrita ao ensaio e restaurada no finally.
O cenário passou sem mudar Manifest, MainActivity ou pacote. Sem o XML anterior ao
antigo toque, a sequência exata de reposicionamento daquele frame não é comprovada.

**Troca de contas:** a falha ocorreu na segunda entrada, B → A, linha 180 do código
`eff20b1`. A captura mostra login com campos vazios; o executor não via `entry-email`.
Não foi demonstrado defeito de logout ou persistência: o modelo já estava sem usuário
admitido e o workspace bloqueado. A travessia nativa agora atualiza cada ancestral/
nó antes de ler os filhos e o testTag oficial, recusando nós inválidos. Nenhum acesso
por DAO/auth/engine substitui a interação. Diagnósticos guardam só tags conhecidas,
booleanos e contagens, sem conteúdo de campos. As três trocas passaram. Os contadores
do diagnóstico registraram zero mudanças na **quantidade** de filhos e zero nós
inválidos; portanto não permitem atribuir definitivamente o evento antigo a um nó
específico em cache. A divergência árvore/captura é comprovada, sua causa interna
exata permanece limitada à evidência disponível. Não foi alterado código do produto.

## Commits e gate atual

- `18ad61e`: geometria fresca/estável e regressão estática do launcher.
- `8201b29`: atualização dos nós, diagnóstico sanitizado e modo isolado manual.
- `d6efa2c5bb9092bb025ba00da6b4407d0f2213df`: inclui os três ciclos A/B/A também
  na regressão completa, preservando todos os 122 casos nativos anteriores
  (123 execuções nativas esperadas agora, mais a prova separada de launcher).
- `c3d29f80358af1f93a8c8dda1179755bf342bfb5`: preserva dados próprios de B
  que o teste de isolamento completo já cria deliberadamente. O novo teste compara
  snapshot/revisões de B entre as três visitas e impede acesso às turmas de A;
  não exige apagar ou esvaziar B. As asserções do cenário anterior permanecem intactas.
  **Compilado localmente em 26s, ainda não executado em Android nessa revisão.**
- Compilação instrumentada inicial local: sucesso em 47s; Bash/Python/YAML e diff conferidos.
- JVM focado desta retomada: `SignupOtpTest` (11) + `SignupOtpHttpsTest` (4),
  **15 aprovados, zero falhas/erros/ignorados**, Gradle em 21s. Transporte HTTPS
  local simulado, sem acesso a GoTrue, e-mails ou credenciais hospedadas.

A credencial da CLI GitHub expirou após o diagnóstico (HTTP 401). O conector ligado
permitiu publicar o último commit; ele conserva o gatilho existente de push.
A execução completa desse SHA ainda precisa ser localizada e seus artefatos
conferidos. O conector disponível só lista execuções de pull request por SHA e
não oferece despacho manual; a ausência nessa consulta não prova ausência de CI.
Foi solicitada renovação da conexão, sem solicitar valores de credenciais.
Não se despachou APK, não se declarou aprovação e não se repetiu CI em paralelo.
O commit `c3d29f8` foi publicado com `[skip ci]` enquanto não é possível verificar
se a execução anterior terminou. Portanto, o gate final exige uma execução completa
que inclua essa correção da precondição, após conferir que não há outra em andamento.
Não contar como aprovada a asserção nova apenas por ter compilado.

### Continuidade exata após renovar a conexão

1. Conferir `git status` e preservar as três alterações locais que correspondem às
   publicações pelo conector (o fetch da CLI também retornou falha de autenticação).
   Local HEAD ficou em `8201b29`; a branch remota inclui os commits posteriores.
   Reconciliar por fast-forward verificado, sem reset/clean e sem descartar arquivos.
2. Localizar a execução por push do SHA `d6efa2c`, inspecionar seu resultado e guardar
   o artefato; não iniciar outra enquanto ela estiver em andamento.
3. Executar a regressão completa sobre o HEAD atualizado:
   `gh workflow run commercial-auto-local.yml --repo italocesarfr-sketch/CHATGPTCODEX --ref sync/academic-commercial-onboarding-034 -f ui_diagnostic_only=false -f launcher_only=false`.
4. Exigir os 123 casos nativos, launcher, JVM, PostgreSQL e HTTP; examinar as capturas
   de fontes ampliadas e as evidências finais dos dois AVDs. Comparar hash final,
   revisões, cursor, recibos e decisões com o baseline 3.3; os três ciclos adicionais
   não devem criar decisões/recibos acadêmicos nem alterar o hash.
5. Somente depois, gerar APK paralelo pelo workflow existente; não usar assinatura
   permanente nem instalar nos AVDs existentes. Conferir URL, applicationId, SHA e
   certificado do artefato antes de disponibilizar como candidato para teste.

O template OTP e a migração `202611220001_institutional_provisioning.sql` permanecem
preparados, sem aplicação hospedada. Hash da migração permanece
`934bc08a8448cea5b6e73c4cef488472940838195420236a3aeaae283ea341aa`.
Após CI completa aprovada, gerar APK exclusivamente paralelo pelo workflow existente,
inspecionar identidade/assinatura/launcher e usar somente AVDs novos. A validação
hospedada mínima e suas autorizações separadas estão nos procedimentos abaixo.

---

# Fase 3.4 — autenticação, onboarding e provisionamento comercial

Branch dedicada `sync/academic-commercial-onboarding-034`, base
`sync/academic-commercial-final-033` / `2b999cbe9868e580002f67772de217e1f1763fae`.
Preparação iniciada em 21/11/2026; retomada em 22/11/2026, America/Sao_Paulo.
Sem operações hospedadas, contas existentes, dados reais, main, assinatura ou APK oficial.

## Confirmação por código: implementada, hospedagem pendente

O cadastro original usa PKCE S256. O callback exige esquema/host/caminho exatos,
verificador/estado locais, prazo, ausência de fragmento/parâmetros extras. Essa
proteção deliberadamente impede trocar o código do link em outro aparelho.
Um link antigo pode confirmar o e-mail no servidor e falhar no retorno local;
isso não autoriza contornar o verificador. O novo caminho exibe uma confirmação
por código no aplicativo, sem exigir que a caixa de e-mail esteja no mesmo dispositivo.

Usa somente mecanismos oficiais: signup com senha/PKCE, POST `/auth/v1/verify`
com email/token/type=email, GET `/auth/v1/user` para identidade confirmada pelo
servidor e resend type=signup com novo challenge PKCE. Não usa `/otp` com criação
automática de usuários, TokenHash no campo de código, JWT decodificado como identidade,
service_role ou confirmação local falsa.

Após validar o código, **não se admite a sessão OTP no workspace**: ela é encerrada
com logout scope=local e o professor entra com senha. O e-mail confirmado precisa
corresponder ao alvo pendente. Logout durante verificação invalida a operação;
uma resposta tardia não pode recuperar sessão ou trocar de conta. Uma sessão anterior
armazenada, inclusive expirada/offline aguardando revalidação, bloqueia o início
do fluxo OTP e permanece intacta; a confirmação não a encerra ou substitui. Cleanup da
sessão temporária é best-effort, sem persistir seus tokens em disco/logs; falha de
rede pode deixar uma sessão sem uso no serviço até expiração/revogação normal.

A tela mantém os campos/botões Compose e tema existentes, código efêmero sem senha
persistida, loading, erro, reenvio explícito e navegação de volta. A tentativa de
login não confirmado oferece a confirmação por código, inclusive em outra instalação.
O alvo e os timestamps persistem no cofre Keystore (não o código). Metadados locais
reservam cooldown de reenvio de 60s antes da rede, espera entre verificações e pausa
após cinco tentativas. São controles de UX, **não substitutos** dos limites/expiração
obrigatórios do Supabase. Não foi alterado limite algum no servidor.

Aceita 6–10 dígitos ASCII, intervalo do provedor, sem supor que todo projeto usa
seis. O servidor decide validade, uso único e expiração; o app limita a tentativa
local a uma hora e permite solicitar outro código. Erros expõem mensagens genéricas,
nunca respostas privadas. HTTP mantém HTTPS, sem redirects, timeouts e limite de
resposta de 256 KiB. O factory de conexão é test seam para TLS loopback, não uma
URL configurável por Intent ou propriedade de usuário.

AuthSecrets ganhou apenas o metadado opcional signup, com defaults para ler
arquivos antigos. AES-GCM, alias, AAD, arquivos de titularidade, sessões existentes,
Room 14 e backup acadêmico permanecem iguais. **Downgrade para leitor antigo durante
uma confirmação pendente não é homologado**: leitores antigos estritos não conhecem
o novo campo. Concluir/cancelar a confirmação o remove; não limpar arquivos para
resolver erro. Nenhum downgrade/instalação no proprietário foi executado.

### Configuração futura dos e-mails, sem aplicação nesta fase

1. Confirmar no painel o projeto **noctua-homologacao / sscexzxtqdcsqdlcytum**.
2. Revisar/copiar o template atual antes de alterar. Somente após autorização,
   Authentication → Email Templates → Confirm signup: usar o arquivo preparado
   `supabase/templates/confirm_signup_otp.html`, com `{{ .Token }}` como caminho principal
   e `{{ .ConfirmationURL }}` opcional para clientes antigos. Não remover esse
   fallback numa futura implantação que ainda admita versões anteriores.
3. Não alterar Reset password, confirmação obrigatória, CAPTCHA, limites de
   tentativas ou OTP expiry para fazer o teste passar. A recuperação permanece PKCE.
4. Conferir a configuração real de tamanho/prazo do OTP, envio de e-mail/SMTP e
   limites Free. Não presumir entrega a qualquer destinatário externo.
5. Preservar o callback homologação `br.com.diariodeclasse.homologacao://auth/callback`
   e suas validações para os fluxos de link. Produção não recebe alterações.

O template foi preparado, não enviado ao Supabase. O novo APK só poderá provar a
entrega OTP real após configuração autorizada e teste com conta fictícia inédita.
Referências oficiais consultadas: [Email templates](https://supabase.com/docs/guides/auth/auth-email-templates),
[verificação OTP Kotlin](https://supabase.com/docs/reference/kotlin/auth-verifyotp),
[resend](https://supabase.com/docs/reference/javascript/auth-resend),
[rate limits](https://supabase.com/docs/guides/auth/rate-limits),
[código oficial GoTrue](https://github.com/supabase/auth/blob/master/internal/api/verify.go)
e [limites configuráveis OTP](https://github.com/supabase/auth/blob/master/internal/conf/configuration.go).

## Launcher — análise e prova Android

Manifest declara MAIN/LAUNCHER, MainActivity exportada, singleTop, ícones normal e
round e callback separado. Não se presume defeito só porque instruções anteriores
usaram ADB. O ensaio novo tocará o ícone real no launcher de AVD próprio, com o
Application normal e rede bloqueada antes de instalar. Não usa instrumentação,
monkey ou componente hardcoded para produzir a abertura. Validação pendente de CI;
nenhuma correção de manifest se justifica antes da reprodução.

As rodadas 45067806104 e 45068761680 pararam na automação da gaveta, antes
de abrir o app. A captura isolada sugeriu um botão clássico, mas o XML posterior
esclareceu a geometria real: `Apps list` é um indicador de gesto de apenas seis
pixels, cujo toque não abriu a gaveta. Não era evidência de defeito no Manifest.
A correção `0ec3cba` usa a tecla pública Android ALL_APPS para abrir **só** a gaveta;
o toque que abre o NOcTuA continua sendo no ícone encontrado pela árvore real.
O comportamento da tecla foi conferido no código oficial Android 11
[PhoneWindowManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release/services/core/java/com/android/server/policy/PhoneWindowManager.java)
e [Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android11-release/src/com/android/launcher3/Launcher.java).
XML/capturas do launcher vazio são preservados para diagnóstico. A regressão
instrumentada também passa a coletar suas evidências independentes se essa prova
falhar; **o código de falha do launcher continua reprovando o job**, mesmo que todos
os demais testes passem. A prova do APK paralelo permanece uma barreira de upload.

## Provisionamento institucional mínimo — migração preparada

A autorização é separada em cinco responsabilidades:

1. Auth cadastra usuário/senha; não concede licença nem vínculos institucionais.
2. Confirmação do e-mail prova posse da caixa; não comprova vínculo de trabalho.
3. Administração confiável pré-autoriza o e-mail exato em roster protegido, com
   referência institucional e prazo de primeiro resgate; não há regra por domínio.
4. Servidor deriva UUID e e-mail de Auth confirmado, cria direito comercial e um
   vínculo de proveniência imutável. Nunca aceita papel/instituição por user_metadata.
5. O inicializador E existente conclui o protocolo transacional/idempotente. App
   não tem menu institucional nem configuração A–E.

Arquivo preparado: `supabase/migrations/202611220001_institutional_provisioning.sql`.
Exige a migração comercial original 202611170001, mesma role proprietária da função,
corpo original revisado e A–E sem H13/PT409. Acrescenta roster administrativo e
claims de proveniência, ambos com RLS/deny-all e sem ACL de leitura/escrita dos
clientes. Helper privado sem EXECUTE comum; assinatura/ACL da única RPC existente
`noctua_ensure_academic_e()` não muda. Seu corpo permanece byte a byte igual salvo
a chamada explícita ao provisionador privado.

Não é um sistema multi-institucional completo. institution_reference é uma
referência opaca de proveniência; não concede acesso aos alunos/notas de outros
professores, papéis de gestor ou compartilhamento. Não há portal, convites por
link, gestão financeira, domínio institucional ou serviço administrativo no APK.

Rollout e roster ativo explícito são necessários. Contas manuais já existentes,
inclusive desabilitadas, não são reassociadas/reativadas por roster. Qualquer inscrição
de testes, dado v1, estado v2 parcial ou identidade legada continua bloqueando a
admissão automática. A autorização tentada e o head vazio são desfeitos se o guard
original recusar elegibilidade. O mesmo lock de v1 protege a corrida com escritores
antigos. Concessão, claim e E compartilham a transação; resposta perdida repete o
mesmo resultado. Claims confirmados não podem ser alterados/excluídos por operações
normais; não se reciclam e-mails/UUIDs para recuperar contas.

O prazo do roster é **prazo de primeiro resgate**, não licença/subscrição com
expiração automática. Revogar novas admissões não apaga dados/direitos existentes:
a administração deve desabilitar o direito comercial específico pelo mecanismo
existente, mediante autorização própria. Alterar associação de conta existente
exige outro procedimento auditado; não é feito implicitamente nesta entrega.

A migração não insere roster ou direitos e **não foi aplicada em ambiente hospedado**.
Localmente, 18 casos PostgreSQL passaram: e-mail exato, não autorizado, expirado,
rollout off, sessão inválida, privilégios, v1/parcial, replay, concorrência,
rollback, preservação manual, imutabilidade e fraude de metadata/identidade.
O procedimento preparado de implantação e contingência está ao final; sua execução hospedada continua bloqueada por autorização.

## Auditoria dos quatro tipos acadêmicos

| Tipo | Quando deve existir | O que não produz o tipo |
| --- | --- | --- |
| class_archives | `DiaryRepository.archiveClass(id,true)` grava marcador de arquivamento; reativar remove o marcador | Apenas editar/visualizar turma |
| preserved_histories | Exclusão validada de turma cujo histórico continua necessário a remanejamentos/vínculos preservados | Toda exclusão comum, simples arquivamento ou exportação |
| export_rows | Personalização persistida de posições, espaços ou ocultações da planilha | Gerar arquivo XLSX/CSV com disposição padrão calculada |
| import_drafts | Importação/colar lista ainda em conferência; confirmar ou descartar remove os rascunhos | Cadastro direto de aluno ou importação já concluída |

Todos já pertencem ao contrato de quinze tipos e aos triggers/outbox. O acervo
local 3.3 recuperou um arquivo de turma, um histórico, três linhas de disposição e
um rascunho. A ausência no inventário hospedado relatado não prova defeito: não há
registro acadêmico remoto de tipo que não tenha sido materializado/persistido.
Novos ensaios usam os comandos reais do repositório, conferem outbox e provam que
exportação de arquivos não cria linhas acadêmicas nem envia arquivos locais.
Nenhuma correção de sincronização foi comprovada como necessária neste checkpoint.

## Recuperação e evidências desta retomada — checkpoint de 22/11/2026

A branch 3.4 e as alterações não commitadas estavam disponíveis. Foram preservados
os cinco arquivos de Auth, os testes OTP/Compose, a migração institucional e os
preparativos de auditoria/CI. HEAD inicial ainda era a base 2b999cb; não houve reset,
limpeza ou reconstrução das fases anteriores. A retomada acrescentou proteção contra
substituição de sessão armazenada que aguarda revalidação, regressão de reutilização
do código e conferência de logout temporário scope=local no transporte TLS.

Commits por responsabilidade:

- `32dbd36`: Auth/OTP, UI e template preparado.
- `d4c89f7`: provisionamento institucional privado e 18 testes PostgreSQL.
- `5838b71`: captura das quatro entidades auxiliares por operações reais do repository.
- `ab21cf7`: launcher pelo ícone e ampliação da CI comercial já existente.
- `01acb32`: prepara fixture vazio exclusivo do job antes da bateria de migração; nenhum CHECK foi relaxado.
- `54e3da2`: amplia diagnósticos e coleta XML do launcher; a tentativa por toque no indicador não foi suficiente.
- `0ec3cba`: abre a gaveta com ALL_APPS e conserva falhas independentes da instrumentação.

Resultados efetivamente concluídos no workspace:

| Verificação | Resultado |
| --- | --- |
| JVM `testDebugUnitTest` | 337 casos: 332 aprovados, 5 condicionais/ignorados, 0 falhas/erros |
| OTP isolado, incluído no total JVM | 11 de controller/policy + 4 TLS loopback, aprovados |
| Compilação | assembleDebug e assembleDebugAndroidTest aprovados, configuração comercial padrão desligada |
| Provisionamento PostgreSQL 17.11 descartável | 18/18 aprovados; última rodada 5,387 s |
| Transporte PostgREST 12.2.3 local | 43 verificações aprovadas, 171 registros/15 tipos, sessão expirada/revogada/inválida e acesso cruzado |
| Preparação segura de homologação | 8/8 verificações estáticas aprovadas |
| YAML/Python/shell/diff | três workflows parseados; py_compile, bash -n e git diff --check aprovados |

A primeira tentativa manual HTTP foi feita antes do cache de esquema PostgREST
ficar pronto e recebeu 503. O log confirmou a inicialização do cache logo depois;
a rodada após readiness passou integralmente. Não foi falha acadêmica e não houve
alteração de timeout/retry do produto. A primeira reconstrução SQL do fixture sobre
um banco descartável ainda populado também foi interrompida por CHECK de escopo;
o banco exclusivo desta tarefa foi recriado vazio, sem qualquer banco do usuário.

A primeira [CI 45067489560](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45067489560)
parou antes do Android: a bateria anterior deixou seu fixture em E e a nova bateria
de migração tentou reaplicar A–D nele. O artefato confirmou a falha no CHECK de escopo,
sem executar a migração institucional. A correção 01acb32 recria apenas o banco do
serviço PostgreSQL efêmero do próprio job, com guardas CI/loopback/porta/nome; preserva
as evidências da bateria anterior e testa a migração sobre fixture vazio.

A [CI 45069656089](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45069656089)
executou **122 testes Android aprovados, zero ignorados/falhos**, além de 20+27+18
casos PostgreSQL, 43 verificações HTTP e 337 casos JVM por configuração (332 aprovados,
5 condicionais). **O job foi reprovado na prova independente da gaveta do launcher.**
A tecla ALL_APPS tampouco abriu a gaveta nessa imagem. A correção `4c2df5f` usa um
gesto iniciado nos bounds reais do indicador Apps list, acima dos atalhos do hotseat,
e aguarda o ícone; não troca a prova por abertura direta da Activity.

A recuperação/regressão preservou 172 registros/15 tipos, hashes iguais a 3.3,
cursores/head 13, 13 recibos únicos, três decisões auditadas, revisão da nota 10,
inbox/outbox pendentes zero nos dois AVDs. O Android foi provisionado pelo roster
com **um** claim e entitlement inicializado; B permaneceu sem protocolo/dados.
[Resumo sanitizado](evidence/phase34/local-45069656089.json) e
[captura real OTP com fonte 1,6](evidence/phase34/signup-otp-font16.png).
A captura foi inspecionada: campos/ações legíveis no tema escuro; conteúdo rola em
320×560dp, todos os controles foram alcançados pelos testes. Detector Impeccable:
zero achados mecânicos; isso não substitui a prova visual/native ou autenticação real.

A [CI 45071107166](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45071107166)
no código `4c2df5f5b84a3d10a457480b221d3d24c78fedb0` está em andamento.
A tentativa antecipada de despachar homologacao_only foi bloqueada pela revisão
automática porque a CI completa ainda falhava no launcher; o build paralelo não foi
iniciado. Aguardar aprovação antes do novo despacho. Não
confundir TLS mock/OTP simulado com cadastro/verificação real em GoTrue. As cinco
condicionais JVM não contam como aprovadas. Nenhuma autenticação hospedada foi feita.

### Preparação operacional do provisionamento (não executada)

- Inspeção preparada: `supabase/checks/onboarding_034_readonly.sql`, somente
  catálogo/configuração. Antes da migração devem existir as duas tabelas comerciais
  originais e nenhuma das duas novas. O inicializador precisa ter o corpo original
  e a role do SQL Editor precisa ser sua proprietária; a migração aborta ante drift,
  H13/PT409, objeto parcial ou role diferente. Não reaplicar migrações anteriores.
- Backup administrativo verificável do projeto separado, inventário/RLS aprovado e
  autorização explícita são portões anteriores à execução. O arquivo novo contém
  uma transação única e lock_timeout local de 5s, sem ampliar statement_timeout.
- Após autorização futura, executar **só** 202611220001 inteiro, uma sessão. Erro ou
  resultado incerto: não repetir; inspecionar catálogo para distinguir rollback de
  commit sem resposta. Não fazer DROP/reparos automáticos para passar o preflight.
- Pós-aplicação: esperado total de vinte tabelas acadêmicas/controle com RLS
  (dezesseis A–E/v1 + duas comerciais + duas de provisionamento). As novas tabelas
  são deny-all e os novos helpers não têm EXECUTE autenticado/anônimo. A RPC antiga
  mantém a ACL. Não conceder own_rows/SELECT nas tabelas administrativas.
- A migração não preenche o roster. Mediante **outra** autorização, administrador
  pode inserir uma linha de e-mail fictício inédito, referência institucional opaca,
  enabled=true e prazo futuro de resgate. Modelo para revisão: `INSERT INTO
  public.noctua_commercial_roster(email,institution_reference,enabled,expires_at)
  VALUES ('<e-mail fictício canônico aprovado>','<referência administrativa>',true,
  now()+interval '7 days');`. Confirmar uma linha, nenhum direito prévio, ausência
  de allowlist/v1/v2 parcial; não usar UPSERT, domínio, wildcard ou dados reais.
- O rollout global existente também precisa estar autorizado/ativo. Não alterá-lo
  incidentalmente: seus efeitos sobre todos os direitos atuais exigem revisão
  separada. A homologação existente não é reconfigurada nesta tarefa.
- O Android do ensaio descartável passa a ser provisionado por esse roster, sem
  INSERT manual de entitlement; a prova final exige exatamente um claim, vínculo
  correto e inicialização E. B permanece sem direito. Isso não é uma conta hospedada.
- Contingência administrativa futura: desabilitar o roster bloqueia novas admissões;
  para direito já concedido, desabilitar o entitlement específico preservando dados,
  claim, filas e titularidade. Essas alterações exigem autorização própria. Não
  remover auditorias nem restaurar banco sobre dados ativos como rollback casual.

## Arquivos e comandos de validação

Auth: `AuthModels.kt`, `AuthVault.kt`, `AuthController.kt`, `SupabaseAuthClient.kt`
e `AuthScreen.kt`. Testes: `SignupOtpTest.kt`, `SignupOtpHttpsTest.kt`,
`SignupConfirmationUiTest.kt` e `AcademicAuxiliaryCaptureTest.kt`. Preparação do
servidor: nova migração institucional, check somente leitura e template OTP.
Integração CI: workflows `commercial-auto-local.yml`, `android.yml`, `homologacao.yml`,
helpers `test-commercial-android.sh`/`test-launcher-icon.py` e os scripts de fixture,
evidência e teste de provisionamento. Nenhuma entidade/schema Room, DAO, regra
acadêmica ou RPC A–E foi reescrita. A migração comercial original continua com
SHA-256 `1d5c48c0b55a24edbae948407a8775030490338c8a3b0c6a64b2a98e5bf051e2`.
Nova migração preparada (6.729 bytes):
`934bc08a8448cea5b6e73c4cef488472940838195420236a3aeaae283ea341aa`.

Comandos locais (SDK/JDK instalados no workspace; serviços somente loopback):

```sh
./gradlew --offline --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES python3 scripts/test-commercial-provisioning.py
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES NOCTUA_ONBOARDING_LOCAL=YES python3 scripts/test-commercial-http.py --prepare
PGHOST=127.0.0.1 PGPORT=55431 PGDATABASE=noctua_phase_e_test NOCTUA_COMMERCIAL_DISPOSABLE=YES python3 scripts/test-commercial-http.py
python3 scripts/test-commercial-homologation-preparation.py
bash -n scripts/ci/test-commercial-android.sh
```

São fixtures SQL locais independentes de GoTrue hospedado. As cinco condicionais
JVM são H01–H04 e SupabaseLiveAuthTest, deliberadamente sem autorização/credenciais
nesta tarefa; não foram executadas nem aprovadas novamente.

## Validação manual mínima futura (não executada)

1. Após autorização e revisão do template em **homologação**, usar uma conta
   fictícia inédita: cadastrar, consultar o e-mail em outro aparelho, tentar um
   código inválido, confirmar com o código correto e entrar com senha. Não enviar
   códigos, links ou senhas pelo chat. Conferir que a confirmação, sozinha, não abre
   workspace. Expiração/uso único/entrega precisam ser comprovados no GoTrue real.
2. Após autorização administrativa separada para a nova migração e **uma** linha
   de roster, entrar com essa conta num AVD novo. Verificar inicialização E sem menus
   e recuperação no segundo AVD novo. Não alterar contas comerciais já homologadas;
   os oito cenários acadêmicos manuais das fases anteriores não precisam ser refeitos.

Os testes locais não autorizam produção ou concessão comercial. Migração, template,
roster/rollout e uso de conta hospedada continuam pendentes de autorização própria.

## Continuidade após diagnóstico isolado — 22/11/2026

Os 122 testes instrumentados acima foram aprovados na execução 45069656089; essa
prova permanece válida para o código nela executado. **Não transferir a aprovação
para uma execução posterior que falhou.**

- `4c2df5f`: tentativa de gesto a partir do indicador medido. A CI 45071107166
  continuou falhando na gaveta e interrompeu a bateria em
  `e-auto-commercial-reconnect` com CalledFromWrongThreadException. O stack está no
  TestMonotonicFrameClock/ApplyingContinuationInterceptor do Compose e termina na
  emissão de um flow do Room em ThreadPoolExecutor; não há evidência de perda ou
  mudança de regra acadêmica. As fases posteriores não foram executadas nessa rodada.
- `ecdf378`: os dois probes comerciais passam StandardTestDispatcher ao effectContext
  da regra Compose. O Compose 1.7.6 usava UnconfinedTestDispatcher por padrão, que
  pode retomar recomposição inline na thread do banco. O scheduler explícito enfileira
  essa retomada para o relógio de UI do teste. Compilação instrumentada local aprovada
  em 34s; **a validação Android dessa correção ainda está pendente**. Código do app,
  engine, transporte e regras acadêmicas não foram alterados nessa correção.
- `b22489f`: adiciona `launcher_only` ao workflow comercial existente para investigar
  a UI do AVD sem repetir SQL/HTTP/JVM/rede. Job/artefato são explicitamente marcados
  como diagnóstico; ele não substitui nem aprova a bateria completa. O commit foi
  enviado com skip ci para evitar duplicar a regressão durante a investigação;
  a execução completa será exigida depois do diagnóstico. Nenhum teste foi removido.
- Diagnóstico 45072504281: ainda falhou em DRAWER, API 30, tela 320×640, foco na
  QuickstepLauncher. Não houve abertura do NOcTuA, instrumentação ou autenticação.
- `df381c4`: a próxima prova usa o mecanismo normal de busca local do launcher AOSP
  e toca o ícone encontrado; a consulta contém apenas a marca pública Noctua. Não
  envia Enter, Intent ou componente para abrir o aplicativo. O diagnóstico inclui
  um toque de controle na Gallery vazia do próprio AVD e coleta erros de sistema,
  diferenciando falha geral de entrada de defeito do APK. Todos os ensaios permanecem
  em AVDs novos, sem sessão, com rede desabilitada antes da instalação.

Diagnóstico em andamento: [45073148659](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45073148659),
SHA `df381c45461c2324ce002167570293186d2fc171`. Não presumir sucesso. Depois de passar,
executar o workflow comercial completo (launcher_only=false), conferir os 122 casos,
hashes, claims, filas e revisões e somente então despachar homologacao_only.
O bloqueio automático do build paralelo não será contornado.

## Launcher: prova real aprovada, causa de infraestrutura isolada

[Diagnóstico 45074383960](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45074383960),
SHA `64f0c610a955ef71e19be272c3ba63aae599a010`, aprovado. Mesma imagem API 30,
mesma geometria medida e mesmo app, com a única mudança relevante de temporização:
`animator_duration_scale` restaurado de 0,0 para 1,0 durante a prova. A gaveta abriu,
o ícone Noctua foi encontrado e **tocado**, e a Application normal apresentou a tela
de login em 12,706s, sem instrumentação, usuário/sessão ou conexão de rede.
A escala anterior é restaurada no finally antes dos testes Compose.

A causa observada na CI foi a interação do Launcher3 com animação desabilitada
pelo runner. O Manifest, MainActivity, rótulo e ícone estavam corretos; não foram
modificados. Isso **não prova a causa do relato original no AVD do proprietário**:
a configuração daquele dispositivo não foi inspecionada nem alterada. A validação
atual comprova o contrato e a abertura pelo ícone nesse AVD descartável.

- [Resultado sanitizado](evidence/phase34/launcher-proof.json).
- [Ícone na gaveta real](evidence/phase34/launcher-icon-found.png).
- [Login aberto pelo toque](evidence/phase34/login-from-launcher.png).

A busca QSB da imagem não ofereceu o app como sugestão; ela não é usada como
substituto da prova. A abertura final veio da **gaveta**, com o ícone correto.
Os três testes da geometria usam o XML vazio real do AVD, dobram a densidade e
reprovam layouts sem região segura. Não substituem o teste Android. O modo
launcher_only continua exclusivo de diagnóstico; o job completo não foi executado
nessa rodada e seu skip não é contado como aprovação.

A regressão completa foi iniciada em
[45074735418](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45074735418),
mesmo SHA 64f0c61, launcher_only=false, incluindo a correção ecdf378 do scheduler
Compose. **Ainda não aprovada neste checkpoint.** Aguardar resultados/artefatos
antes do APK paralelo. Nenhuma execução hosted, SQL remoto ou assinatura permanente.

## Atualização da instrumentação após a primeira regressão completa

A execução 45074735418 comprovou novamente a abertura pelo ícone em 8,379s, sem
controle Gallery e com a Application normal. Foi reprovada no primeiro cenário de
rede: o StandardTestDispatcher de ecdf378 passou a executar o flow do Room durante
avanços do relógio na main thread. **A guarda Room recusou; não foi desabilitada.**
O APK não foi gerado por essa execução e a bateria posterior não foi executada.

`7847dcb` substitui essa tentativa por um adapter **somente androidTest** que lança a
mesma MainActivity com WindowRecomposerFactory.LifecycleAware, seu recomposer real
ligado ao lifecycle/Choreographer. Os testes continuam usando as mesmas consultas
semânticas, assertivas, cliques, deadlines e dados/HTTP reais locais. O contexto
artificial de continuação do Compose 1.7.6 não se propaga mais às consultas Room.
Não há allowMainThreadQueries, troca de DAO, conteúdo fake, admissão direta,
activate/run/request ou remoção de asserts. Compilação instrumentada aprovada em45s.
O adapter usa API interna **test-only da versão Compose fixada no BOM** e precisa
ser revisado na atualização dessa dependência; não é incorporado ao APK do app.
A prova Android dessa mudança ainda aguarda a nova CI completa.

O template preparado passou a manter também o ConfirmationURL oficial como
fallback de versões antigas. O código continua primário para leitura do e-mail em
outro dispositivo. Isso preserva o link PKCE existente sem desabilitar confirmação
ou emitir tokens localmente. Nenhum template hospedado foi alterado.

## Ponto atual da instrumentação — adapters anteriores substituídos

A CI 45076131787 passou os seis cenários de rede iniciais, mas a tentativa de
combinar o recomposer de produção com o leitor de semântica Compose falhou numa
consulta de login (SnapshotStateObserver, acesso concorrente de threads). Não foi
aprovada e o adapter interno de 7847dcb foi **removido**, sem mudar o código do app.

`1e10cf5` mantém ActivityScenario/lifecycle normais e usa ações nativas de
acessibilidade apenas nos dois probes comerciais de HTTP/Room. Os identificadores
existentes são lidos pela extra-data oficial `androidx.compose.ui.semantics.testTag`;
não se habilita testTagsAsResourceId no app, não se substitui conteúdo, não se chama
login/engine diretamente e não se muda a thread do Room. Permanecem os mesmos
valores esperados, escolhas explícitas, botões desabilitados, rejeição de terceira
revisão, IDs, hashes, recibos, conflitos, pausa/restauração e contas isoladas.
As ações exigem controle único, habilitado e visível; a restauração de sessão é
aguardada antes de decidir que é necessário entrar. A rolagem tem deadline e exige
o controle realmente alcançado. Não se afrouxa nenhuma guarda do banco.

A primeira CI do driver, 45077628272, parou antes do primeiro cenário por uso de
Bundle.EMPTY na API refreshWithExtraData. Android modifica o Bundle de argumentos;
o executor foi corrigido em `eff20b1` para Bundle mutável e descarta nós obsoletos.
Compilação instrumentada local aprovada (28s). **Ainda não é aprovação Android.**

Execução completa atual: [45078704183](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/45078704183),
SHA `eff20b174151e989f82a4ee76b30fcc1ed2d7818`. Aguardar e inspecionar o artefato
commercial-auto-local-evidence. Não repetir uma execução em andamento.
O último conjunto de 122 casos aprovados integralmente continua sendo 45069656089,
cujo job falhava só no launcher; a prova isolada de launcher já passou em 45074383960.
A aprovação conjunta e o APK paralelo continuam pendentes. Não contornar o bloqueio
automático do despacho de homologacao_only nem apresentar os jobs condicionais ou
as fases não executadas das rodadas com falha como aprovados.
