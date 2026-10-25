# NOcTuA Professor 0.18.2 — dias úteis em Horários

Base: 0.18.1 / 34. Candidato: 0.18.2 / 35. **Compilação, regressão Android e atualização release assinada aprovadas.**

O seletor principal e o campo Dia da semana no formulário comum oferecem somente segunda a sexta. Em fonte normal, os cinco botões dividem a largura disponível; em telas estreitas/fontes ampliadas, distribuem-se em linhas. Alvos de toque têm no mínimo 48 dp. Aos fins de semana, a seleção inicial é segunda-feira. O mesmo tratamento vale para uma seleção principal antiga restaurada. Um rascunho de formulário antigo de sábado/domingo exige escolher explicitamente um dia útil antes de salvar, sem converter automaticamente seus períodos.

A restrição é da interface: os modelos continuam lendo os sete dias. Nenhuma revisão, período histórico ou lançamento acadêmico é apagado. Vigências conserva a consulta histórica de sábado/domingo, e os calendários de Chamada e Diário mantêm as previsões legadas e os registros efetivos. Editar um dia útil preserva os demais dias, inclusive os fins de semana. Sem alterações de Room v9, migrações, banco diario.db, backup 11, regras acadêmicas, identificador ou assinatura.

Impeccable 4.7.0 aplicado ao refinamento nativo: contexto do alvo, orientações Android, legibilidade, alvos de toque e revisão por capturas reais. Sem alteração da identidade visual.

## Validação

- Compilação local de debug/instrumentação aprovada; 176 testes JVM, sem falhas.
- Testes Android verificam cinco seletores, ausência de sábado/domingo no formulário, seleção/listagem por dia, calendário de Chamada/Diário, fontes 1,0/1,3/1,6 e preservação integral do snapshot acadêmico.
- Teste Room dedicado mantém uma revisão antiga com sábado/domingo, P/FJ/NR e diário, edita sexta-feira, reabre o banco e restaura backup, conferindo também referências órfãs.
- O workflow de atualização utiliza o APK original assinado 0.18.1, com hash fixado em docs/baseline/noctua-release-0.18.1.json. A semente inclui grade de fim de semana para conferir preservação após instalação por cima, restauração e reinício. Configuração da chave permanente não é modificada.

A primeira rodada Android detectou corte de rótulos quando cinco chips dividiam uma largura compacta. O layout foi corrigido para usar duas linhas nessas larguras, preservando a fonte e as verificações de texto não cortado. Resultados finais e capturas registrados abaixo após execução efetiva. Somente dados fictícios em emulador descartável; validação no aparelho físico permanece manual.

## Arquivos alterados

- `app/src/main/java/br/com/diariodeclasse/ui/TimetableScreen.kt`: cinco dias, adaptação de layout e proteção do formulário.
- `app/build.gradle.kts`: 0.18.2 / 35.
- `app/src/test/java/br/com/diariodeclasse/TimetablesTest.kt`: conjunto de dias e seleção restaurada.
- `app/src/androidTest/java/br/com/diariodeclasse/TimetableUiInstrumentedTest.kt`: seletor, formulário e edição preservando o legado.
- `app/src/androidTest/java/br/com/diariodeclasse/TimetableLayoutInstrumentedTest.kt`: legibilidade, fontes e alvos de toque.
- `app/src/androidTest/java/br/com/diariodeclasse/TimetableInstrumentedTest.kt`: banco, reinício e backup com fins de semana.
- `app/src/androidTest/java/br/com/diariodeclasse/StableUpgradeProbeTest.kt`: semente histórica na release anterior e preservação após atualização.
- `scripts/ci/validate-android.sh`: contagem do grupo de horários, de 23 para 25 testes.
- `.github/workflows/android.yml` e `docs/baseline/noctua-release-0.18.1.json`: referência original 0.18.1 para o teste de atualização, sem mudança da configuração de assinatura.
- Esta documentação, resultados estruturados e capturas reais.

## Resultados executados no CI

- **176 testes JVM aprovados**, sem falhas, erros ou testes ignorados.
- **293 casos Android nos XML: 289 aprovados e quatro sondas condicionais**, sem falhas ou erros. As sondas de frequência/atualização executam com argumentos próprios; a sonda ReleaseContinuityProbe é legada. Não são contadas como aprovadas na suíte geral.
- **25 testes no grupo de horários**, incluindo seleção dos cinco dias, ausência de opções de fim de semana no formulário, troca de dia e listagem, cadastro e edição, calendário de Chamada e Diário, preservação de históricos, reinício e backup/restauração. Grupo executado também na regressão completa, sem soma como casos únicos adicionais.
- Os grupos anteriores de Alunos da turma (2), exclusão individual (14), layout (4), históricos/remanejamentos (35) e relatórios/exportação (27) passaram. Também passaram as verificações de fontes ampliadas do sistema e os leitores independentes das exportações XLSX/CSV/TSV.
- A primeira falha de legibilidade foi corrigida no layout, mantendo os testes de corte de rótulos.

[Commit de código validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/cc8a07855fa5cd19d674cf004d93db7e65f9f496) · [Execução](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40214997657) · [XML, testes e capturas completos](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40214997657/artifacts/12607650952).

## Capturas reais conferidas

| Captura | Conteúdo |
| --- | --- |
| [320 dp / fonte normal](images/horarios-dias-uteis-0182/horarios-320.png) | Cinco dias, seleção de sexta e estado vazio sem turma |
| [320 dp / fonte 1,6](images/horarios-dias-uteis-0182/horarios-320-fonte-1.6.png) | Rótulos inteiros e ações acessíveis |
| [Formulário](images/horarios-dias-uteis-0182/editar-horarios.png) | Dia útil e seis períodos, preservando a edição existente |
| [Grade após salvar](images/horarios-dias-uteis-0182/grade-apos-salvar.png) | Cinco dias na tela completa e confirmação discreta |

São capturas reais de testes Compose no emulador API 30, usando dados fictícios. Em telas compactas, os dias se distribuem em três mais dois; em larguras maiores e fonte normal, ficam em uma linha. O teclado/leitor de tela continuam usando componentes Material 3 e descrições de dia por extenso. A avaliação física e com leitor de tela permanece manual.

## Release assinada e atualização

O APK publicado foi baixado e verificado novamente: **0.18.2 / código 35**, `br.com.diariodeclasse`, assinatura v2 válida e mesmo certificado da 0.18.1 / 34. O hash do arquivo publicado coincide com o APK efetivamente testado no CI. Não houve alteração de chave, Secrets ou configuração de assinatura.

O teste usou o **APK original 0.18.1** com hash fixado, sem recompilar a referência. Em emulador descartável, instalou a referência, semeou dados fictícios e instalou a candidata com `adb install -r`, sem desinstalar. As oito fases passaram: antes, após atualizar, restauração, reinício, exclusão com histórico preservado, novo reinício, exclusão individual e reabertura. A grade de sábado/domingo foi preservada na atualização, restauração e reinício, incluindo a revisão antiga após edição anual na sonda. Também foram preservados todos os registros e preferências esperados por comparação integral de snapshots.

- **APK SHA-256:** `51c2e9516ebe4c0e1c5b5534491d71d661bd715440cc714c4eef90ad7d9da469`.
- **Certificado SHA-256:** `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` (identificação pública).
- [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40214997657).
- [APK release assinado — extrair o ZIP e instalar app-release.apk sobre a versão anterior](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40214997657/artifacts/12607866370).
- [Evidências da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40214997657/artifacts/12607756758).
- [Resultados estruturados e arquivos alterados](HORARIOS_DIAS_UTEIS_0182_RESULTADOS.json).

Room continua v9, backup formato 11 e banco `diario.db`. Não há migração nesta atualização. O APK usa o mesmo identificador e certificado; dados reais nunca foram acessados. Os artefatos exigem acesso ao repositório e seguem a retenção do Actions. As únicas pendências manuais são a avaliação no aparelho físico e com leitor de tela. Não há falhas automatizadas pendentes. O commit posterior de finalização altera somente README, documentação e capturas, preservando o código validado `cc8a07855fa5cd19d674cf004d93db7e65f9f496`.
