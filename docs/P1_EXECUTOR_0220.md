# P1 — recuperação automática pela UI em dois AVDs

Continuação de [HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md](HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md).
Somente preparação/local nesta entrega; P1 hospedada, P2 e P3 não autorizadas.

## Etapa 1 — APK paralelo atualizado

[Build #208 / 43632816764](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764),
`success`, fonte `6f3ae05f8ccdd918600f99c2ef5b6c6ee6d15474`, cujo código coincide
com o aprovado `b8206c3`. Workflow `homologacao.yml` via dispatcher existente,
sem login hospedado. [APK paralelo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764/artifacts/14128872479).

- `br.com.diariodeclasse.homologacao`, NOcTuA Homologação.
- `0.22.0-dev-e2-homologacao` / código 45, preservados.
- SHA-256 APK `3aa87abdc60f2f42184ca9a9a90a5783d3c09e97d60ab8a9954cea191252bc35`.
- Certificado Debug `8e9b7b97b14914182d42a6be2e1ed0f32a92a48926cbc94288a983ae9e2676a5`.
- Assinatura/AAPT/DEX/relatório de identidade conferidos offline. Endpoint exclusivo
  `sscexzxtqdcsqdlcytum`, callback próprio; novas funções presentes e marcadores
  privados/produção ausentes. O vínculo da chave opaca não foi consultado online.
- Certificado diferente do #206: **não atualiza aquela instalação**. Nenhum APK
  foi instalado nesta etapa; usar somente AVDs novos, jamais limpar/desinstalar
  aplicações existentes para contornar incompatibilidade.
- JVM Debug: 313 aprovados, cinco condicionais ignorados; Homologação: os mesmos
  313 aprovados/cinco ignorados, zero falhas. Não somar como 626 casos distintos.

[Identidade e hashes estruturados](evidence/APK_P1_208.json).

## Etapas 2/3 — executor

Executor de instrumentação separado do aplicativo, via `P1RecoveryProbeTest` e
`P1RecoverySupport`. A Application executa seu `onCreate` normal, com cofre
Keystore vazio e autenticação por `entry-email`/`entry-password`/`entry-enter`.
O teste não chama `activate()`, `run()`, `request()` nem controles de recuperação.
A recuperação pertence ao startup/coordenador aprovado. O logout usa a UI.

Dois AVDs novos, nomes vinculados ao run/attempt, instalações sem `-r`; qualquer
pacote previamente instalado, dispositivo extra ou runner não descartável bloqueia
o executor. Nenhum `pm clear`, uninstall ou instalação em dispositivo existente.
Cada AVD verifica A → B → A, inventário/revisões/recibos/filas/cursor/FKs,
hashes por tipo e imutabilidade do inventário e auditorias remotos.

A barreira reutiliza `H05ReadOnlyRemote`, que recusa as dez operações mutantes
antes de delegar ao HTTP. Somente status/pull E e SELECTs fixos de auditoria e
isolamento são permitidos; Auth admite exclusivamente login/verify/refresh/logout
das próprias sessões. Cadastro, recuperação, alteração de senha e troca PKCE
não são permitidos neste ensaio. H05/H12 continuam inalterados.

LOCAL ignora Secrets e utiliza Auth sintética com PostgreSQL/PostgREST reais,
fixtures A=171 registros/15 tipos e B=6 registros/3 tipos. O caso B vazio/E/head=0
possui guarda adicional própria; não relaxa os limites do H05. HOSTED exige
contas confirmadas, protocolo 2/escopo E/allowlist em tempo de execução, sem
ativar escopos remotamente. A ausência de elegibilidade bloqueia a prova.

Limites: 1000 registros vivos/por lote, 4096 versões, 8 MiB de inventário,
128 fragmentos, 256 lotes, 512 leituras/instalação, 300 s de prova e 30 s de
cleanup, três logins por AVD (seis no conjunto). Ausência de cobertura de um
tipo na fixture hospedada permanece explícita nos quinze contadores.

Credenciais chegam por stdin a um arquivo 0600 no sandbox do novo aplicativo,
são removidas no finally; logs nativos/build permanecem em temporário privado.
Somente relatório com vocabulário fixo, booleanos, tempos, contagens e hashes é
publicado. Nenhum e-mail, UUID, dado acadêmico ou token é permitido no relatório.

O workflow reutilizável `p1-recovery.yml` tem modo PREFLIGHT padrão e autorização
HOSTED independente, desativada por padrão. É chamado pelo dispatcher existente
`h131-investigation.yml`, seleção P1_*; não integra candidatos H13.1/H13.2.
LOCAL não recebe Secrets/Environment. HOSTED/PREFLIGHT usam apenas os dois Secrets
nomeados no Environment já existente. PREFLIGHT não faz requisições nem gera AVD.

### Parâmetros futuros — somente após autorização específica

```sh
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=P1_PREFLIGHT -f authorize_hosted_p1=false
# NÃO executar a linha seguinte sem a autorização específica P1:
gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 \
  -f suite=P1_HOSTED -f authorize_hosted_p1=true
```

A prova usa target/test APKs compilados juntos, com o mesmo certificado descartável,
porque a chave Debug varia entre jobs. Isso não prova identidade binária com o
APK #208: o relatório registra o SHA/certificado do par efetivamente instalado.
O código principal deve permanecer idêntico ao código validado; nenhuma chave
privada permanente é solicitada. As duas instalações recebem os mesmos bytes.

### Situação da validação

46 testes Python locais passaram (P1 15, H05 15, Auth 16). A compilação Debug
passou. A prova de dois AVDs será registrada após execução; não declarar P1 pronta
para autorização enquanto faltar esse resultado. Nenhuma P1 hospedada, P2/P3 ou
consulta de elegibilidade hospedada foi executada nesta preparação.
