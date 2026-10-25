# NOcTuA Professor 0.18.2 — dias úteis em Horários

Base: 0.18.1 / 34. Candidato: 0.18.2 / 35. Validação Android e release ainda pendentes.

O seletor principal e o campo Dia da semana no formulário comum oferecem somente segunda a sexta. Em fonte normal, os cinco botões dividem a largura disponível; em telas estreitas/fontes ampliadas, distribuem-se em linhas. Alvos de toque têm no mínimo 48 dp. Aos fins de semana, a seleção inicial é segunda-feira. O mesmo tratamento vale para uma seleção principal antiga restaurada. Um rascunho de formulário antigo de sábado/domingo exige escolher explicitamente um dia útil antes de salvar, sem converter automaticamente seus períodos.

A restrição é da interface: os modelos continuam lendo os sete dias. Nenhuma revisão, período histórico ou lançamento acadêmico é apagado. Vigências conserva a consulta histórica de sábado/domingo, e os calendários de Chamada e Diário mantêm as previsões legadas e os registros efetivos. Editar um dia útil preserva os demais dias, inclusive os fins de semana. Sem alterações de Room v9, migrações, banco diario.db, backup 11, regras acadêmicas, identificador ou assinatura.

Impeccable 4.7.0 aplicado ao refinamento nativo: contexto do alvo, orientações Android, legibilidade, alvos de toque e revisão por capturas reais. Sem alteração da identidade visual.

## Validação

- Compilação local de debug/instrumentação aprovada; 176 testes JVM, sem falhas.
- Testes Android verificam cinco seletores, ausência de sábado/domingo no formulário, seleção/listagem por dia, calendário de Chamada/Diário, fontes 1,0/1,3/1,6 e preservação integral do snapshot acadêmico.
- Teste Room dedicado mantém uma revisão antiga com sábado/domingo, P/FJ/NR e diário, edita sexta-feira, reabre o banco e restaura backup, conferindo também referências órfãs.
- O workflow de atualização utiliza o APK original assinado 0.18.1, com hash fixado em docs/baseline/noctua-release-0.18.1.json. A semente inclui grade de fim de semana para conferir preservação após instalação por cima, restauração e reinício. Configuração da chave permanente não é modificada.

Resultados finais, links e capturas serão registrados após a execução efetiva. Somente dados fictícios em emulador descartável; validação no aparelho físico permanece manual.
