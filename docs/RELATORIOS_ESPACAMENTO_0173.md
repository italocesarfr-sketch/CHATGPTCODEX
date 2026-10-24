# NOcTuA Professor 0.17.3 — espaçamento dos Relatórios

Base: 0.17.2 / 31. Incremento de apresentação: **0.17.3 / 32**. Compilação, regressão Android e release assinada aprovadas.

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

Os testes verificam geometria, rolagem, alvos de 48 dp e ausência de sobreposição. A regressão existente cobre busca global, modos/ordens, ajuda, histórico, personalização e exportação. A validação principal passou: **167 JVM aprovados; 275 casos Android nos XML, sendo 271 aprovados e quatro sondas condicionais, sem falhas ou erros**. Os grupos focados (23 de horários, quatro de layout, 35 de regressão e 27 de relatórios/exportação) também passaram e se sobrepõem à suíte completa. A leitura independente de XLSX/CSV/TSV passou. As sondas de atualização e frequência têm execuções específicas; a quarta é legada. Não são contadas como aprovadas na suíte geral.


## Referência visual

A [captura anterior da 0.17.2](images/relatorios-0172/relatorios-visualizar-ordenar.png) corresponde ao estado com visualização Frequência e ordem Nome A–Z. A [nova captura equivalente no aplicativo](images/relatorios-0173/relatorios-no-app.png) mostra a separação dos controles e a busca sem filtro. Os dados são fictícios.


| Captura real do emulador | Conteúdo |
| --- | --- |
| [Relatórios em 320 dp](images/relatorios-0173/relatorios-320.png) | Busca de largura completa, ações e boletim com respiro |
| [320 dp / fonte 1,6](images/relatorios-0173/relatorios-320-fonte-1.6.png) | Ações empilhadas, sem reduzir fontes |
| [360 dp / fonte 1,3](images/relatorios-0173/relatorios-360-fonte-1.3.png) | Controles com adaptação à fonte ampliada |

As capturas foram inspecionadas visualmente. O teste mede afastamentos entre grupos, largura da busca e ausência de sobreposição; cada ação conserva alvo de pelo menos 48 dp. O menu de três pontos fica junto ao boletim; os testes existentes confirmam ajuda e histórico, sem mudança da política acadêmica.

- [Execução GitHub Actions](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40052446835).
- [Código validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/e3e499a02bca42ad5bda84abc1a14511d0af3363).
- [Testes, XML e capturas completos](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40052446835/artifacts/12544675344).

Validação física final permanece com o proprietário. A tela é rolável: em aparelhos compactos e fontes ampliadas, o boletim e algumas ações exigem rolagem. Nenhuma fonte foi reduzida para encaixar o conteúdo.


## Release e preservação

A execução foi aprovada integralmente. O fluxo permanente passou pelas seis fases de atualização sobre o APK original **0.17.1 / 30**: semente antes da atualização, instalação por cima com `adb install -r`, restauração, reinício, exclusão controlada com histórico preservado e novo reinício. Somente dados fictícios e emulador descartável foram usados.

O APK publicado foi baixado e verificado localmente. Assinatura v2 válida, pacote `br.com.diariodeclasse`, versão 0.17.3 / 32, hash idêntico ao artefato testado. A comparação adicional com o APK release **0.17.2 / 31** confirmou mesmo pacote e certificado e código de versão maior. Essa comparação é de identidade; a atualização com dados no emulador utiliza a referência 0.17.1 do workflow existente. Nenhuma configuração desse workflow foi alterada nesta tarefa.

- **APK SHA-256:** `a9e12dddcc904eab2ecd68de1860bfb353d6a7e49067cdbd6dc708165a8cc07f`.
- [APK release assinado — extrair o ZIP e instalar app-release.apk](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40052446835/artifacts/12544248440).
- [Evidências da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40052446835/artifacts/12544034761).

O artefato conserva o nome histórico `noctua-etapa6-release`, mas contém o APK verificado 0.17.3 / 32. Downloads exigem acesso ao repositório e estão sujeitos à retenção do Actions. O certificado permanente foi reutilizado; nenhuma chave, senha ou Secret foi modificado.

Além dos quatro arquivos de implementação/teste listados acima, esta entrega atualiza este documento, o resumo de versão em `README.md` e as quatro capturas de `docs/images/relatorios-0173/`. Após o commit de código aprovado, a finalização altera apenas documentação e imagens.
