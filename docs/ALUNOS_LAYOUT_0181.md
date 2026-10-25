# NOcTuA Professor 0.18.1 — layout de Alunos da turma

Base: 0.18.0 / 33. Incremento visual: 0.18.1 / 34. Validação final pendente.

A tela conserva título, subtítulo, ações e ordenação existentes. Cabeçalho e controles são separados por 24 dp. Adicionar aluno fica em largura total; Colar lista e Importar CSV permanecem lado a lado, separados por 12 dp e com alturas iguais, que crescem quando o texto ocupa mais linhas. Há 16 dp entre os grupos de ações. O rótulo Organização da lista fica a 8 dp do seletor. A lista conserva seus cards e recebe 24 dp de separação em relação ao último controle.

Todos os blocos seguem o padding lateral existente de 16 dp. Tipografia, cores, formas e callbacks são preservados. O componente Choice recebe somente um parâmetro opcional labelSpacing, com padrão zero: somente esta tela solicita o intervalo de 8 dp; os demais seletores não mudam.

Não há alteração de cadastro, importação, numeração, movimentações, exclusão, cálculos, banco, migrações, backup ou assinatura. Nenhuma dependência adicionada. O incremento de versão permite instalar a build posterior sobre a anterior.

Arquivos de implementação: ui/RosterScreen.kt, ui/Components.kt (sob app/src/main/java/br/com/diariodeclasse) e app/build.gradle.kts. Validação: RosterLayoutInstrumentedTest, scripts/ci/validate-android.sh e coleta de artefatos em .github/workflows/android.yml.

Impeccable 4.7.0 consultado: contexto do alvo, layout, craft e Android. Refinamento da interface existente, sem nova identidade visual.

Os testes usam 100 matrículas fictícias, larguras de 320/360 dp e fontes 1,0/1,3/1,6. Conferem geometria, alinhamentos, legibilidade, alvos de 50 dp das ações, rolagem até o fim da lista e abertura dos formulários existentes. O snapshot escolar deve permanecer idêntico após navegar e ordenar. Capturas e resultados efetivos serão registrados depois da validação.
