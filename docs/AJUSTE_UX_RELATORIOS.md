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

Resultados da compilação, testes e atualização assinada: pendentes até a conclusão da execução de CI. Nenhum resultado físico neste aparelho é presumido.
