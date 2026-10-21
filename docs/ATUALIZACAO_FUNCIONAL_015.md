# Noctua 0.15.0

Esta atualização permite corrigir remanejamentos externos confirmados, conferir o que impede limpar horários e exportar registros de aula em XLSX. Mantém o pacote `br.com.diariodeclasse`, o banco `diario.db` e o processo de assinatura permanente. Utiliza somente dados fictícios nos testes.

## Correção de remanejamento externo

Em Gerenciar alunos, habilite o histórico, edite a matrícula remanejada e toque em **Corrigir remanejamento**. O formulário mostra a data e o destino registrados. Informe a data efetiva confirmada pelo professor; o aplicativo não a presume.

**Conferir correção** apresenta a mudança de data e destino e os lançamentos encontrados na data proposta ou depois. P, F, FJ e NR persistidos aparecem por data e horário. Notas, inclusive zero ou pendentes, e recomposições aparecem com seus valores. A ausência de uma linha de frequência não é apresentada como NR persistido.

Uma marcação indevida só é retirada quando o professor a seleciona, confere novamente a prévia e confirma a correção administrativa. A retirada exclui exatamente aquela linha de frequência, sem criar presença, falta ou NR. A matrícula continua Remanejada, com os mesmos IDs e observações. Notas e recomposições nunca são excluídas por esse fluxo: se houver lançamentos na data proposta ou posteriores, a correção fica bloqueada com indicação do motivo.

Remanejamentos internos permanecem protegidos: a interface informa que as duas matrículas precisariam ser ajustadas em conjunto. Esta versão não permite correção unilateral desses vínculos.

## Diagnóstico e limpeza de horários

Em Chamada, abra **Horários → Limpar horários do dia**. O diagnóstico enumera somente vínculos encontrados: marcações por horário e tipo, observações, diário, atividades, recomposições e referências históricas preservadas.

Horários sem vínculos podem ser removidos após confirmação. Se existirem somente NR, a ação **Remover horários e registros NR** retira essas linhas e os horários da turma e data conferidas. A data volta a não ter indicadores. P, F, FJ e outros vínculos relevantes impedem a limpeza; cancelar e reativar horários continua disponível.

A confirmação revalida o snapshot. Alterações posteriores à conferência invalidam a operação. A remoção dos NR e horários ocorre na mesma transação; qualquer falha desfaz todas as mudanças.

## Planos de aula em Excel

Acesse **Relatórios → Exportar → Outras planilhas → Exportar planos de aula (XLSX)**. A turma e o ano continuam sendo os selecionados no aplicativo. Escolha um bimestre cadastrado ou um intervalo de datas e confira a prévia antes de salvar ou compartilhar.

O arquivo contém uma folha com Data, Título e Descrição. As datas são células numéricas com formato `dd/mm/yyyy`. Os textos são literais, editáveis e preservam acentos, caracteres especiais e quebras de linha. A ordem é data seguida de ID persistente, mantendo títulos repetidos e vários registros no mesmo dia. Não inclui estudantes, frequência ou notas e não depende de chamadas.

A geração reutiliza `SpreadsheetWriter`, `ExportFiles`, o seletor de documentos e `SpreadsheetShare`. Apenas o novo modelo recebe larguras próprias, cabeçalhos congelados e quebra automática de texto. Os modelos anteriores mantêm suas projeções e formatos. Sem planos elegíveis, a interface informa o estado vazio e desabilita salvar e compartilhar.

O limite do Excel de 32.767 caracteres por célula permanece aplicado: o aplicativo recusa a geração em vez de truncar o texto. A altura de linha do Excel é limitada; textos muito extensos permanecem integralmente na célula e podem exigir ajuste de visualização no Excel.

## Persistência e atualização

Room passa de v7 para v8 com uma única operação aditiva: `ALTER TABLE relocations ADD COLUMN corrections TEXT NOT NULL DEFAULT '[]'`. Não há recriação de tabelas nem migração destrutiva.

A coluna guarda uma lista serializada de correções administrativas: data e destino anteriores e novos, instante da confirmação, identificador da correção e cópias de auditoria das marcações explicitamente retiradas e seus horários. Essas cópias nunca entram nas projeções acadêmicas. As observações do professor não são utilizadas como campo técnico.

Como a trilha pertence ao próprio vínculo, acompanha os históricos já preservados na exclusão de turmas, sem duplicar notas ou frequência. Vínculo e matrícula são corrigidos na mesma transação, com revalidação antes da escrita e validação de integridade antes do commit.

O backup passa ao formato 9, com leitura dos formatos anteriores 1 a 8. A nova coluna tem valor vazio ao migrar dados antigos. O certificado e os Secrets não são modificados. A compatibilidade de instalação depende da verificação do APK e do teste de atualização assinado no CI; resultados serão registrados após execução.

## Interface e skills

O caminho informado de Impeccable (`/run/codex-environment/codex-home/skills/impeccable/SKILL.md`) não existe neste ambiente. Foi aplicado o fallback autorizado pelo proprietário: componentes nativos Material 3 e Jetpack Compose, formulários com rolagem, confirmação antes de efeitos e mensagens específicas. A identidade visual existente foi mantida.

Foram consultadas as skills locais `spreadsheets` e `documents`, seus arquivos `SKILL.md` e referências pertinentes. A orientação de planilhas foi aplicada à tipagem de datas, colunas, quebra de texto e validação independente da saída nativa. A orientação documental foi aplicada à revisão de clareza deste Markdown, sem criar DOCX ou PDF.

## Validação

Em andamento. Não distribuir como versão validada antes de concluir JVM, instrumentação Android, migração, backup/restauração, leitura independente dos XLSX e atualização assinada sobre a 0.14.0. Os resultados efetivamente executados e os links serão acrescentados ao final.
