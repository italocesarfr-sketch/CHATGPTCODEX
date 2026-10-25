# NOcTuA Professor 0.18.1 — layout de Alunos da turma

Base: 0.18.0 / 33. Incremento visual: 0.18.1 / 34. **Compilação, regressão Android e release assinada aprovadas.**

A tela conserva título, subtítulo, ações e ordenação existentes. Cabeçalho e controles são separados por 24 dp. Adicionar aluno fica em largura total; Colar lista e Importar CSV permanecem lado a lado, separados por 12 dp e com alturas iguais, que crescem quando o texto ocupa mais linhas. Há 16 dp entre os grupos de ações. O rótulo Organização da lista fica a 8 dp do seletor. A lista conserva seus cards e recebe 24 dp de separação em relação ao último controle.

Todos os blocos seguem o padding lateral existente de 16 dp. Tipografia, cores, formas e callbacks são preservados. O componente Choice recebe somente um parâmetro opcional labelSpacing, com padrão zero: somente esta tela solicita o intervalo de 8 dp; os demais seletores não mudam.

Não há alteração de cadastro, importação, numeração, movimentações, exclusão, cálculos, banco, migrações, backup ou assinatura. Nenhuma dependência adicionada. O incremento de versão permite instalar a build posterior sobre a anterior.

Arquivos de implementação: ui/RosterScreen.kt, ui/Components.kt (sob app/src/main/java/br/com/diariodeclasse) e app/build.gradle.kts. Validação: RosterLayoutInstrumentedTest, scripts/ci/validate-android.sh e coleta de artefatos em .github/workflows/android.yml.

Impeccable 4.7.0 consultado: contexto do alvo, layout, craft e Android. Refinamento da interface existente, sem nova identidade visual.

Os testes usam 100 matrículas fictícias, larguras de 320/360 dp e fontes 1,0/1,3/1,6. Conferem geometria, alinhamentos, legibilidade, alvos de 50 dp das ações, rolagem até o fim da lista e abertura dos formulários existentes. O snapshot escolar deve permanecer idêntico após navegar e ordenar. As capturas reais foram coletadas no emulador e inspecionadas, sem necessidade de novos ajustes.


## Resultado executado

- **175 testes JVM aprovados**, sem falhas.
- **291 casos Android nos XML: 287 aprovados e quatro sondas condicionais**, sem falhas ou erros. As sondas de frequência/atualização têm execução específica; a sonda ReleaseContinuityProbe é legada. Não são contadas como aprovadas na suíte geral.
- Os **dois testes novos de layout/fluxos** passaram no grupo focado e na regressão completa. Conferem separação dos grupos, alinhamento de margens, botões de mesma altura, texto não cortado, rolagem por 100 alunos e abertura dos formulários atuais. Ordenar e navegar não alterou o snapshot escolar.
- Os grupos anteriores de exclusão (14), horários (23), layout anterior (4), regressão (35) e relatórios/exportação (27) passaram. São sobrepostos à suíte completa, sem soma como casos únicos adicionais.
- Os leitores independentes das exportações XLSX/CSV/TSV também passaram.

## Capturas reais do emulador

| Captura | Conteúdo |
| --- | --- |
| [320 dp / fonte normal](images/alunos-layout-0181/alunos-320.png) | Cabeçalho, ações, seletor e início dos cards |
| [320 dp / fonte 1,6](images/alunos-layout-0181/alunos-320-fonte-1.6.png) | Ações lado a lado, altura adaptada ao texto |
| [360 dp / fonte 1,3](images/alunos-layout-0181/alunos-360-fonte-1.3.png) | Organização e separação em relação à lista |
| [Fim da lista de 100 alunos / fonte 1,6](images/alunos-layout-0181/lista-longa-fonte-1.6.png) | Rolagem até o último aluno, com dados legíveis |

Os nomes fictícios foram numerados em ordem inversa à chamada para verificar a ordenação visual sem alterar os números oficiais. Nenhum dado real foi usado.

- [Código validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/3ca40f625fbb01d98bdacea4709f59d360cb68c6).
- [GitHub Actions](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40200325343).
- [Testes, XML e capturas completos](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40200325343/artifacts/12601368073).

Em telas compactas e fontes ampliadas, a lista exige rolagem. Não houve redução de fontes nem alteração dos cards. A conferência final no aparelho físico permanece manual.


## Build instalável e preservação

A assinatura v2 foi verificada e o APK publicado foi baixado para nova conferência local. Ele contém `br.com.diariodeclasse`, versão **0.18.1 / código 34**, com o mesmo certificado do APK **0.18.0 / 33** e código maior. Room v9, migrações, backup 11, dados e preferências não foram alterados. A configuração de assinatura e os Secrets permanecem intactos.

O workflow existente também aprovou as oito fases de atualização sobre a referência original 0.17.3, incluindo instalação por cima, restauração, reinício, históricos e exclusão individual. A comparação local com a 0.18.0 comprova identidade e compatibilidade de versão; a atualização com dados no emulador utiliza a referência 0.17.3 desse workflow. Não foi feita desinstalação nem acesso a dados reais.

- **APK SHA-256:** `5c7de9a6296932d045678f17513a294aeeeb0c2634e85c84583546019a9f3dfc`.
- **Certificado SHA-256:** `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` (identificação pública).
- [APK release assinado — extrair o ZIP e instalar app-release.apk sobre a versão anterior](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40200325343/artifacts/12601513476).
- [Evidências da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40200325343/artifacts/12601503509).
- [Resultados estruturados e lista de arquivos](ALUNOS_LAYOUT_0181_RESULTADOS.json).

Os artefatos mantêm seus nomes históricos do Actions; o APK release verificado é 0.18.1 / 34. Downloads exigem acesso ao repositório e seguem a retenção do Actions. Não há falhas de testes pendentes. A finalização posterior ao commit de código altera somente README, documentação e capturas.
