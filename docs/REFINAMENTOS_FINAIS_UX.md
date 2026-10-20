# Noctua — sete refinamentos finais de interface

Base auditada: `0475ab2`, Noctua 0.13.0 / 23. Incremento: **0.14.0 / 24**. Escopo exclusivamente de apresentação/navegação e entrada demonstrativa, sem autenticação.

## Alterações

1. Relatórios: indicação visual **Buscar**, com lupa e Limpar preservados. Algoritmo de busca global, turmas arquivadas, anos e filtros inalterados.
2. Ações principais: **Organizar** e **Exportar**, lado a lado e com larguras equivalentes, texto centralizado em uma linha.
3. Ambos usam o mesmo `OutlinedButton`, padding, altura mínima de 48 dp, cores e estados. Nenhum preenchimento permanente de seleção.
4. Rodapé: vetor nativo de calendário, rótulo **Chamada e Diário** e acessibilidade atualizada. As quatro páginas e destinos permanecem iguais. Espaço de rótulo reservado igualmente entre abas; até três linhas em fontes ampliadas, sem reduzir fonte. Coruja oficial permanece no launcher e cabeçalho.
5. Organizar exportação: mesmos modos, cartões e preferências. Controles de salvar/cancelar/concluir e exportar agrupados no rodapé acessível; cartões com padding consistente, checkbox centralizado e setas de 48 dp para mover. Linhas vazias, ocultação e confirmação mantidas. Não há novo editor nem alteração na ordenação dos dados.
6. Exportar: prévia antecipada, espaçamento menor e ações de arquivo/cópia reunidas no rodapé. Chamada e relatório anual agrupados em **Outras planilhas**. Opções continuam recolhíveis. Mesma matriz, cabeçalhos, células, formatos e execução de geração/gravação e clipboard. O feedback da exportação usa snackbar local acima das ações, evitando cobri-las. Ações têm rolagem própria quando necessário para não bloquear conteúdo com fonte ampliada. Scaffolds internos não duplicam insets do Android.
7. Entrada demonstrativa com logo oficial, Noctua, E-mail, Senha e Mostrar/Ocultar, Entrar, Criar conta e Esqueci minha senha. Identificação explícita: **Prévia de entrada • acesso livre, sem autenticação.** Entrar funciona com campos vazios ou quaisquer valores. Os outros botões mostram somente **Funcionalidade disponível em uma versão futura.** Nenhuma conta é criada.

## Entrada: estado e privacidade

`NoctuaStart` envolve a interface existente. O ViewModel continua sendo criado no início da Activity, mantendo a leitura e as migrações existentes antes de Entrar; a prévia não interfere na inicialização do Room. Somente o booleano de navegação usa SavedInstanceState. Os campos usam `remember`, sem Repository, preferências, arquivos, logs ou serviços; são descartados ao entrar ou recriar a tela. Não há permissão de internet no APK, nem dependência de autenticação/autofill adicionada.

Cold launch mostra a prévia. Entrar substitui a tela na composição, sem adicioná-la a uma pilha. Recriar a Activity depois de entrar conserva o trabalho e não volta à prévia. Voltar na raiz encerra a Activity como antes. Retomar uma Activity existente não obriga nova passagem. Essa flag não é sessão autenticada nem proteção de acesso. A seleção persistida de ano/turma continua sendo a já existente.

A página é rolável, considera barras do sistema e teclado, tem teclado E-mail e Password e ações de toque adequadas. O aviso de versão futura dispensa o teclado e usa snackbar; nunca simula recuperação ou criação de conta.

## Integridade e assinatura

Sem mudança em entidades, esquema Room v7 (14 tabelas), migrações, `diario.db`, backup formato 8, IDs, repositórios, cálculos, escritores XLSX/CSV, remanejamento ou exclusão. Pacote `br.com.diariodeclasse`. Chave, Secrets e workflow manual de release preservados.

A referência do teste de atualização passa a ser o APK release 0.13.0 real: run 39421673236, artefato 12263754040, SHA-256 `83a237ad090321f1b14481dcfa3ab6a7e77c1d3fc2a975158db0f1c09a5ed9d9`. A chave existente é usada exclusivamente pelo pipeline já configurado.

## Testes e validação

Regressões antigas atravessam o botão Entrar real via regra JUnit comum, sem bypass no código de produção. Novos testes cobrem entrada livre, aviso futuro, mostrar/ocultar, campos vazios após recriação, dados/preferências idênticos, ausência de permissão de internet, Voltar, nomes/ícone novos, destinos, igualdade visual dos botões, fontes ampliadas, prévia antes da metade da tela e acessibilidade das ações.

O smoke verifica primeiro os botões com fontes 1,0 e 1,6; a regressão completa continua obrigatória. O encaixe é validado pelo texto completo em uma linha, ausência de reticências/linhas omitidas, tamanho dos caracteres e posição real do nó dentro do botão. Não se compara a largura reservada ao parágrafo centralizado com a largura intrínseca das letras: essas áreas são diferentes no Compose.

### Problemas encontrados e corrigidos

- Descrição acessível duplicada da aba: o calendário é decorativo e o rótulo acessível fica no controle da aba.
- Snackbar externo sobrepunha uma ação de cópia: o feedback local fica acima do rodapé; a comparação exata dos textos copiados foi preservada.
- Métrica inadequada no teste de texto centralizado: substituída pela geometria dos caracteres e do nó, sem reduzir fonte ou remover verificações de texto completo.
- Busca recursiva de semântica da fixture anual podia ocorrer durante recomposição: os testes aguardam a projeção e navegam por índices conhecidos, mantendo as asserções de todos os resultados.
- Composição condicional adiava a criação do ViewModel e a abertura do banco até Entrar: a inicialização de leitura foi restaurada no início da Activity, como antes. As migrações existentes e sua sonda rigorosa não foram alteradas.

As execuções intermediárias 39428315953, 39436207411, 39440477064, 39443022929, 39444913800 e 39446084063 registraram os problemas de interface/automação acima. A execução 39448262853 aprovou 113 JVM, 207 Android, o smoke, 41 XLSX, 41 CSV e 123 TSV; a sonda posterior de atualização legada identificou a inicialização adiada, portanto esse workflow não foi aprovado nem gerou release para entrega.

### Resultado final aprovado

[GitHub Actions — execução 39449951397](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39449951397), commit de código `c8b86781c2fe757a8d782efdb588635d53ee91cf`: **ambos os jobs aprovados**. Nesta retomada foram conferidos os relatórios e artefatos da execução concluída, sem repetir testes aprovados nem alterar o aplicativo.

- 113 testes JVM: zero falhas/erros.
- 207 testes Android aprovados; XML contém 211 casos e quatro sondas condicionais fora da execução genérica, sem falhas/erros.
- Smoke de encaixe dos botões aprovado, além da execução na suíte completa.
- Frequência após reinício: sondas `write` e `read` aprovadas.
- Atualização legada v1→v7: sondas antes/depois aprovadas, preservando as oito tabelas originais da fixture; a inicialização antecipada corrigiu a falha anterior.
- 41 XLSX reais, 41 CSV e 123 TSV conferidos por leitura independente; inclui notas, chamada e relatório anual.
- Atualização **release 0.13.0 → 0.14.0** com `adb install -r`: seis fases aprovadas (`before`, `after`, `backup`, `reopened`, `deleted-history`, `reopened-history`). Preservação de registros, preferências, backup/restauração e históricos após exclusão/reinício comprovada com dados fictícios.

[Relatórios da regressão](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39449951397/artifacts/12279525803) · [Evidências da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39449951397/artifacts/12279591765) · [Resultados estruturados](REFINAMENTOS_FINAIS_UX_RESULTADOS.json).

### APK release verificado

[Baixar Noctua 0.14.0 / código 24 — release assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39449951397/artifacts/12278952603). Baixar o ZIP e extrair `app-release.apk`. O arquivo baixado foi verificado localmente com `apksigner` (assinatura v2 válida), `aapt` e comparação com o hash do APK efetivamente testado no emulador.

- Pacote: `br.com.diariodeclasse`; versão `0.14.0`; código `24`; tamanho: **8.520.424 bytes**.
- SHA-256 do APK: `798b5fa12cb71eda28356086e5f8177e1e6e1b9240e27d35deb7efe2f22e06d2`.
- SHA-256 do certificado público: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` — idêntico ao da release 0.13.0. Nenhuma chave ou Secret foi criado/substituído.
- Esquema Room v7 e backup 8 inalterados; hash do esquema: `45e14573bede8c237e55bd2bf21dea38d256e650686bb9b2226f0269ad1f1510`.

A compatibilidade de atualização foi comprovada no emulador com a release 0.13.0 real. O teste da nova versão no aparelho físico do proprietário continua pendente. As planilhas foram lidas com openpyxl; não há alegação de abertura no Microsoft Excel instalado. Provedores Android de outros fabricantes e outras dimensões/escalas de fonte ainda precisam de validação manual.

### Capturas da versão validada

Capturas do emulador, somente com dados fictícios:

| Tela | Captura |
|---|---|
| Entrada demonstrativa | [Abrir](images/refinamentos-finais-ux/entrada.png) |
| Relatórios, Buscar e ações com contorno | [Abrir](images/refinamentos-finais-ux/relatorios.png) |
| Organizar, ocultação e controles agrupados | [Abrir](images/refinamentos-finais-ux/organizar.png) |
| Exportar com prévia antecipada | [Abrir](images/refinamentos-finais-ux/exportar.png) |
| Ações em 320 dp, fonte 1,6 | [Abrir](images/refinamentos-finais-ux/acoes-fonte-ampliada.png) |
| Navegação em 320 dp, fonte 1,3 | [Abrir](images/refinamentos-finais-ux/navegacao-fonte-ampliada.png) |

A documentação final não modifica o aplicativo testado. O commit acima identifica exatamente o código do APK; os commits posteriores de documentação não exigem nova compilação.

## Arquivos alterados

Inventário da implementação e dos testes (em relação à base 0.13.0; README, resultados e capturas são a documentação de entrega):

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/br/com/diariodeclasse/ActivityChangesUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/BackupDialogInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ExportLayoutUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ExportLifecycleUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/FinalUxEntryInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/FinalUxLayoutInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/FrequencyUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/LessonRecordsUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/NoctuaEntryTestRule.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/NoctuaIdentityInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PostTwelveUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PromptTwoUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/RecompositionUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/RelocationUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ReportsUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/StageSixUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/StageTwoUiInstrumentedTest.kt`
- `app/src/main/java/br/com/diariodeclasse/MainActivity.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/Components.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DemoEntryScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DiaryApp.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ExportLayoutScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ExportScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ReportsScreen.kt`
- `app/src/main/res/drawable/ic_calendar.xml`
- `docs/ESPECIFICACAO.md`
- `docs/REFINAMENTOS_FINAIS_UX.md`
- `docs/ROADMAP.md`
- `docs/baseline/noctua-release-0.13.0.json`
- `scripts/ci/validate-android.sh`
