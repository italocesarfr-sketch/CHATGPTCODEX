# Noctua 0.15.2 — conversão de destino externo para interno

Incremento restrito sobre a 0.15.1 / 26. Candidata **0.15.2 / 27**, ainda em validação. Não distribuir como release aprovada antes do CI e do teste de atualização assinada.

## Fluxo preservado

Em Editar aluno → Corrigir remanejamento, escolha **Uma das minhas turmas**, selecione o destino e informe a data efetiva. **Corrigir** abre a mesma revisão da 0.15.1. A prévia informa origem, destino externo anterior, turma interna, data e se a matrícula será criada ou vinculada. As marcações afetadas continuam disponíveis para seleção explícita, com atualização automática da prévia.

**Confirmar correção** abre um único diálogo **Cancelar | Confirmar**. Voltar, descarte e cancelamento mantêm o comportamento anterior. Nenhum aluno, vínculo ou lançamento é gravado ao apenas conferir a prévia.

## Matrículas e regras compartilhadas

`RelocationRules.internalTarget` concentra a resolução já utilizada pelo remanejamento inicial: compatibilidade por `RelocationRules.compatible`, turma não arquivada, ano/disciplina/etapa/escala/bimestres/política de FJ, ausência de ciclos, identidade, matrícula existente e prevenção de homônimos ou duplicidades. O remanejamento inicial reutiliza essa função, preservando suas regras.

Sem matrícula correspondente no destino, a conversão cria um ID próprio, mantém o `personId`, ingresso na data efetiva, situação Ativo e número de chamada livre. Não cria notas, presenças, faltas, FJ, NR, atividades ou recomposições.

Quando já existe matrícula da mesma pessoa, ela só é vinculada se estiver ativa, sem saída, ingresso exatamente na data efetiva e sem outro vínculo. Nome, número, observações e registros do destino não são sobrescritos. Na conversão, qualquer frequência, nota ou recomposição anterior ao ingresso, inclusive NR e valores pendentes, impede o vínculo. Nenhuma data é ajustada silenciosamente.

Conflitos de recomposição no mesmo bimestre são recusados pelas regras existentes da cadeia. Não há soma alternativa, fusão de identidades ou nova fórmula de notas/frequência. Os relatórios e exportações continuam consultando os registros originais pelos vínculos já existentes.

## Auditoria

A coluna JSON `relocations.corrections` comporta os novos campos sem alterar o esquema Room. Correções antigas são lidas como **CORRECAO_EXTERNA**, com os novos campos opcionais vazios.

A conversão acrescenta uma entrada **CONVERSAO_INTERNA**, preservando integralmente a lista anterior. A entrada registra:

- ID e momento da confirmação;
- datas anterior e nova;
- descrição externa anterior;
- IDs da matrícula de origem, matrícula de destino e turma de destino;
- identificação da turma na confirmação;
- cópias de auditoria das marcações explicitamente retiradas nesta operação.

O destino atual passa a ser `targetStudentId`; `Relocation.externalTarget` fica nulo. A auditoria da conversão também tem `externalTarget` nulo, pois a turma cadastrada não é simulada como uma descrição externa.

`validateLinks` verifica o encadeamento das datas e descrições anteriores, o tipo de operação e os IDs reais. A conversão deve ser o último ato dessa sequência de correções externas e corresponder ao destino atual. Remanejamentos posteriores do aluno usam os vínculos normais e não alteram essa auditoria. Renomear a turma não reescreve a identificação histórica registrada.

Os históricos preservados após excluir origem ou intermediária continuam contendo os mesmos vínculos e auditorias, sem duplicar notas ou frequência. As cópias de marcações retiradas são somente auditoria e não entram nos cálculos.

## Transação

A confirmação compara o snapshot integral, recalcula a prévia, revalida a matrícula do destino e mantém o UUID apresentado para uma matrícula nova. Na mesma transação Room, retira somente as marcações selecionadas, atualiza o encerramento da origem, cria ou vincula o destino e atualiza o vínculo com a auditoria acrescentada. Antes do commit, valida toda a cadeia e o conjunto de dados.

A matrícula de origem continua Remanejada, com os mesmos IDs, notas, observações e registros legítimos. Notas e recomposições não são excluídas. Uma FJ retirada deixa de ter linha contabilizável; não é convertida em P, F ou NR. Falha em qualquer etapa desfaz também a matrícula nova e a auditoria. Alterações concorrentes invalidam a prévia e exigem nova revisão, conforme o fluxo já existente.

## Compatibilidade

- Pacote `br.com.diariodeclasse`, banco `diario.db`, Room **v8**, suas 14 tabelas e identidade de esquema permanecem inalterados. Não há migração nem recriação de tabelas.
- Backup passa ao **formato 10**, identificando a auditoria estendida, com leitura dos formatos **1 a 9**. A leitura do formato 9 aplica os valores padrão às correções antigas. Conversões não podem ser disfarçadas como backups antigos; versões anteriores do aplicativo não leem o novo formato.
- Nenhuma chave, certificado, senha ou Secret é criado ou modificado. O workflow usa a assinatura permanente já configurada e fixa o APK original da 0.15.1 por hash.
- Login demonstrativo, funcionamento offline, exportações e identidade visual são preservados.

## Interface e skill

O arquivo `/run/codex-environment/codex-home/skills/impeccable/SKILL.md` não existe neste ambiente. Foi utilizado o fallback autorizado com Material 3 e Compose: mesmo formulário rolável, seletor existente, identificação completa do destino, rótulos acessíveis e confirmação com texto rolável. Não foi alegado uso da Impeccable nem utilizadas skills de planilhas ou documentos.

## Testes e resultados

Em andamento. Os cenários novos cobrem auditoria ausente ou múltipla, FJ anteriormente retirada, criação e vínculo de matrícula, conflitos de identidade/datas/duplicidade, ciclos, turma arquivada/incompatível, notas e recomposições, retirada explícita, concorrência, rollback, reinício, histórico após exclusões sucessivas e backup antigo/novo.

A instrumentação inclui a mesma navegação simplificada, cancelamento sem escrita, confirmação única, prévia de criação/vínculo e fonte ampliada. A atualização assinada será executada sobre o APK real da 0.15.1, com uma auditoria antiga semeada antes da instalação e conversão após a atualização.

Os resultados e links serão registrados depois da execução. Somente dados fictícios e emuladores descartáveis são utilizados.

## Limitações

O incremento permite **externo → interno**. Correção unilateral de remanejamento já interno, troca entre destinos internos e reversão interno → externo continuam protegidas e não são implementadas. Conflitos acadêmicos precisam ser resolvidos pelo professor nas operações existentes; a conversão não decide por ele quais notas ou recomposições descartar.
