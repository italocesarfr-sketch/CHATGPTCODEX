# Candidato oficial 0.22.0/c45 — preflight e atualização protegida

15/11/2026, America/Sao_Paulo. Branch `sync/academic-complete-0220`.
**Preparação concluída; nenhum job com chave permanente executado.** A meta de
16/11 depende das configurações e autorizações abaixo, não autoriza pular etapas.
Referência anterior: [preparação e ensaio descartável #37](PREPARACAO_ATUALIZACAO_OFICIAL_0220.md).
Não repetir #37 por rotina. Não houve nova chave, instalação real, SQL ou acesso Supabase.

## Estado efetivamente observado

| Item | Resultado desta preparação |
| --- | --- |
| Repository | Público; nenhuma alteração de plano |
| Environment `release` | Existe |
| `protection_rules` | `[]`: sem aprovação configurada |
| `deployment_branch_policy` | `null`: sem restrição configurada |
| `can_admins_bypass` | `true` |
| Listagem somente dos nomes de Secrets | HTTP 403, `Resource not accessible by integration` |
| Listagem somente dos nomes de Variables | HTTP 403, mesma limitação |
| Presença/consistência atual da chave permanente | **Não verificada**; 403 não significa ausência |
| Certificado público original c40 | Identificado/verificado nas evidências anteriores; fingerprint fixado abaixo |
| Ensaio descartável #37 | 11 fases aprovadas; preservado, não repetido |
| Ensaio com assinatura permanente | **Não executado** |

Esses resultados não autorizam acesso à chave. Nenhuma proteção, Secret ou Variable
foi criado, alterado ou removido. Não se tentou contornar o 403.

## Configuração necessária pelo administrador — antes de autorizar chave

Em **Settings → Environments → release**:

1. Em Deployment branches and tags, selecionar **Selected branches and tags**.
   Adicionar uma regra do tipo **branch** exatamente `sync/academic-complete-0220`.
   Pode manter `main` se necessária aos fluxos existentes. O verificador aceita
   somente essas duas branches literais; não aceita tags, curingas ou nomes extras.
   Não usar a opção Protected branches como substituto dessa lista explícita.
2. Ativar **Required reviewers**, com pelo menos um revisor confiável, e
   **Prevent self-review**. O autor da execução não poderá aprovar o próprio acesso.
   Se não houver revisor independente disponível, a automação permanece bloqueada;
   não desabilitar a proteção para cumprir a data.
3. Desabilitar **Allow administrators to bypass configured protection rules**.
4. Conferir a existência, **somente nos Environment Secrets de `release`**, dos nomes:
   `DIARY_KEYSTORE_BASE64`, `DIARY_STORE_PASSWORD`, `DIARY_KEY_ALIAS`,
   `DIARY_KEY_PASSWORD`. Não enviar nem copiar valores para chat, Git, relatórios,
   Repository Secrets, Organization Secrets ou outro Environment para contornar o bloqueio.
   Se houver cópias em escopos mais amplos, o administrador deverá revisar sua
   exposição separadamente; esta tarefa não autoriza removê-las automaticamente.
5. Conferir a Environment Variable pública `DIARY_CERT_SHA256`:

   `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.

As opções dependem da disponibilidade real no painel. Não contratar recursos.
Se alguma não estiver disponível, informar o impedimento e manter o job bloqueado.
Essas mudanças podem exigir aprovação também nos fluxos antigos de main e impedir
execução de branches históricas de auth/foundation. Rever esse efeito antes de salvar;
não alargar a lista automaticamente.

## Caminho preparado

- Dispatcher existente: `.github/workflows/h131-investigation.yml`.
- Workflow isolado reutilizável: `.github/workflows/official-signed-upgrade.yml`.
- Controlador: `scripts/official-signing.py`.
- Verificador Java: `scripts/signing/SigningPreflight.java`.
- Prova Android: reutiliza as 11 fases da #37 por `official-upgrade-local.py`,
  com execução `PERMANENT`, sem chamar a preparação que gera chave descartável.

O dispatcher evita depender do registro de um workflow novo na main. Não há
workflow_dispatch direto no arquivo reutilizável, evento push, release/tag ou
chamada a P1/P2/P3/B. Não há `secrets: inherit` nem encaminhamento das chaves pelo
chamador. Os Secrets são resolvidos diretamente no job com `environment: release`;
o job de política não os recebe. A resolução real ainda precisa do preflight autorizado.
Não criar Repository Secrets como alternativa se algum nome estiver indisponível.

### Barreiras anteriores à chave

- Execução manual, repositório/branch/workflow exatos, GitHub-hosted runner,
  tentativa 1 e **SHA completo revisado igual ao SHA executado**.
- Flags independentes de preflight e candidato, ambas falsas por padrão.
  PREFLIGHT recusa a flag de candidato ligada; CANDIDATE exige as duas.
- Job `policy`, sem Environment e sem Secrets, verifica via API GitHub as regras
  reais acima. Falha ou metadata incompleta bloqueia o job protegido.
- Job `protected` depende do sucesso de `policy` e da aprovação nativa do
  Environment. Revalida a política antes de mapear Secrets e durante a execução.
- Somente leitura de contents/actions; sem id-token, deploy ou escrita no repositório.
  Concurrency exclusiva, sem cancelamento automático de um ensaio em andamento.
- Actions externas fixadas em SHAs de commit, resolvidos dos tags públicos
  consultados nesta preparação. Isso fixa versões; não equivale a auditoria integral
  de cada action. Gradle cache desabilitado e credenciais de checkout não persistidas.

Se o GITHUB_TOKEN do runner não conseguir ler a política, ocorrerá
`ENVIRONMENT_POLICY_UNREADABLE`, antes de abrir a chave. A capacidade de leitura
desse token específico ainda não foi comprovada em execução protegida.

### PREFLIGHT

Confere os cinco valores requeridos sem imprimir conteúdos, valida tamanho/encoding
PKCS12 e compara a variável pública ao certificado c40 fixado no código e na referência.
Decodifica o keystore somente em diretório temporário 0700/arquivo 0600.
O Java confere certificado válido, alias de chave privada e senha da chave; assina
um desafio curto em memória e verifica a assinatura com o certificado. Não gera
nem exporta chave, não assina APK e não imprime alias, senhas, cadeia ou exceções privadas.

Resultado público: booleanos, SHA de código, fingerprint público esperado e códigos
de bloqueio fixos. Store/senhas não chegam a logs, artifacts ou GITHUB_ENV. A cópia
PKCS12 é removida no finally, também em erro. Falta de chave, formato incompatível,
senha errada ou certificado diferente bloqueiam; **nenhum fallback existe**.

### CANDIDATE — somente depois de autorização própria

1. Repete as barreiras e o preflight usando a chave já existente.
2. Baixa o **original c40** da execução `41228898103`, artefato `13053517551`;
   verifica assinatura, `br.com.diariodeclasse`, 0.21.0/c40 e SHA-256
   `18335b1e7a640d733e9975d88d3f51a8cea4f73bd9e3bf536f32e90a40495a73`.
   Copia esse APK **sem reassinar** e exige identidade binária igual.
3. Compila release e instrumentação a partir do SHA revisado. A propriedade explícita
   `-PnoctuaOfficialCandidate=true` produz **0.22.0/c45**; o default continua
   0.22.0-dev-e2/c45. Não altera `BuildConfig.DEBUG`, pacote ou elegibilidade experimental.
4. Verifica o certificado do candidato contra o original; exige release não
   depurável, código 45 > 40, nome exato 0.22.0 e ausência dos marcadores de
   teste/demo/credencial administrativa na aplicação. Assina a instrumentação
   privada com a mesma chave e apaga o keystore **antes** de iniciar o AVD.
5. Em AVD novo, exige ausência dos pacotes oficial, de homologação e de testes.
   Desliga rede e comprova ausência de INTERNET antes de semear dados fictícios.
   Instala original, semeia/vincula e atualiza por `adb install -r`.
   Não há uninstall, limpeza ou downgrade.
6. Exige as 11 fases com OK nativo e sem condicionais/crash: offline, dados c40,
   proprietário vinculado, migração, conflito/fila congelados, backup/restauração,
   históricos, exclusões individuais e reabertura. Confere novamente SHA/certificado
   depois da prova; resposta incompleta ou APK alterado bloqueia o artefato.
7. Remove instrumentação privilegiada, keystore e logs privados. **A instrumentação
   assinada nunca é publicada**, pois tem privilégios sobre o pacote oficial.
8. Somente após aprovação técnica exporta `noctua-0.22.0-c45-candidato-assinado`,
   contendo APK, SHA-256 e identidade. Não é release estável, distribuição aprovada
   nem permissão para instalar no celular do proprietário. Relatórios sanitizados
   ficam separados; falhas não publicam APK.

## Parâmetros futuros — não executar sem nova autorização

Escolher e revisar um SHA completo publicado. Preencher **literalmente** `SHA_REVISADO`;
não substituir por consulta automática a um HEAD que pode ter avançado sem revisão.
Se a branch mudar entre revisão e execução, o guard reprova o SHA e não acessa a chave.

Preflight apenas:

```sh
gh workflow run h131-investigation.yml \
  --repo italocesarfr-sketch/CHATGPTCODEX --ref sync/academic-complete-0220 \
  -f suite=OFFICIAL_SIGNING_PREFLIGHT \
  -f signing_reviewed_sha=SHA_REVISADO \
  -f authorize_signing_preflight=true -f authorize_signed_candidate=false \
  -f authorize_hosted_p1=false -f authorize_hosted_b_eligibility=false
```

Após preflight aprovado e **outra autorização**, candidato:

```sh
gh workflow run h131-investigation.yml \
  --repo italocesarfr-sketch/CHATGPTCODEX --ref sync/academic-complete-0220 \
  -f suite=OFFICIAL_SIGNED_CANDIDATE \
  -f signing_reviewed_sha=SHA_REVISADO \
  -f authorize_signing_preflight=true -f authorize_signed_candidate=true \
  -f authorize_hosted_p1=false -f authorize_hosted_b_eligibility=false
```

O usuário autorizou **apenas a preparação**, portanto nenhum desses comandos foi executado.
Não usar Rerun para repetir uma falha; diagnosticar e obter autorização para uma nova execução.

Texto proposto para autorização inicial, após o administrador conferir proteções:

> Autorizo somente OFFICIAL_SIGNING_PREFLIGHT no SHA [SHA completo revisado],
> usando os Secrets existentes do Environment release após aprovação independente.
> Pode verificar a disponibilidade/consistência da chave e seu certificado c40,
> sem compilar/assinar APK, publicar candidato, consultar Supabase ou instalar aplicativo.

Texto separado para o ensaio, depois do resultado inicial:

> Autorizo OFFICIAL_SIGNED_CANDIDATE no SHA [SHA completo revisado], com a chave
> permanente já verificada, para produzir 0.22.0/c45 e testar atualização do original
> c40 em AVD novo com dados fictícios, sem reinstalação ou limpeza. Pode publicar
> o artefato candidato somente após todas as verificações. Não autorizo release
> estável, instalação real, mudanças Supabase, P1/P2/P3 ou transição de B.

## Testes desta preparação e limites

- 29 testes Python novos de autorização, política, sanitização, cleanup e bloqueio
  de artefato sem prova aprovada: passaram, zero falhas/ignorados.
- Sete provas Java reais: passaram, zero falhas/ignorados. Usaram uma cópia temporária
  PKCS12 da chave Debug **preexistente**, sem gerar chave, sem assinar APK e sem
  alterar a fixture original. Provaram leitura/senha/assinatura em memória e recusas
  de certificado permanente incompatível, senhas/alias ausentes/errados e corrupção.
  **Não comprovam a chave permanente.**
- Dez guardas anteriores do ensaio c40 e cinco de transição B: passaram, sem rede.
- `actionlint 1.7.7` nos dois workflows: zero erros de sintaxe/expressões.
  Shellcheck e pyflakes não executados; `py_compile` passou nos controladores Python.
- Gradle offline: configuração aceita; metadata explícita 0.22.0/c45 verificada
  e default 0.22.0-dev-e2 restaurado. `preReleaseBuild` sem material de assinatura
  falhou como exigido; nenhum APK foi produzido nessa verificação.
- CI #37 não repetida; nenhuma nova execução GitHub Actions, key access protegido,
  assinatura permanente ou instalação Android realizada nesta tarefa.

Pendências reais: administrador configurar políticas e conferir os nomes, leitura da
política pelo token do runner e resolução de Environment Secrets no reusable,
preflight com chave real, ensaio permanente, revisão física e aprovação do proprietário.
Falha de qualquer requisito mantém o caminho bloqueado. Não declarar APK pronto pela data.

## Funcionalidades e continuidade

P/F, busca, ordenação e atividades independentes entre turmas foram preservados.
Não mudou Room, backup, regras de frequência, notas, remanejamento ou contratos SQL.
Sincronização automática e popup de notas permanecem no código **experimental**;
o release não recebe elegibilidade artificial para contas reais.

Backlog preservado, sem implementação nesta tarefa:

| Item | Continuidade |
| --- | --- |
| Botão coletivo de presença | Revisão funcional/visual pendente; recursos coletivos históricos não foram removidos |
| Licença domiciliar | Etapa funcional própria, com validação das regras acadêmicas antes de implementar |
| Seleção automática por série | Continua bloqueada até série estruturada e migração/protocolo aprovados; [escopo atual](ESCOPO_ATIVIDADES_0220.md) |
| Ajustes visuais | Próxima etapa pequena e testável; identidade atual preservada |

Nenhum desses itens foi abandonado ou implementado implicitamente para viabilizar a assinatura.


## Publicação e ponto de retomada

Código preparado e publicado em
`de3a73c8789c03e98afad0e4203eb3cf551bf67f`.
[Evidência local e metadata pública](evidence/C45_ASSINATURA_PREPARACAO.json).
A publicação documental posterior não muda a implementação. Nenhuma execução
protegida nova consta desta preparação; o último ensaio Android continua sendo #37.

O SHA usado nos comandos deve corresponder ao **HEAD efetivamente revisado e executado**.
Se um commit documental posterior avançar a branch, usar o SHA completo desse HEAD
após revisão; o SHA de código acima não autoriza executar silenciosamente outro HEAD.

Próxima ação do administrador: configurar as proteções e conferir os nomes no painel,
sem transmitir seus conteúdos. Depois informar somente que configurou e autorizar
**apenas o preflight** no SHA revisado. A autorização do candidato fica para depois
do relatório real de preflight. Até lá, a presença da chave permanece desconhecida,
o caminho protegido permanece bloqueado e não existe APK oficial novo aprovado.
