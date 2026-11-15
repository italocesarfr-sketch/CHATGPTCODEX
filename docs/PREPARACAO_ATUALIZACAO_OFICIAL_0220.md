# Preparação da atualização oficial 0.21.0/c40 → 0.22.0

15/11/2026, America/Sao_Paulo. Meta solicitada: segunda-feira, 16/11/2026.
**Preparação e ensaio isolado; nenhuma publicação oficial autorizada nesta tarefa.**
Branch `sync/academic-complete-0220`. Não houve alteração em produção, main,
dispositivos do proprietário, assinatura permanente ou gates acadêmicos.

## Referência original efetivamente conferida

- Workflow original `41228898103`, artefato `13053517551`, fonte
  `36f58a239318c42f8293c6d0a4c7fa28747d865b`; artefato ainda não expirado na API.
- APK disponível no workspace e verificado novamente com `apksigner` e `aapt`:
  `br.com.diariodeclasse`, **0.21.0/c40**, mínimo Android 26, sem debuggable,
  assinatura v2 válida, um assinante.
- SHA-256 APK `18335b1e7a640d733e9975d88d3f51a8cea4f73bd9e3bf536f32e90a40495a73`.
- Certificado público permanente
  `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- Referência fixada em `docs/baseline/noctua-release-0.21.0.json`.
  O run `40988203896` contém **c39**, portanto não substitui esta referência.

O certificado público foi conferido. Isso **não comprova disponibilidade da chave
privada ou das senhas atuais** no Environment.

## Assinatura protegida: verificado e desconhecido

O Environment GitHub `release` existe. Sua metadata acessível retornou
`protection_rules=[]` e `deployment_branch_policy=null`; nada foi modificado.
Os jobs existentes ainda restringem suas próprias branches. Antes de adicionar
um novo job com acesso à assinatura, o administrador deve revisar essas proteções.

As tentativas de listar somente nomes/datas, sem valores, retornaram **HTTP 403,
Resource not accessible by integration**, tanto para Secrets quanto Variables.
Não houve tentativa de contornar essa permissão nem extração da chave.
A disponibilidade atual permanece **SEM VERIFICAÇÃO**.

O administrador deve conferir pessoalmente a presença destes nomes em
Settings → Environments → release, sem compartilhar os conteúdos:

- `DIARY_KEYSTORE_BASE64`
- `DIARY_STORE_PASSWORD`
- `DIARY_KEY_ALIAS`
- `DIARY_KEY_PASSWORD`
- Variable pública `DIARY_CERT_SHA256`, correspondente ao certificado acima.

Presença de nomes não basta: um futuro job protegido deverá abrir o PKCS12 em
arquivo temporário privado, verificar a chave/senha e comparar seu certificado
com a referência c40. Nunca gerar chave substituta ou pedir valores pelo chat.
**Esse job protegido não foi executado nesta preparação.**

## Pipeline existente e ensaio preparado

`android.yml:stable-update` atualmente aceita main/auth/foundation e usa baseline
0.19.1/0.20.0. Não aceita esta branch nem fixa o original c40. Foi preservado;
não é caminho já aprovado para publicar a 0.22.0.

O dispatcher existente `h131-investigation.yml` ganhou somente a modalidade
**OFFICIAL_UPGRADE_LOCAL**, que chama `official-upgrade.yml`. Não cria distribuição
oficial e não usa Environment ou Secrets. `actions: read` fica restrito ao job
que baixa o artefato original. Os jobs hospedados/P1/P2/P3 não são acionados.

```sh
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=OFFICIAL_UPGRADE_LOCAL \
  -f authorize_hosted_p1=false -f authorize_hosted_b_eligibility=false
```

Esse comando documenta a modalidade local; o resultado da execução realizada
deve ser lido abaixo antes de decidir qualquer repetição.

O ensaio:

1. Baixa o original c40 fixado e confere assinatura, pacote, versão e SHA-256.
2. Gera chave **descartável** no runner. Reassina apenas uma cópia do APK original
   com v2, mantendo todas as 125 entradas ZIP byte a byte iguais; o original não muda.
3. Compila o código atual como variante **release não depurável**, com a mesma
   chave descartável, e assina a instrumentação com essa chave de teste.
4. Exige AVD novo, nome exclusivo e nenhum pacote oficial/de homologação/teste
   preexistente. Não usa uninstall, pm clear, downgrade ou dispositivo do proprietário.
5. Desativa Wi-Fi/dados. Antes de semear dados, uma sonda nativa confere ausência de rede
   com capacidade INTERNET. Auth é fixture local; não há sessão Supabase real.
6. Instala a cópia c40 e semeia dados fictícios usando o probe existente
   `StableUpgradeProbeTest`: notas zero/nulas, F/FJ/motivos, diário, horários de
   fim de semana legados, remanejamentos, correções, exportações e recomposição.
7. `C40BoundUpgradeProbeTest` vincula o proprietário fictício usando o contrato
   existente, backup criptografado e Keystore do AVD. Preserva vínculo após a
   atualização, isolamento de outra conta, conflito v1 sintético pausado e uma
   operação v1 congelada. É sonda de API em fixture, não teste físico de PIN.
8. Atualiza com `adb install -r`, sem desinstalar. Confere snapshots, preferências,
   backup/restauração, reabertura, exclusão com histórico e isolamento.
9. Exige `academicTestEligible=false` e nenhum controle E criado no release;
   não torna uma conta de produção elegível para contornar os gates.
10. Exporta somente relatórios JSON sanitizados; remove chaves, APKs e logs
    privados. **Nenhum APK oficial ou APK de teste é publicado como artefato.**

O roteiro reutiliza as sondas de atualização existentes. A sonda de vinculação
antiga que pressupõe proprietário ainda não vinculado não se aplica depois de
`BOUND_C40`; sua cobertura é preservada na suíte anterior, e esta nova sonda
testa explicitamente o estado já vinculado antes da atualização.

## Compatibilidade local do banco e backup

Room **11 → 12 → 13 → 14**, migrações aditivas já existentes. Comparação dos
esquemas congelados 11/14: **18 tabelas anteriores sem alterações, zero removidas,
sete adicionadas**, todas de metadados acadêmicos:
`sync_academic_control`, `sync_academic_records`, `sync_academic_outbox`,
`sync_academic_inbox`, `sync_academic_applied`, `sync_academic_parts`,
`sync_academic_decisions`. Índices, FKs e definições das entidades antigas iguais.
Não há nova migração nesta tarefa. Backup lógico/criptografia/identificadores e
separação de bancos por conta permanecem inalterados.

A #33 passou 34 testes Android, incluindo migração congelada, rollback, backup,
recebimento por partes, conflitos e preservação dos 15 tipos. É evidência local;
não substitui atualização instalada do original com certificado permanente.

## Funcionalidades: candidatura local versus homologação adicional

| Funcionalidade preservada | Situação para futuro oficial |
| --- | --- |
| Segundo toque P/F remove a marcação sem criar NR | Código presente, transação/remoção/tombstone validados; candidato local após ensaio assinado |
| Busca por nome/número e ordenação em notas | Código e regressão funcional anteriores preservados |
| Atividades independentes em várias turmas | Transação/prevenção de duplicatas preservadas; seleção manual somente no mesmo ano letivo |
| Seleção automática por série | Continua desabilitada, sem inferência por nome/etapa |
| Auth, offline, dados vinculados, backup e acadêmico local | Preservados; dependem de ensaio com assinatura permanente e homologação física antes da instalação real |
| Sincronização automática A–E e popup de notas | Código mantido, **experimental DEBUG-only**; não habilitado no release por esta tarefa |
| Conflito legado v1 do proprietário | Não lido nem resolvido; a atualização deve preservar seus bytes/filas |
| Recuperação integral na produção | Não aprovada nem oferecida como garantia: produção A–E não foi implantada/autorizada |

Não retirar DEBUG-only/RLS/allowlist para liberar o oficial. Uma edição funcional
local pode ser candidata separadamente da habilitação da nuvem. Se a distribuição
pretender incluir A–E em produção, isso exige decisão de política, implantação
autorizada, migração de workspaces e homologação próprias — fora desta tarefa.
Os limites H13 continuam válidos; H13.1/H13.2 reprovadas não foram integradas.

## Ordem para chegar a um APK oficial seguro

1. Conferir o resultado do ensaio descartável e corrigir falhas comprovadas.
2. Administrador verificar os cinco nomes da assinatura e as proteções do Environment,
   sem compartilhar conteúdos. Resolver o impedimento de acesso/autorização do job.
3. Preparar/aprovar execução protegida específica c40→candidato com certificado
   permanente. Usar o mesmo original fixado e dados fictícios; sem publicação
   automática. O pipeline deve testar proprietário vinculado e conflito preservado.
4. Executar regressões relevantes do release, verificar package/code/certificado,
   exportações, backup protegido e fontes/telas. A versão atual é
   **0.22.0-dev-e2/c45**: código superior a 40, ainda sem promoção/versionamento estável.
5. Após prova assinada e autorização do proprietário, fazer homologação física em
   ambiente separado e planejar backup verificável antes da atualização real.
   Não desinstalar nem restaurar backup por cima dos dados reais para testar.

A transição de B e a repetição P1 hospedada são frentes experimentais separadas;
não são pré-requisitos para corrigir P/F e atividades **locais** no oficial, nem
autorização para enviar o acervo real do proprietário.

## Execuções e evidências desta preparação

- Consolidação #33: [B_TRANSICAO_E_0220.md](B_TRANSICAO_E_0220.md), commit documental
  `722f7d5`; não foi repetida.
- #34 / `43715285924`, fonte `1b8a375`: `startup_failure`, sem jobs/AVD. O callee
  solicitava leitura de artefatos não concedida pelo chamador; `c97ac0d` limitou
  essa permissão ao job local.
- #35 / `43715523712`, fonte `c97ac0d`: preparação recusada em
  `ORIGINAL_PAYLOAD_CHANGED`, antes do AVD. Reproduzido localmente: apksigner
  adicionava META-INF/MANIFEST.MF, COPY.SF e COPY.RSA para v1; nenhuma entrada
  original mudara. `bd5efb6` torna a cópia explicitamente v2-only. Repetição local
  confirmou todas as 125 entradas iguais e assinatura v2 válida, sem relaxar a comparação.
- Dez guardas Python novas passaram; compilação AndroidTest passou. Sem nova
  execução hospedada e sem uso de chave permanente.
- #36 / `43715998137`, fonte `bd5efb6`: compilação release não depurável c45,
  cópia original byte a byte e assinatura descartável conferidas. ADB falhou antes
  da primeira sonda; não há aprovação de instalação/migração. O artefato não
  identificou o comando ADB. [Evidência sanitizada](evidence/C40_ATUALIZACAO_36.json).
- A instrumentação seguinte identifica operação ADB e códigos fixos de erro,
  elimina o broadcast protegido opcional de modo avião e exige um probe nativo
  de ausência de internet antes de qualquer semeadura. Não atribuir a falha da #36
  a esse broadcast sem evidência: o comando exato não foi preservado.
- Resultado da rodada seguinte será registrado após exame do artefato.

Esta preparação não autoriza liberação para segunda-feira por calendário. A meta
depende dos bloqueios acima; teste ignorado/condicional ou assinatura descartável
não será apresentado como prova da atualização permanente.
