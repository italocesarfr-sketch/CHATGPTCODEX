# NOcTuA Professor 0.19.0 — Produção textual e motivos opcionais de FJ

Base validada: 0.18.2 / 35. Nova versão: 0.19.0 / 36. Compilação debug/instrumentação e 183 testes JVM aprovados localmente. Validação Android e release ainda pendentes.

## Atividades

Tipo passa a oferecer, na ordem: Prova, Trabalho, Redação, Produção textual, Atividade prática, Seminário, Estudo dirigido e Outro. O campo já era texto: nenhuma atividade existente é reclassificada. Relatórios e exportadores continuam recebendo a atividade e suas notas, sem colunas novas ou cálculos diferentes.

## Faltas justificadas

Ao registrar FJ, o menu da própria célula passa a oferecer os motivos opcionais. Continua sendo possível seguir a chamada sem abrir ou preencher esse seletor. Opções: Atestado médico, Acompanhamento médico, Falecimento familiar, Convocação oficial, Atividade escolar ou esportiva e Outro. Sem motivo limpa a seleção; não é uma nova categoria. Sem texto livre, documentos ou confirmação adicional. Salvamento automático pela fila de comandos existente, com feedback de erro já utilizado pela chamada.

A chave continua sendo aula/horário e matrícula. Aula determina turma e data. Motivos de ocorrências e alunos diferentes permanecem independentes. Trocar FJ por P, F ou NR limpa o motivo; o segundo toque em FJ continua produzindo NR sem motivo. A ação explícita de copiar a primeira chamada copia também o motivo de uma FJ; marcar todos como presentes limpa os motivos substituídos. Cancelar um horário preserva seu histórico.

Um motivo alterado depois da abertura da seleção é revalidado dentro da transação. Uma seleção atrasada não recria FJ nem sobrescreve uma chamada concorrente. Matrículas e horários protegidos continuam bloqueados. A remoção administrativa de FJ preserva o motivo no registro de auditoria; exclusões individuais removem somente o campo da ocorrência excluída. Históricos preservados de turmas excluídas conservam seus motivos e IDs.

P, F, FJ e NR mantêm a semântica e os cálculos existentes. Motivo não abona a falta nem transforma FJ em presença. Relatórios, XLSX, CSV e TSV conservam suas colunas e dados acadêmicos; não exportam motivos em colunas inesperadas.

## Persistência e compatibilidade

Room **9 → 10**, com uma única migração aditiva: `ALTER TABLE marks ADD COLUMN reason TEXT DEFAULT NULL`. As quinze tabelas, chaves e registros permanecem. FJ antigas recebem valor nulo, sem preenchimento ou reinterpretação. Esquemas 1–9 congelados permanecem no repositório; esquema 10 é gerado pelo Room/KSP.

Backup **11 → 12**, banco 10. Leitura dos formatos 1–11 preservada. O campo opcional integra automaticamente o backup completo, os históricos independentes de turma e a auditoria das frequências retiradas. Validação rejeita motivos fora das seis categorias, motivos em P/F/NR e motivos não nulos declarados em backups de formatos anteriores. Não há limpeza de banco ou migração destrutiva.

Identificador `br.com.diariodeclasse`, banco `diario.db`, funcionamento offline e assinatura permanente mantidos. A referência da atualização assinada é o APK original 0.18.2, com hash fixado, não uma recompilação.

## Interface e testes

Impeccable 4.7.0 efetivamente consultado: contexto do alvo, modo Operate, diretrizes Android e craft. Componentes Material 3 existentes; seletor no próprio menu de FJ, com área de toque mínima de 48 dp e menu rolável, sem aumentar a altura das linhas. Identidade noturna e demais controles preservados.

Sete testes JVM novos verificam opções exatas, motivos opcionais, tipos inválidos, equivalência das projeções/cálculos/exportações, backup 12, leitura de backup 11 sem campo e auditoria antiga. As fixtures já lidas independentemente em XLSX/CSV/TSV também incluem Produção textual e FJ com motivo. Os testes anteriores de versão esperada foram atualizados para banco 10/backup 12, preservando as verificações de dados e esquemas antigos.

Dez testes Android novos cobrem migração, seis categorias, conversões P/F/FJ/NR, edição, limpeza, concorrência, rollback SQL, dois alunos/horários, reinício, backup/restauração, auditoria administrativa, exclusão da turma de origem com histórico e exclusão individual, criação/edição/reabertura de atividade e layout 320 dp/fontes 1,0/1,3/1,6.

Resultados finais, arquivos, capturas e APK serão registrados somente após validação. Dados exclusivamente fictícios; aparelho físico e leitor de tela permanecem validações manuais.

A validação de fonte do seletor inclui também `font_scale` real do Android em 1,3 e 1,6, com conferência da densidade do texto dentro da janela do menu. O teste de interface respeita o contrato anterior: criar abre as notas; salvar uma edição retorna à lista. A reabertura após recriação é feita a partir dessa lista, preservando as verificações de nome, categoria e demais atividades.

Na regressão completa, um teste anterior de lista longa encontrou acesso concorrente ao SnapshotStateObserver durante a busca iterativa por 100 cards. O teste passou a usar o índice determinístico da fixture, mantendo a rolagem até o último aluno, os controles de legibilidade/alinhamento e a comparação integral do snapshot. Nenhuma tela de alunos foi modificada nesta atualização.
