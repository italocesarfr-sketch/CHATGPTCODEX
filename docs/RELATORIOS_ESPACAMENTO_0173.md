# NOcTuA Professor 0.17.3 — espaçamento dos Relatórios

Base: 0.17.2 / 31. Incremento de apresentação: 0.17.3 / 32. Validação final pendente.

## Alteração restrita à tela Relatórios

Os controles existentes são agrupados em colunas: Visualizar, Ordenar alunos, Busca, ações e cabeçalho do boletim. Cada bloco tem 16 dp de separação; os rótulos ficam a 4 ou 8 dp de seus controles. As ações têm mais 4 dp acima e 8 dp abaixo, e o conteúdo da tabela mantém afastamento do cabeçalho. São reutilizados os tokens NoctuaSpace; não há novas cores, fontes ou dependências.

A busca ocupa toda a largura disponível, com lupa, Buscar e Limpar. O ícone de filtro foi removido. Os acessos já existentes a histórico de remanejados e ajuda permanecem no menu de três pontos junto do cabeçalho do boletim, sem repetir visualizações ou ordenações.

Personalizar planilha e Exportar conservam o componente e callbacks existentes: duas ações de contorno lado a lado quando há espaço e em coluna para fontes ampliadas/telas estreitas. O afastamento pertence apenas ao contêiner da tela Relatórios, sem modificar outras telas.

Nenhuma mudança em busca, cálculos, seleção de bimestre, modos, ordenação, exportadores, preferências persistentes, matrículas, Room v9, migrações ou backup formato 11. Nenhuma alteração na configuração de assinatura. A atualização incrementa somente a identificação da versão, preservando `br.com.diariodeclasse` e `diario.db`.

## Arquivos

- `app/src/main/java/br/com/diariodeclasse/ui/ReportsScreen.kt`: agrupamento, espaçamento, busca de largura completa e reposicionamento do menu existente.
- `app/src/main/res/drawable/ic_more_vert.xml`: ícone Android de três pontos para esse menu.
- `app/src/androidTest/java/br/com/diariodeclasse/PostTwelveLayoutInstrumentedTest.kt`: validação dos afastamentos, largura da busca, posição do menu, ações acessíveis e capturas em 320/360 dp e fontes 1,0/1,3/1,6.
- `app/build.gradle.kts`: 0.17.3 / 32.

Impeccable 4.7.0 consultado e aplicado: contexto do alvo, layout, craft e diretrizes Android. Escopo de refinamento, preservando identidade visual e implementações existentes.

## Verificação

Os testes verificam geometria, rolagem, alvos de 48 dp e ausência de sobreposição. A regressão existente cobre busca global, modos/ordens, ajuda, histórico, personalização e exportação. Capturas finais e resultado efetivo do CI serão acrescentados após a execução.
