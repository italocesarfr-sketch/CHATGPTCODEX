# Noctua 0.15.1 — correção de remanejamentos

Incremento sobre a 0.15.0 / 25, limitado à correção administrativa existente. Versão candidata: **0.15.1 / 26**. A validação ainda está em andamento; não distribuir antes da aprovação do CI e da atualização assinada.

## Fluxo

Em Editar aluno → Corrigir remanejamento, confira a data e escolha o tipo de destino. **Corrigir** abre a revisão; os campos deixam de ocupar espaço na prévia. As marcações aparecem por data brasileira, horário e tipo. Notas zero e pendentes continuam distintas.

Selecionar ou desmarcar uma marcação atualiza a prévia automaticamente, com breve indicador de carregamento. Leituras antigas são canceladas quando a seleção muda. Não há segunda ação “Conferir correção”. A seleção, por si só, não grava nem retira dados.

**Voltar** na revisão retorna à edição e mantém data, destino e seleções que continuem elegíveis. Sair do fluxo com alterações pede uma única confirmação de descarte. Cancelar a confirmação final ou o descarte mantém os registros intactos.

**Confirmar correção** abre um único diálogo com estudante, datas, destino, contagem por tipo das marcações selecionadas e indicação de que a matrícula continuará Remanejada. Apenas **Confirmar** executa a transação já existente. Nenhuma confirmação adicional é apresentada depois.

Se os registros mudarem durante a revisão, a prévia é atualizada, o diálogo antigo é fechado e as seleções são retiradas para nova conferência explícita. A transação também compara integralmente o snapshot imediatamente antes de gravar. Uma mudança entre a prévia e o commit produz um conflito específico, sem escrita parcial.

## Destinos suportados e bloqueados

- **Outra turma / destino externo:** correção de data, descrição ou ambas, com auditoria, mesmos identificadores e proteção de notas, recomposições e frequências legítimas.
- **Uma das minhas turmas:** seletor por ID, identificado por nome, disciplina, ano e turno. Mostra turmas compatíveis pelas regras existentes e não arquivadas. Alternar de tipo preserva o texto externo durante a sessão.
- **Conversão externa → interna:** deliberadamente bloqueada neste incremento, conforme a alternativa autorizada. A auditoria distribuída na 0.15.0 descreve correções externas; criar uma matrícula ou converter o vínculo exigiria outro contrato de auditoria. A interface explica o impedimento e desabilita Corrigir enquanto esse tipo estiver selecionado. O repositório também rejeita um ID interno, inclusive quando já existe uma matrícula da mesma identidade no destino. Nenhuma turma cadastrada é gravada como texto externo.
- **Remanejamento interno existente:** tipo, destino e data ficam protegidos. A correção exigiria ajustar origem e destino em conjunto; não há alteração unilateral nem reativação temporária.

Destinos não elegíveis, inexistentes, iguais à origem ou arquivados também são recusados no repositório. Não foi criada uma fórmula de compatibilidade alternativa: o seletor reutiliza `RelocationRules.compatible`.

## Preservação

Room **v8**, suas 14 tabelas, `diario.db`, pacote `br.com.diariodeclasse` e backup **formato 9** permanecem inalterados. Não há migração, limpeza de tabelas nem alteração de assinatura ou Secrets. As exportações, a entrada demonstrativa e os demais módulos permanecem como estavam.

A confirmação reutiliza a remoção exata por matrícula/horário/tipo, o ajuste do encerramento, a auditoria e a validação de backup na mesma transação Room. A única alteração no controle de concorrência é identificar o conflito para a interface poder atualizar a revisão. P/F/FJ só saem mediante seleção explícita; sua retirada não cria P, F nem NR. Notas e recomposições nunca são apagadas nesse fluxo.

## Interface e acessibilidade

O caminho `/run/codex-environment/codex-home/skills/impeccable/SKILL.md` não existe neste ambiente. Não foi alegado uso da skill. Foi aplicado o fallback autorizado: Material 3 e Compose nativos, tema existente, formulário rolável, controles de toque padrão e rótulos de retirada que identificam data, horário e marcação. A atualização da prévia tem indicador acessível e mensagens de conflito anunciadas discretamente. Não foram usadas skills de planilhas ou documentos, pois este incremento não as exige.

## Validação prevista

- JVM: seleção/revalidação, datas brasileiras, destinos elegíveis e bloqueio de conversão mesmo com identidade já cadastrada, além da regressão completa.
- Android/Room: correções sucessivas de data/destino, trilha de auditoria, reinício, backup/restauração, ausência de duplicação e todos os testes anteriores de rollback e preservação.
- Compose: seleção sem conferência repetida, retorno com rascunho, descarte/cancelamento, confirmação única, destino interno bloqueado, texto externo preservado e mudança concorrente de marcação. Inclui fonte ampliada.
- Atualização assinada: APK original **0.15.0**, fixado por SHA-256, mesmo certificado, instalação por cima e comparação dos registros. A semente inclui uma correção administrativa já confirmada na release anterior.

Os resultados executados, links e eventuais limitações serão registrados após o CI. A inspeção final no aparelho físico do proprietário permanece necessária; testes usam somente dados fictícios em emuladores descartáveis.
