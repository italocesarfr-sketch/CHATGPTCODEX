# Etapa 4B.3 — Consolidação final dos relatórios

Incremento **Noctua 0.6.1 / código 10**, baseado na **0.6.0 / código 9**, aprovada em celular físico pelo proprietário. Implementação restrita à consolidação dos relatórios, sem novas regras pedagógicas, exportação ou reformulação visual. Este documento será atualizado com os resultados verificados do CI antes da entrega.

## Relatórios consolidados

- **Bimestral:** número/nome, situação atual da matrícula, atividades, original, recomposição e vigente em colunas próprias, pendências, aulas elegíveis, P, F, FJ, faltas efetivas e percentual. Zero continua numérico; pendente e fora da matrícula são estados distintos. Uma recomposição não registrada difere de um registro não realizado e de zero.
- **Individual:** identificação, turma/ano, intervalo de matrícula e cobertura parcial, atividades e os três resultados, data da recomposição, frequência do período e histórico dos quatro bimestres. Consulta somente leitura.
- **Anual:** quatro resultados independentes com situação dos lançamentos, faltas efetivas, F/FJ separados e percentual; frequência anual por matrícula e soma das oportunidades da turma. Inclui aulas elegíveis do ano fora dos intervalos bimestrais. Não calcula média anual nem aprovação. A porcentagem vem das chamadas válidas agregadas; não é a média dos quatro percentuais.
- **Frequência e faltosos:** mesma projeção numérica e política de FJ da turma, informada na tela. Ranking por faltas efetivas decrescentes, desempate por número, nome e ID. NR, dias sem chamada, cancelamentos e períodos fora da matrícula não viram faltas.

Nenhuma atividade aplicável ou chamada realizada produz “Sem dados”. Atividades aplicáveis sem notas continuam pendentes, não zero. Lançamentos parciais continuam parciais mesmo com recomposição. A distinção de atividade não aplicável depende da matrícula na data da atividade, como antes. Transferidos/remanejados permanecem consultáveis pelo histórico; o status atual não apaga suas notas ou chamadas. Bimestres sem configuração são identificados, sem usar acidentalmente a frequência anual.

## Projeções e consistência

`SchoolReports` permanece a entrada compartilhada somente leitura para o futuro módulo de exportação: resultados numéricos, ordem estável, estados das atividades, cobertura da matrícula, quatro períodos e `FrequencySummary`. Não há uma segunda fórmula: `DiarySnapshot.result`, `DiarySnapshot.frequency`, `SchoolRules` e `RecompositionRules` continuam responsáveis pelos cálculos. O resultado original é recalculado das atividades atuais; a recomposição permanece separada por aluno/bimestre e não é somada às atividades.

O Repository passou a observar invalidações das nove tabelas e ler um `DiarySnapshot` completo em uma única transação Room de leitura. Isso impede combinar estados intermediários de consultas independentes durante alterações transacionais, como o novo máximo de uma atividade com notas ainda antigas. Não altera comandos de gravação, dados, IDs, esquema ou backup. Emissões iguais são descartadas.

A tabela conserva o nome visível durante a rolagem horizontal, aceita nomes longos e mantém a posição vertical/horizontal em atualizações dos dados. Apenas mudanças explícitas de turma, período, aluno ou tipo de relatório reposicionam a leitura. Os filtros globais e as quatro páginas existentes foram preservados. Não há nova identidade visual.

## Preservação e assinatura

Permanecem `br.com.diariodeclasse`, `diario.db`, Room **v2**, nove tabelas e IDs, migração aditiva 1→2, backup **formato 3**, leitores dos formatos 1/2, preferências e funcionamento offline. Nenhuma limpeza, recriação, migração destrutiva ou alteração do esquema. Arquivos de esquema anteriores permanecem idênticos. O código de backup/restauração e as regras de notas, frequência, recálculo proporcional e recomposição não foram alterados.

O workflow manual `release.yml` e a configuração de assinatura permanecem intactos. O CI reutiliza a chave existente, sem criar/substituir chaves ou modificar Secrets. A referência de atualização agora é a release real **0.6.0**, execução `39045605081`, artefato `12105803073`; metadados públicos em [baseline/noctua-release-0.6.0.json](baseline/noctua-release-0.6.0.json).

O ensaio instala essa referência em emulador descartável, sem dados reais, cria as nove tabelas com recomposições zero, positiva e não realizada antes da atualização, atualiza por cima e compara todos os registros/IDs/preferências. Confere relatórios, backup/restauração, correção explícita de recomposição e reabertura do processo. Scripts recusam aparelhos físicos. A comparação criptográfica com o certificado de referência continua obrigatória.

Faça backup externo válido antes de atualizar. Não desinstale nem limpe os dados para contornar uma divergência de assinatura. Não faça downgrade para versões que não reconheçam o esquema v2. APK debug não é indicado para atualizar a instalação release.

## Validação planejada para esta entrega

- Regressão JVM, compilação debug/instrumentação e cenários Java locais.
- Seis novos testes JVM de projeção: quatro períodos, zero/pendente/não aplicável, recomposição e correções posteriores, frequência anual ponderada, elegibilidade/política FJ, ranking/ordem estável e isolamento de turma/ano.
- Três novos testes Room: consistência de todas as emissões durante recálculos proporcionais, reinício/backup das nove tabelas e mudança/exclusão de atividade sem deslocar a recomposição de origem.
- Quatro novos testes Compose, além da regressão existente: campos do boletim, integração anual/individual, nomes extensos/posição de leitura e trocas rápidas de ano/turma/período.
- Regressão de frequência, atividades, recálculo proporcional, recomposição, migração e backups anteriores; sondas de encerramento/reabertura e atualização real 0.6.0 → 0.6.1.

Os resultados finais, links de execução, artefatos e limitações serão registrados após a conclusão dos testes. A aprovação física anterior refere-se somente à 0.6.0; a 0.6.1 ainda depende de conferência do proprietário no aparelho. Etapa 5, exportação, registros de aula e outras regras permanecem fora deste incremento.
