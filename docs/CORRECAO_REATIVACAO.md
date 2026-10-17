# Correção 0.7.1 — Reativação e remanejamento

Base: **Noctua 0.7.0 / código 11**. Correção entregue: **0.7.1 / código 12**. Escopo exclusivo: consistência da reativação e validações diretamente relacionadas. Nenhuma Etapa 5, regra pedagógica, reformulação visual ou exportação.

## Reprodução e causa confirmada antes da correção

O teste Room `transferredThenReactivatedEnrollmentCanRelocateAfterReopenWithoutLosingHistory` foi publicado e executado antes de modificar produção. Na [execução 39065254583](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39065254583), a sequência Transferido → Ativo → fechar/reabrir banco → conferir remanejamento em 17/10/2026 falhou com a mensagem exata: **“Esta matrícula já foi encerrada/remanejada. Consulte o histórico.”** Os demais testes da suíte Android não falharam.

No `StudentForm`, o seletor alterava somente `status`. A data `left` continuava preenchida e era reenviada no salvamento. O ViewModel encaminhava esses valores pela fila existente, sem convertê-los. `saveStudent` aceitava e persistia `Ativo` com a saída antiga. Não era só um texto desatualizado na tela: após reabrir Room, o status era Ativo e o intervalo permanecia encerrado. `previewRelocation` reunia status, saída e vínculo em uma única condição, produzindo a mensagem genérica.

Uma transferência administrativa simples é uma edição de situação/saída. Um remanejamento consolidado possui vínculo em `relocations`, que não pode ser apagado ou reaberto pelo formulário. Transferências administrativas legadas também podem possuir outra matrícula da mesma pessoa sem vínculo explícito; essa condição exige proteção contra sobreposição.

## Comportamento corrigido

- Ao salvar uma alteração explícita de **Transferido para Ativo**, a saída anterior é retirada na mesma transação. O formulário informa esse efeito antes de salvar. Ingresso, saída alternativa e reativação devem ser confirmados separadamente.
- Uma edição de nome, número ou observação de um aluno já Ativo **não** retira sua saída. Datas de saída legítimas continuam válidas. Abrir o aplicativo, navegar, atualizar ou restaurar backup nunca reativa matrículas automaticamente.
- O cadastro Ativo com saída oferece **Confirmar reativação**. A confirmação identifica a matrícula/data e só retira a saída se o registro ainda for exatamente o conferido. Cancelar não grava nada. O formulário fecha após o reparo, para que qualquer remanejamento use o estado confirmado.
- IDs de matrícula e pessoa, ingresso, observações, notas, chamadas e recomposições permanecem preservados. Nenhuma presença, falta, identidade, matrícula ou vínculo é criado pela reativação.
- Matrículas com saída de remanejamento consolidado, com entrada vinculada cujos limites seriam alterados, ou com outra matrícula da mesma pessoa sobreposta no mesmo ano são recusadas. Não há fusão por nome nem inferência de vínculo legado.
- Mensagens distinguem matrícula encerrada, já remanejada, destino igual à origem, data inválida e estado Ativo com saída que exige conferência.

## Teste final no mesmo celular

1. Antes da atualização, salve um backup externo válido pelo aplicativo. Instale o **APK release** 0.7.1, sem desinstalar nem limpar os dados.
2. Em **Cadastrar Turma → Gerenciar alunos**, edite a mesma aluna fictícia do 1º X.
3. Se ela está Ativa com saída, toque **Confirmar reativação**, confira a data antiga e confirme somente se a matrícula foi legitimamente reativada. Se existem outras edições no formulário, reabra o cadastro antes de confirmar o reparo.
4. Reabra a edição da mesma matrícula, selecione Remanejado, destino 1º G e data 17/10/2026. Confira a prévia e confirme.
5. Consulte o histórico na origem e os registros consolidados no destino. Não exclua nem recadastre a aluna.

Uma transferência ainda válida deve permanecer encerrada. Se houver movimento consolidado, outra matrícula da mesma pessoa, lançamento na origem após a data efetiva ou destino incompatível, a operação será recusada com motivo específico; os registros não serão modificados.

## Compatibilidade e assinatura

Sem alteração de entidades, DAO, consultas, esquema Room **v3**, migrações ou backup **formato 4**. Permanecem `br.com.diariodeclasse`, `diario.db`, dez tabelas, IDs, preferências, leitores dos backups anteriores e funcionamento offline. Não há migração de dados nem correção em massa; o reparo é explícito e transacional.

O workflow manual `release.yml` e a configuração de assinatura permanecem intactos. A referência do teste de atualização é a release real **0.7.0**, execução **39055280256**, artefato **12109269070**, fixada por checksum e certificado em [baseline/noctua-release-0.7.0.json](baseline/noctua-release-0.7.0.json). O CI reutiliza exclusivamente a chave já configurada pelo proprietário, sem criar/substituir chaves ou modificar Secrets. O ensaio é restrito a emulador descartável e dados fictícios; nunca executá-lo em aparelho físico.

A semente é criada pelo próprio APK 0.7.0: todas as dez tabelas, remanejamento consolidado anterior, matrícula legitimamente encerrada e a sequência que produziu Ativo com saída residual, incluindo notas zero, recomposição e P/F/FJ. A atualização deve preservar tudo antes de qualquer reparo. Depois, testa-se reparo confirmado, novo remanejamento, ausência de duplicação, backup/restauração e reabertura. A sonda `FrequencyProcessProbeTest` também executa Transferido → Ativo, aguarda o commit, sofre `force-stop`, reabre e só então remaneja, conferindo notas, frequência, recomposição, IDs e contagem de matrículas.

## Resultados verificados — 17/10/2026

[Baixar APK release com a assinatura existente](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492/artifacts/12113923175) • [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492) • [Testes e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492/artifacts/12113841690) • [Provas da atualização](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492/artifacts/12114151999). Código testado: [`03631ca`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/03631ca9b8eaf80f99612a927ac1975881b2ef84). O commit documental posterior conserva esse código.

| Verificação | Resultado |
| --- | --- |
| Reprodução anterior à correção na 0.7.0 | Falha esperada com a mesma mensagem do aparelho; 101 testes anteriores aprovados |
| JVM | 62 aprovados, zero falhas, localmente e no CI; sete novos testes de reativação |
| Android API 30 / Room / Compose | 109 aprovados, zero falhas; seis novos testes Room e dois Compose |
| Encerramento real do processo | Duas fases aprovadas; Transferido → Ativo, commit, force-stop, reabertura e remanejamento preservando notas/chamadas/recomposição |
| Atualização legada | Duas fases aprovadas; migrações aditivas anteriores preservadas |
| Release real 0.7.0 → 0.7.1 | Quatro fases aprovadas: semente na 0.7.0, atualização sem desinstalar, reparo/backup/restauração e reabertura |
| Preservação | Dez tabelas e preferências conferidas; matrícula já remanejada e matrícula realmente encerrada intactas |
| Backup/restauração | Estado anterior com saída residual e estado após reparo/remanejamento preservados; formatos anteriores continuam cobertos |
| Falhas e cancelamentos | Cancelar confirmação, reparo com trigger de falha e formulário obsoleto não alteram registros |
| Esquema | Room v3 e JSONs v1/v2/v3 idênticos à 0.7.0; nenhuma nova migração necessária |
| Compilação | `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease` aprovados |
| Assinatura | Verificação criptográfica e instalação por cima aprovadas com o mesmo certificado da release 0.7.0 |
| Regressão Java | 28 cenários aprovados |

Quatro sondas exclusivas ficam ignoradas na suíte Android normal. Três sondas ativas são executadas separadamente em oito fases. A sonda histórica `ReleaseContinuityProbeTest` e seu script de criação de chaves não foram executados. Nenhuma chave permanente, certificado ou Secret foi criado/substituído/modificado. Todos os dados de teste são fictícios.

A [primeira validação da correção](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39066149593) aprovou 62 JVM e 109 Android. Sua etapa release foi substituída por esta execução, que inclui a sonda reforçada de encerramento real do processo antes de remanejar. A execução seguinte ([39066771184](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39066771184)) encontrou uma corrida em um teste antigo de relatórios: ele aguardava um texto também presente no resumo anual e consultava a lista durante a recomposição. O teste passou a aguardar o cabeçalho do bimestre corrente e a ociosidade da interface, sem alterar o código dos relatórios. A regressão completa foi repetida na candidata final; a falha inicial de reprodução permanece separada.

Os APKs e testes foram compilados localmente com JDK 17 / SDK 35. O ambiente local restringia inicialmente o socket do daemon Gradle; a execução com acesso de rede permitido compilou e executou os testes JVM. Testes Android e atualização assinada ocorreram no emulador descartável do CI. Os ZIPs finais foram baixados, os XMLs JUnit e as oito fases foram conferidos, assim como checksum, manifesto binário e assinatura do APK.

- Release: **7163342 bytes**, pacote `br.com.diariodeclasse`, versão **0.7.1 / código 12**, Android 8.0+.
- Caminho verificado: `app/build/outputs/apk/release/app-release.apk` (absoluto `/workspace/diario-de-classe/app/build/outputs/apk/release/app-release.apk`).
- SHA-256 do APK: `6aee7f25856cb98f5c42170b3a858aa853374d8f5c28fb7cff7f550f632ab2f0`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- Esquema v3 SHA-256: `3e087b839b8c286589b1d7cabc0e7d2e8e83645728d1df757c05e32a95b5c7ce`.
- [Metadados completos](baseline/reativacao-validacao.json).
- [APK debug interno](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39067364492/artifacts/12113373259): certificado diferente da release; não é indicado para atualizar a instalação release.

## Arquivos alterados

| Arquivo | Alteração |
| --- | --- |
| `data/DiaryRepository.kt` | Transição consistente, reparo confirmado/atômico e validações com mensagens separadas |
| `data/EnrollmentReactivation.kt` | Guardas compartilhadas contra remanejamento consolidado e matrícula legada sobreposta |
| `ui/Forms.kt` | Aviso ao reativar e confirmação específica para Ativo com saída residual |
| `app/build.gradle.kts` | Somente versionCode 12 e versionName 0.7.1 |
| `EnrollmentReactivationTest.kt` | Sete testes JVM |
| `RelocationInstrumentedTest.kt` | Seis regressões Room, incluindo a reprodução original |
| `RelocationUiInstrumentedTest.kt` | Dois fluxos Compose adicionais |
| `ReportsUiInstrumentedTest.kt` | Sincronização do teste com o cabeçalho do bimestre antes da rolagem |
| `FrequencyProcessProbeTest.kt` | Reativação antes e remanejamento depois do force-stop, restrito ao emulador |
| `StableUpgradeProbeTest.kt` | Semente na release real 0.7.0, dez tabelas, reparo e restauração |
| `.github/workflows/android.yml` | Referência 0.7.0 fixada e nomes dos artefatos; assinatura existente mantida |
| `README.md`, `docs/ESPECIFICACAO.md`, `docs/ROADMAP.md`, este documento e dois JSONs em `docs/baseline/` | Uso, referência pública e resultados |

Os caminhos `data/` e `ui/` são relativos a `app/src/main/java/br/com/diariodeclasse/`; os testes ficam nos diretórios `test` e `androidTest` existentes. `Database.kt`, entidades/DAO, migrações, regras pedagógicas, cálculos/relatórios, backup/restauração, preferências e workflow manual `release.yml` não foram alterados.

## Limitações e próximo teste

A atualização foi comprovada sobre a release 0.7.0 de referência, com o mesmo certificado, preservando os dados sintéticos. Não foi inspecionado o certificado do APK instalado fisicamente pelo proprietário. A correção ainda precisa do teste final no mesmo aparelho e na mesma matrícula fictícia, seguindo o procedimento acima, sem excluir ou recadastrar. API 26, tablets e bases extensas não receberam novos ensaios nesta correção.

A confirmação é intencional: uma data de saída pode ser legítima. Não há reativação automática de todas as matrículas com saída. Conflitos de identidade ou remanejamentos já consolidados permanecem bloqueados; este incremento não cria desfazer/fusão de históricos. Artefatos APK têm retenção de 30 dias; guarde o APK/backup e não faça downgrade ou desinstalação para contornar incompatibilidade.

Somente o bug de reativação/remanejamento e suas validações foram corrigidos. A Etapa 5 aguarda autorização.
