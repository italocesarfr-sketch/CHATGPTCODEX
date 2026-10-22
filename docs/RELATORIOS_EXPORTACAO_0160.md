# Noctua 0.16.0 — Relatórios e exportação minimalistas

Base: Noctua 0.15.2 / código 27. Candidato: 0.16.0 / código 28.

## Fluxo do professor

**Relatórios → bimestre → Exportar → Exportar XLSX.** O período vem da seleção global; não existe um segundo seletor de bimestre na operação comum. Ao voltar, a seleção permanece. Configurações e prévia não são passos obrigatórios.

Relatórios mantém Organizar, Exportar, Buscar e o boletim. O ícone de filtro reúne Geral/Frequência/Notas, as ordenações existentes, ordem alfabética e Mostrar remanejados. Organizar continua tendo uma responsabilidade diferente: ocultação manual, ordem e linhas vazias da planilha. Filtros do boletim não mudam a chamada nem a disposição personalizada.

Exportar possui um seletor compacto para **Notas, Frequência, Consolidado, Chamada, Planos de aula e Resumo anual**. Cabeçalho identifica turma, disciplina, ano e período uma única vez. Ações diretas: Copiar e Exportar XLSX.

## Ajustes sob demanda

O ícone de ajustes abre uma folha inferior Material 3. Cada modelo oferece somente parâmetros aplicáveis:

- Notas/Frequência/Consolidado: abrangência bimestral herdada ou quatro bimestres; XLSX/CSV/TSV; cabeçalhos; número da chamada; destino salvar/compartilhar.
- Notas/Consolidado: conteúdo da cópia — dados completos, somente resultados vigentes ou todas as notas. O resumo identifica quando a cópia contém apenas notas. A exportação de arquivo continua completa conforme o modelo; essa escolha é explicitamente da cópia.
- Chamada/Planos: bimestre herdado ou intervalo personalizado. O resumo anual utiliza o ano da turma.
- Chamada/Resumo anual: cabeçalhos e número da chamada. Planos preserva o formato próprio, incluindo Data/Título/Descrição e sua formatação aprovada.

Concluído fica fixo no rodapé da folha e apenas a fecha; alterações simples não exigem salvamento. Preferências ficam no estado restaurável da interface, separadas por turma e modelo; sobrevivem à rotação e à ida e volta na navegação. Não são novas preferências Room nem integram o backup. A disposição personalizada existente continua persistente no banco e nos backups, sem alterações.

Ver prévia abre uma visualização rolável da mesma matriz usada no arquivo. Fechar retorna sem perder opções. Não existe bloco permanente Prévia — Somente notas.

## Implementação e preservação

`ExportPresentation` adapta parâmetros das projeções existentes. Não contém cálculos acadêmicos. `SpreadsheetKind` reúne os seis destinos; `SpreadsheetPreferences` é exclusivamente estado de UI. `ExportScreen` centraliza seleção, ajustes, cópia, prévia e entrega pelo seletor Android/compartilhamento. `AttendanceExportScreen` encaminha destinos antigos restaurados para o mesmo componente.

As projeções `ExportProjection`, `AttendanceExportProjection`, `AnnualExportProjection` e `LessonPlanExportProjection` preservam suas regras. XLSX e CSV continuam usando os mesmos escritores; TSV reutiliza literalmente a matriz tabulada antes utilizada na área de transferência. A prévia também usa `ExportTable.rows()`.

Não foram criadas opções decorativas de situação, máximos ou colunas que não existiam: situação segue o modelo existente; máximos acompanham os cabeçalhos de Notas; resultados originais/recomposição/vigentes continuam disponíveis. A exclusão automática de remanejados na origem permanece inalterada e é distinta da consulta histórica no boletim.

Sem alteração de Room v8, suas 14 tabelas, migrações, banco `diario.db`, backup formato 10 ou leitores antigos. Sem alteração de `br.com.diariodeclasse`, certificado, Secrets ou regras de notas/frequência/remanejamentos. Nenhuma instalação real de professor é utilizada nos testes.

## Design e acessibilidade

Impeccable 4.7.0 foi lido do diretório instalado no projeto `.agents/skills/impeccable/`. Foram aplicadas as referências `distill`, Android e `craft-floor`: divulgação progressiva, ações diretas, controles Material 3, alvos de pelo menos 48 dp, hierarquia existente e preservação da identidade noturna. Não há nova identidade visual. As duas ações ficam lado a lado nas larguras normais e se empilham em fontes maiores ou larguras menores, em vez de comprimir os textos. A folha abre expandida quando há muitas opções e mantém Concluído sempre acessível. Ajustes e prévia respeitam Voltar do Android.

## Validação

Resultados efetivamente executados e links serão registrados após o CI. Não considerar este texto uma declaração antecipada de aprovação.

Cobertura acrescentada: equivalência das seis projeções padrão, combinações de cabeçalhos/números/abrangência, cópias completas/vigentes/históricas, zero/pendente/linhas vazias, TSV/CSV, parâmetros por modelo, bimestre herdado e rotação, ausência de prévia obrigatória, todos os modelos acessíveis, filtros agrupados e preservação do snapshot Room.

A instrumentação anterior foi adaptada somente onde os caminhos de acesso mudaram, mantendo as verificações de integridade, histórico, compartilhamento, cancelamento e conteúdo. A atualização assinada usa o APK real 0.15.2 e a chave permanente já configurada, sem criação ou troca de chave.

Limitação do ambiente local: não há `/dev/kvm`; capturas e testes de navegação rodam no emulador descartável do GitHub Actions. Validação física do professor continua recomendada após a entrega, especialmente com teclado e escala de fonte próprios.

### Primeira execução de instrumentação

A execução [39860766408](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39860766408) passou por 148 testes JVM, validadores independentes e 35 cenários Android focados anteriores. Nos 21 cenários focados de relatórios/exportação, duas automações ainda chamavam `performScrollTo()` para as ações Copiar/Exportar, agora fixas fora da lista. Falharam antes de executar a ação por ausência de ancestral rolável. Foram atualizadas para exigir visibilidade e clicar diretamente; as verificações de cancelamento, área de transferência e igualdade do backup continuam presentes. Essa execução não aprovou uma release.

A revisão das capturas reais também levou a reduzir espaçamentos, manter ações lado a lado em 320 dp na fonte normal e fixar o fechamento da folha de ajustes. A troca entre turmas ganhou um cenário explícito; filtros vazios durante a busca global não são exibidos. A aprovação final depende da nova execução integral.
