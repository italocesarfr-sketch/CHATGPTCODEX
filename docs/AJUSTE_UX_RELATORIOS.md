# Relatórios — refinamento pontual de UX

Base: Noctua 0.11.0 (16). Candidato: 0.11.1 (17), para permitir a instalação deste ajuste sobre a versão anterior.

## Apresentação

O ícone de informação ao lado do título abre **Informações do relatório**. A ajuda fica fechada inicialmente, usa texto de leitura confortável, permite rolagem em telas pequenas e fecha pelo botão **Fechar**, toque fora ou Voltar do Android. Possui descrição acessível e alvo de toque de 48 dp.

Foram movidas, sem remover seu conteúdo:

- Escala das atividades, escala da recomposição e explicação das pendências/resultados parciais.
- Instrução de rolagem horizontal, nome fixo, acesso ao histórico e significado da situação da matrícula.
- Política de faltas efetivas conforme a configuração atual da turma, sem modificá-la.
- Explicação de aulas elegíveis e critério de desempate do ranking de faltosos.
- Ressalvas do resumo anual: quatro resultados separados, ausência de média/aprovação e cálculo da frequência anual.

Título, identificação da turma/período, ações de exportação, filtro de remanejados quando aplicável, seleção do relatório/ordenação e tabelas permanecem no fluxo principal. O contador de horários foi abreviado; os totais de frequência, os estados vazios e os avisos de ausência de registros continuam visíveis. O relatório individual não foi alterado.

Sem os parágrafos de orientação antes do boletim, a tabela aparece mais cedo na rolagem. Nenhuma fórmula, projeção, exportação, matrícula, chamada ou valor escolar foi alterado. Room v6, backup 7, pacote, banco e configuração de assinatura permanecem iguais. O workflow reutiliza a assinatura já configurada e passa a testar a atualização a partir do APK real 0.11.0, identificado por hash e certificado público.

## Validação

Dois testes instrumentados adicionais verificam ajuda inicialmente oculta, leitura/rolagem, fechamento, preservação de valores e ordenação, ações de exportação visíveis, explicações anuais/ranking e política de faltas da turma. Os testes anteriores de exportação, filtros e dados permanecem na regressão.

Resultados no [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39149230316), código [`a251f28`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/a251f287c0c023ef1516a7435ee75bcb295d6ca9):

- Compilação local e CI: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease` aprovadas.
- **93 testes JVM** e **158 testes Android** aprovados, nenhum com falha. Quatro probes condicionais não executados na suíte normal; atualização, backup e reinício assinados foram executados separadamente em quatro fases aprovadas. Também passaram os probes legados e de frequência entre processos da regressão.
- Os dois novos testes de ajuda passaram. Capturas sintéticas conferidas: a tela principal preserva as ações/filtros; os textos explicativos deixam de ocupar a área acima da tabela e aparecem no diálogo. Capturas `06-relatorios-sem-blocos-auxiliares.png` e `07-informacoes-do-relatorio.png` dentro de `build/etapa4b3-telas.tar`, no artefato `resultados-etapa6`.
- XLSX/CSV/TSV: leitura independente de 30 conjuntos JVM e três conjuntos Android aprovada, sem alterar as implementações de exportação.
- Atualização real **0.11.0 (16) → 0.11.1 (17)** em emulador descartável: mesma assinatura permanente existente, instalação com `adb install -r`, preservação dos registros fictícios, backup/restauração e reinício aprovados. Nenhuma chave ou Secret foi criado ou modificado.
- Verificação local do APK release baixado: assinatura APK v2 válida, pacote `br.com.diariodeclasse`, API mínima 26 e versão 0.11.1/17. SHA-256: `dbe0179d461ba954eca8046696729aee552d60366bbf810eda2c030fbffdb5f8`.

[Baixar APK release assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39149230316/artifacts/12144902604) — extrair `app-release.apk` do ZIP. O artefato mantém o nome histórico `noctua-etapa6-release`; a versão foi conferida dentro do APK.

Limitação: validação instrumentada em API 30 e revisão das capturas, sem novo teste físico ou ensaio completo com TalkBack. A conferência visual final no aparelho do proprietário continua recomendada. Não houve alteração do banco, backup, regras pedagógicas, exportações ou identidade noturna.
