> **Noctua 0.15.1 / 26 validada:** correção administrativa com revisão automática, confirmação única e seleção de destino por tipo. Conversões internas permanecem bloqueadas; Room v8 e backup 9 inalterados. [Contrato, testes e APK assinado](REFINAMENTO_REMANEJAMENTOS_0151.md).

> Atualização funcional **Noctua 0.15.0 / 25** aprovada: correção de remanejamentos externos, limpeza confirmada de NR com diagnóstico e planos de aula XLSX independentes. Room v8 aditivo, backup 9 lendo anteriores, mesma assinatura da 0.14.0. [Contrato, testes e entrega](ATUALIZACAO_FUNCIONAL_015.md).

> Sete refinamentos finais autorizados sobre a 0.13.0: interface compacta, calendário no rodapé e entrada **somente demonstrativa**, sem contas, credenciais persistidas ou autenticação. [Contrato e validação](REFINAMENTOS_FINAIS_UX.md). Room v7, backup 8 e regras escolares inalterados.

> Refinamento autorizado sobre a 0.12.1: relatórios, exportação anual e reativação/limpeza segura de horários. [Contrato e validação](REFINAMENTO_RELATORIOS_CHAMADA.md). Room v7 e backup 8 sem mudança.

> Refinamentos pós-0.12.0 autorizados: backup, saída segura da conferência, três visões de relatórios e redução de instruções permanentes. Consulte [POS_0120.md](POS_0120.md). Sem nova etapa pedagógica ou alteração de esquema.

> **Prompt 2 autorizado — Noctua 0.12.0 / 21:** seleção coletiva na importação, chamada XLSX por período, busca global na própria página Relatórios e segundo toque para NR. Room v7 e backup 8 sem mudança de esquema. [Contrato e validação](PROMPT2.md).

> **Atualização de UX — 0.11.4 / 20:** Cadastrar Turma reúne Nova Turma, Editar Turma e Turmas Arquivadas. Arquivar/Excluir ficam ao final da edição, sem confirmação digitada. Preservação transacional de históricos, Room v7 e backup 8 inalterados. [Contrato](CORRECAO_UX_TURMAS.md).

> **Correção autorizada posterior à 0.11.1 — exclusão com remanejamentos:** origem e intermediárias podem ser excluídas após confirmação, preservando históricos independentes por IDs. Room v7 aditivo e backup 8, compatível com anteriores. O bloqueio genérico descrito na Etapa 5.2 é substituído pela preservação transacional, sem novas regras pedagógicas. [Arquitetura e validação](CORRECAO_EXCLUSAO_REMANEJAMENTOS.md).

> **Autorização vigente — Etapa 6 / Noctua 0.11.0 (16), concluída:** identidade noturna nativa, navegação Chamada/Diário e exportação concentrada em Relatórios. Room v6 e backup 7 sem alterações. Base 0.10.0 aprovada em aparelho físico. Impeccable indisponível nesta execução, sem alegação de uso; **93 JVM, 156 Android e atualização assinada aprovados**; [auditoria, escopo e resultados](ETAPA6.md). Sem novas regras escolares.

> **Autorização vigente — Etapa 5.3 / Noctua 0.10.0 (15), concluída:** Registros de Aula livres e facultativos, com turma/data/título/descrição, edição/duplicação/exclusão confirmada e cópia para a área de transferência. Independentes da chamada e de cálculos escolares. Room v6 aditivo e backup 7 compatível com anteriores. Base 0.9.0 aprovada em aparelho físico pelo proprietário. **89 JVM, 150 Android e atualização assinada 0.9.0 → 0.10.0 aprovados**. [Contrato e resultados](ETAPA53.md). Não autoriza etapa visual final, IA, BNCC ou obrigatoriedade de preenchimento.

> **Atualização autorizada — Etapa 5.2 / Noctua 0.9.0 (14):** exportação XLSX/CSV e cópia, usando a disposição 5.1 e as projeções SchoolReports. Turmas podem ser arquivadas, consultadas, reativadas e excluídas com validação transacional; histórico compartilhado bloqueia exclusão. Room v5 aditivo e backup 6, lendo anteriores. Contrato em [ETAPA52.md](ETAPA52.md). PDF permanece fora da versão 1.0.

> **Atualização autorizada — Etapa 5.1 / Noctua 0.8.0 (13):** organização para exportação por matrícula, ocultação manual em lote, ordem independente e linhas vazias. Room v4 aditivo; backup 5 com leitura dos formatos anteriores. [ETAPA51.md](ETAPA51.md) define o contrato atual. Somente a organização está implementada; arquivos e cópia dependem da Etapa 5.2. **PDF não integra a versão 1.0**, por decisão do proprietário; descrições históricas da base não são funcionalidades atuais.

# Especificação-mestre — Noctua (Diário de Classe)

> Correção autorizada 0.7.1 / código 12: reativação consistente e remanejamento após transferência administrativa, sem alteração de esquema ou regras pedagógicas. Consulte [CORRECAO_REATIVACAO.md](CORRECAO_REATIVACAO.md). 62 JVM, 109 Android e atualização assinada 0.7.0 → 0.7.1 aprovados; resultados no documento. Não autoriza a Etapa 5.

> Etapa 4C autorizada: remanejamento seguro sobre a 0.6.1, com vínculos entre matrículas e pontos do mesmo bimestre acompanhando o aluno. Incremento 0.7.0 / código 11 concluído: migração aditiva v2→v3 e backup compatível com os formatos anteriores. Compatibilidades e bloqueios explícitos em [ETAPA4C.md](ETAPA4C.md). 55 testes JVM, 101 Android e atualização assinada 0.6.1 → 0.7.0 aprovados; resultados em ETAPA4C.md. Não autoriza a Etapa 5.

> Etapa 4B.3 autorizada: consolidação somente leitura dos relatórios sobre a 0.6.0 aprovada em aparelho físico pelo proprietário. Incremento 0.6.1 / código 10: projeções compartilhadas, notas e recomposição separadas, frequência integrada e preservação da leitura. Sem alteração do esquema Room v2, do backup formato 3 ou das regras pedagógicas. Consulte [ETAPA4B3.md](ETAPA4B3.md) para escopo e resultados. 45 testes JVM, 87 Android e atualização assinada 0.6.0 → 0.6.1 aprovados; links no documento do incremento. A Etapa 5 permanece sem autorização.

> Etapa 4B.2 autorizada: recomposição substitutiva por bimestre, sem recuperação aditiva. Incremento 0.6.0 / código 9 concluído: migração aditiva Room 1→2, backup compatível com formatos anteriores e atualização da release real 0.5.0 aprovados. 39 testes JVM e 80 Android passaram; resultados no documento do incremento. Consulte [ETAPA4B2.md](ETAPA4B2.md) para o contrato e a validação. Esta autorização prevalece sobre o planejamento genérico histórico de quatro regras de recuperação; não autoriza exportação ou outros módulos.


> Autorização posterior: primeiro incremento da Etapa 4B.1, somente relatórios de leitura, conforme [ETAPA4B1.md](ETAPA4B1.md). Não autoriza recuperação ou exportação. Incremento 0.5.0, código 8, concluído e validado, inclusive atualização sobre a release 0.4.1 com a mesma assinatura existente. Consultar os resultados em ETAPA4B1.md; próximas etapas aguardam autorização.


> Incremento autorizado posterior à 0.4.0: [identidade Noctua e assinatura](NOCTUA.md). O professor aprovou fisicamente a 0.4.0. Permanecem pacote `br.com.diariodeclasse`, banco `diario.db`, esquema/IDs e funcionalidades. A primeira chave permanente é criada e guardada pelo proprietário; [guia](ASSINATURA_PASSO_A_PASSO.md). Nenhum outro incremento foi autorizado nesta execução. O diagnóstico original abaixo permanece histórico.

> Etapa 4A, primeiro incremento autorizado: ações de atividade na lista e recálculo proporcional explícito; consulte [ETAPA4A.md](ETAPA4A.md). Os demais incrementos seguem pendentes de autorização. A base 0.3.1 foi aprovada pelo professor em celular físico.

> Correção posterior 0.3.1: novos alunos passam a ter ingresso padrão em 01/01 do ano letivo, sem reescrever datas existentes. Consulte [CORRECAO_ETAPA3.md](CORRECAO_ETAPA3.md). Os resultados 0.3.0 permanecem como histórico.

> Atualização posterior à Etapa 0: a Etapa 1 foi autorizada pelo responsável e implementada/testada. Consulte [ETAPA1.md](ETAPA1.md) para resultados e o gate de assinatura ainda pendente. As descrições de “estado atual” abaixo referem-se à base auditada da Etapa 0. A Etapa 2 foi autorizada posteriormente e concluída com APK de teste validado, inclusive com dados antigos de teste descartáveis; consulte [ETAPA2.md](ETAPA2.md). A assinatura estável deve ser resolvida antes do uso real. A Etapa 3 foi autorizada e sua implementação/validação está registrada em [ETAPA3.md](ETAPA3.md). Os incrementos seguintes da Etapa 4 aguardam autorização.

Versão documental: 1.0 · Etapa 0 · 16/10/2026.

Repositório: [italocesarfr-sketch/CHATGPTCODEX](https://github.com/italocesarfr-sketch/CHATGPTCODEX). Base técnica auditada: commit [`a157c79`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/a157c79c0823f898c63133ac6ff35bf5793b5219), branch `main`.

## 1. Finalidade e limites desta etapa

Este documento centraliza o estado atual e o comportamento desejado para as próximas atualizações. O [ROADMAP](ROADMAP.md) define a sequência, as dependências e os testes. **Nesta Etapa 0 serão publicados somente estes dois documentos.** Não há alteração de código, telas, dependências, assinatura, esquema Room ou registros do usuário.

As seções **Estado atual** descrevem código existente. As seções **Requisitos planejados** descrevem trabalho futuro, ainda não implementado. A publicação da documentação não autoriza começar a Etapa 1; é necessária a autorização do responsável pelo projeto.

As diretrizes desta reorganização prevalecem sobre propostas anteriores: quatro páginas principais, chamada sem pré-cadastro obrigatório de horários semanais, retirada futura da importação por fotografia e exportação avançada posterior ao núcleo utilizável. A retirada do OCR é uma mudança expressamente solicitada, não uma instrução para apagar os alunos que já tenham sido importados.

`README.md`, `docs/VALIDACAO.md` e os logs antigos registram limitações do ambiente local anterior. Eles ainda contêm afirmações de ausência de APK/testes Android que foram superadas pela execução de CI citada abaixo. Permanecem intactos nesta etapa; não devem ser usados isoladamente para interpretar o estado atual.

## 2. Diagnóstico do projeto existente

### 2.1. Arquitetura e tecnologias

| Aspecto | Estado encontrado |
| --- | --- |
| Aplicativo | Um módulo Gradle `:app`; `applicationId` e namespace `br.com.diariodeclasse`; nome Diário de Classe |
| Plataforma | Android 8.0+ (`minSdk 26`), `compileSdk 35`, `targetSdk 35` |
| Linguagens | Kotlin nas telas, ViewModels e persistência; Java em regras puras, parser OCR, CSV e parte dos testes |
| Interface | Jetpack Compose, Material 3, tema claro/escuro conforme o sistema; Compose BOM `2024.12.01` |
| Estado | `DiaryViewModel`, `StateFlow`, coroutines e fila de comandos; `rememberSaveable` para parte do estado de navegação/formulários |
| Persistência | Room `2.6.1`, SQLite privado `diario.db`, esquema versão 1; KSP para gerar DAO |
| Organização | MVVM com Repository pragmático; regras também existem em `DiarySnapshot`; algumas telas acessam o DAO pelo Repository |
| Compilação | Gradle Wrapper oficial `8.9`, Android Gradle Plugin `8.7.3`, Kotlin/Compose Compiler `2.0.21`, KSP `2.0.21-1.0.28`, alvo JVM 17 |
| OCR existente | ML Kit Text Recognition `16.0.1`, modelo latino integrado; câmera/galeria, recorte e rascunho de conferência |
| Rede | Manifesto remove `INTERNET` e `ACCESS_NETWORK_STATE`; não há servidor ou autenticação do professor |

Fluxo predominante: Compose → ViewModel → Repository → DAO/Room → Flow → telas. A navegação fica centralizada em `ui/DiaryApp.kt` com rotas em texto; não há um grafo de navegação tipado. `DiaryApplication` mantém as instâncias do banco e Repository. A fila do ViewModel serializa as gravações por ele encaminhadas; a confirmação de OCR usa transação no Repository.

### 2.2. Arquivos de referência

| Área | Arquivos |
| --- | --- |
| Entrada e tema | `app/src/main/java/br/com/diariodeclasse/MainActivity.kt`, `DiaryApplication.kt` |
| Entidades e DAO | `app/src/main/java/br/com/diariodeclasse/data/Database.kt` |
| Transações e agregações | `app/src/main/java/br/com/diariodeclasse/data/DiaryRepository.kt` |
| Navegação e estado | `app/src/main/java/br/com/diariodeclasse/ui/DiaryApp.kt`, `DiaryViewModel.kt` |
| Cadastro e perfil | `ui/Forms.kt`, `ui/RosterScreen.kt` |
| Calendário e frequência | `ui/CalendarScreen.kt` |
| Notas e boletim | `ui/GradeScreen.kt` |
| Regras puras | `domain/SchoolRules.java` |
| Importação existente | `domain/CsvRoster.java`, `domain/RosterParser.java`, `ocr/PhotoImportScreen.kt`, `ocr/PhotoViewModel.kt` |
| Build e CI | `app/build.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/android.yml`, `.github/workflows/device-tests.yml` |

Os caminhos abreviados `ui/`, `domain/` e `ocr/` são relativos ao pacote `app/src/main/java/br/com/diariodeclasse/`.

### 2.3. Organização atual das telas

- **Painel do professor:** ano, bimestre, aulas do dia, criação de turma e cartões de turmas com atalhos.
- **Turma:** abas Calendário, Alunos, Atividades e Boletim; edição da turma e configuração de bimestres.
- **Calendário:** navegação pelos doze meses, filtro opcional por bimestre, aulas por data e inclusão individual de aula extraordinária/reposição.
- **Chamada:** cartões por aluno, estados P/F/FJ/NR, início explícito da aula, todos presentes, cópia da primeira aula realizada e cancelamento com motivo.
- **Atividades e notas:** formulário, lista por bimestre, edição/exclusão e campos individuais de nota.
- **Perfil:** matrícula, observações, transferência, desempenho e frequência dos quatro bimestres e resumo anual de frequência.
- **Boletim:** tabela horizontal, totais e ordenação por nota/faltas.
- **Importação por foto:** câmera/galeria, recorte, várias páginas, revisão e confirmação transacional.

Portanto, a estrutura atual ainda não corresponde às quatro páginas principais solicitadas. Configurações ficam espalhadas em formulários. A seleção de turma usa estado de tela; não há preferência durável para lembrar a última turma em uma abertura nova do aplicativo.

### 2.4. Estrutura atual do Room

Todas as tabelas abaixo pertencem ao mesmo banco versão 1. IDs de turma, matrícula e atividade são textos, usualmente UUID. Datas são textos ISO `yyyy-MM-dd`.

| Tabela / entidade | Identidade e conteúdo principal | Relações e preservação |
| --- | --- | --- |
| `classes` / `SchoolClass` | `id`; nome, etapa, disciplina, ano, turno, duração; sete quantidades semanais em texto; escala, faixas e preferências de frequência | Raiz de turmas; não contém data de criação usada para limitar acesso |
| `students` / `Student` | `id` da matrícula, `personId` da pessoa, turma, nome, número, ingresso, saída, status e observações | FK para turma com `RESTRICT`; transferência cria matrícula nova conservando `personId` |
| `terms` / `Term` | PK composta `(classId, number)`; início/fim dos quatro períodos | FK para turma com `RESTRICT` |
| `lessons` / `Lesson` | `id`, turma, data, ordinal, estado, observação | Índice único `(classId, date, ordinal)`; FK para turma com `RESTRICT` |
| `marks` / `Attendance` | PK composta `(lessonId, studentId)`; P/F/FJ/NR | FKs de aula e matrícula com `RESTRICT`; cada horário tem sua chamada |
| `activities` / `Activity` | `id`, turma, nome, tipo, data, bimestre, máximo, descrição e observações | FK para turma com `RESTRICT` |
| `grades` / `Grade` | PK composta `(activityId, studentId)`; nota anulável | FK para matrícula com `RESTRICT`; exclusão da atividade usa `CASCADE` para suas notas |
| `import_drafts` / `ImportDraft` | `id`, turma, nome, número, posição/página, motivo, revisão e confirmação de homônimo | Rascunho separado das matrículas; FK para turma com `RESTRICT` |

Há validação de número de chamada no Repository, mas não um índice único de número de aluno por turma no esquema. Relações entre tabelas existem; algumas invariantes entre turma, aula e matrícula são verificadas pelo Repository. Não introduzir restrições novas sem examinar os dados legados.

`exportSchema = true` e o argumento KSP apontam para `app/schemas`; a árvore auditada não contém o JSON versionado do esquema. Ele é produzido na compilação e incluído no artefato de resultados. Não há migração entre versões configurada, pois a versão atual é 1. Não há uso de `fallbackToDestructiveMigration`.

### 2.5. Funcionalidades existentes e lacunas

| Módulo | Implementado no código | Parcial ou ausente |
| --- | --- | --- |
| Turmas | Cadastro/edição, matrícula manual/lista/CSV, nomes, números, status, observações, reordenação e transferência com histórico | CSV importa nomes, mas não conserva a numeração do arquivo; prévia de duplicidades/homônimos é limitada; fotografia continua presente |
| Frequência | Calendário anual, aulas múltiplas, P/F/FJ/NR, cópia, cancelamento, retroatividade pela data da aula, totais e tratamento da matrícula | Fluxo principal baseado em horários semanais; quantidade por data exige adicionar aulas individualmente; não há tabela compacta P/F |
| Atividades e notas | CRUD básico, decimais, pendência distinta de zero, soma limitada à escala, parcial, perfil e faixas de cor configuráveis | Recuperação tem apenas funções puras/testes, sem persistência/regra aplicada ao boletim; duplicação entre turmas ausente |
| Relatórios | Boletim na tela, ordenação por nota/faltas, perfil e resumo anual de frequência | Página dedicada, filtros de faltosos completos, relatório anual consolidado e política de nota anual ainda faltam |
| Exportação | Importação CSV existe, mas não é exportação | Sem exportação CSV/PDF/XLSX, cópia ao conselho ou ordem independente |
| Configurações | Ano, bimestres, escala e preferências da turma em formulários; tema segue o sistema | Sem central de configurações, escolha manual de tema, backup/restauração e última turma durável |

As turmas já aparecem por `class.year == anoSelecionado`, sem filtro de data de criação. Já é possível selecionar datas passadas e acrescentar uma aula extraordinária. O objetivo futuro é tornar esses caminhos diretos e independentes da grade semanal, preservando os dados existentes.

### 2.6. Problemas e riscos identificados

| ID | Evidência / consequência | Encaminhamento planejado |
| --- | --- | --- |
| P01 | Não existe backup/restauração; `allowBackup=false` desativa o backup automático Android | Prioridade máxima: cópia completa local e restauração validada antes da reformulação |
| P02 | APK usa assinatura debug padrão, sem chave estável de distribuição configurada/conservada pelo workflow | Verificar certificados do APK instalado e da atualização; definir continuidade da chave antes de distribuir |
| P03 | Esquema JSON v1 não versionado; testes atuais não exercitam atualização real de uma instalação anterior | Congelar base v1 e testar migrações aditivas e instalação por cima |
| P04 | Navegação por rotas em texto e configurações dispersas; última turma apenas em estado de tela | Quatro páginas e contexto global persistente, com proteção para formulários ainda não salvos |
| P05 | Formulário enfatiza grade semanal; `saveClass`/`saveTerms` regeneram aulas previstas | Desacoplar a chamada da grade e preservar todos os registros legados; não regenerar história ao navegar |
| P06 | Ingresso padrão no ano corrente usa a data atual; matrícula anterior precisa ser informada para aparecer em chamada retroativa | Explicitar data real de ingresso; nunca usar criação da turma como início da matrícula nem retroagir matrículas automaticamente |
| P07 | Alterar bimestres redefine filtros de frequência por data, enquanto atividades mantêm bimestre explícito | Mostrar impacto e exigir consistência entre períodos, atividades e relatórios |
| P08 | Ordem de chamada usa `Student.number`; não existe configuração de exportação | Criar disposição de exportação própria por matrícula, sem renumerar chamada |
| P09 | Salvamento é assíncrono; uma fila em memória não comprova persistência da última edição após encerramento abrupto | Diferenciar editando/salvando/salvo/erro e testar saída, retorno e interrupção do processo |
| P10 | `DiarySnapshot` reúne dados de todas as turmas e calcula agregações repetidas nas telas | Medir desempenho; compartilhar cálculos e limitar consultas quando necessário, sem infraestrutura excessiva |
| P11 | README e validação antiga ainda descrevem falha local anterior ao CI bem-sucedido | Registrar aqui a evidência atual; atualizar documentos históricos em etapa autorizada posterior |

P01–P08 e P11 são lacunas/comportamentos observáveis na implementação. P09 e P10 são riscos de durabilidade/usabilidade e escala a testar, não afirmações de perda/corrupção já reproduzida. Não foi identificado erro de compilação pendente na base auditada.

### 2.7. Compilação, assinatura e evidência disponível

A execução [38922054797](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38922054797), para a base auditada, terminou com sucesso nos dois jobs:

- JDK 17, SDK Platform 35 e Build Tools 35.0.0; `testDebugUnitTest assembleDebug assembleDebugAndroidTest` concluídos.
- APK existente em `app/build/outputs/apk/debug/app-debug.apk`, assinatura verificada por `apksigner` e SHA-256 incluído no artefato.
- Artefato [diario-de-classe-apk](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38922054797/artifacts/12054798579), com retenção configurada de 30 dias a partir da execução.
- Nove testes instrumentados passaram em emulador Android 11/API 30: persistência, importação, matrícula, chamadas, notas, OCR integrado sem permissão de Internet e abertura/recriação da Activity.
- A suíte de domínio contém 33 cenários, também executados localmente anteriormente. A prova de compilação não equivale à validação manual de todas as telas, aparelhos e câmeras.

`.github/workflows/android.yml` dispara por push em `main`, pull request e execução manual; `.github/workflows/device-tests.yml` oferece execução manual adicional. A falha inicial de instalação do SDK foi corrigida selecionando `platform-tools`, em vez do pacote antigo `tools`.

Versão atual: `versionCode=1`, `versionName=0.1.0`. Não existe assinatura release própria configurada. Builds debug em runners diferentes podem gerar certificados diferentes; mesmo `applicationId` não basta para permitir atualização. **Não orientar a desinstalação como solução de atualização:** isso pode apagar o banco privado.

## 3. Regras transversais obrigatórias

| Regra | Contrato para todas as próximas etapas |
| --- | --- |
| R01 — Aplicativo único | Manter `br.com.diariodeclasse`, o projeto e o banco `diario.db`; nenhuma segunda aplicação ou base paralela para substituir dados |
| R02 — Offline | Cadastro, chamada, notas, relatórios e geração de arquivos funcionam localmente, sem conta, servidor ou acesso obrigatório à Internet |
| R03 — Histórico | Não apagar/recriar turmas, matrículas, aulas, chamadas ou notas para reorganizar a interface; conservar IDs e vínculos |
| R04 — Atualização | Esquema novo só com migração explícita testada, sem fallback destrutivo; identidade, certificado e versão são verificados antes da instalação |
| R05 — Ano completo | Turmas disponíveis de janeiro a dezembro do próprio ano letivo, independentemente de quando foram cadastradas; outros anos continuam consultáveis |
| R06 — Matrícula | Frequência/atividades respeitam ingresso e saída reais, inclusivos; uma data anterior ao ingresso não recebe falta automática |
| R07 — Frequência | NR/registro ausente não significam F; aula cancelada ou não realizada não entra no cálculo; cada horário conta separadamente |
| R08 — Notas | Zero é nota lançada; ausência de nota é pendência. Mudança de turma/bimestre na navegação não apaga nem move valores |
| R09 — Coerência | Consultas, relatórios e exportações usam as mesmas regras de nota, período, matrícula e frequência |
| R10 — Operações | Exclusão/substituição de dados requer confirmação explícita; gravações relacionadas são transacionais; falhas deixam os dados anteriores válidos |
| R11 — Exportação | Ocultar/reordenar para exportar não muda matrícula, número, chamada ou nota; linhas vazias são posições preservadas |
| R12 — Interface | Português brasileiro, controles acessíveis, valores além das cores, suporte a telas pequenas/tablets e temas claro/escuro |

## 4. Arquitetura visual planejada

Serão **quatro páginas principais**, com barra inferior em celular e adaptação equivalente em telas maiores. Configurações ficam em um menu comum, sem criar uma quinta página principal obrigatória. Perfis, edição de atividade e lançamento detalhado são telas secundárias.

| Página | Conteúdo principal | Módulos relacionados |
| --- | --- | --- |
| **Cadastrar Turma** | Lista/seleção, criar/editar turma, alunos, transferências, lista e CSV | 1 e acesso ao 6 |
| **Fazer Chamada** | Ano/calendário, data livre, número de horários, tabela P/F e totais | 2 |
| **Atividades e Notas** | Bimestre, avaliações, lançamento, boletim, recuperação e perfil | 3 |
| **Relatórios** | Notas, frequência, faltosos, bimestres/ano e ações de exportação | 4 e 5 |

### 4.1. Contexto global

- Um seletor de turma visível nas quatro páginas, acompanhado do ano e da disciplina para distinguir turmas de mesmo nome. Listar todas as turmas do ano selecionado; não limitar por data atual, grade semanal ou data de cadastro.
- Guardar de forma durável o ID da última turma e o ano, usando uma solução simples de preferências, como DataStore. Guardar IDs, não posições na lista. Preferência inválida oferece seleção segura, sem criar ou apagar dados.
- Manter o bimestre coerente entre notas e relatórios. A página de chamada conserva a data escolhida, informa o bimestre correspondente e continua permitindo navegar pelo ano completo. Datas fora dos quatro períodos podem receber aula no ano da turma, sendo identificadas como “fora dos bimestres” e incluídas no total anual.
- Antes de trocar de turma, resolver edições pendentes; cada gravação conserva os IDs da turma, matrícula, aula/atividade que a originaram. Uma troca visual não redireciona uma gravação já iniciada.
- Estados vazios devem orientar a criar/selecionar uma turma. Criar turma precisa funcionar sem seleção prévia. “Voltar” não deve apagar trabalho já confirmado.

### 4.2. Organização técnica futura

Manter inicialmente um único módulo `:app`, Room e Repository. Separar responsabilidades por pacotes/telas de turma, frequência, notas, relatórios e configurações conforme forem alteradas. Compartilhar componentes de seletor, tabelas, validações e cálculos. Não criar seis módulos Gradle, microserviços, sincronização ou camadas abstratas sem necessidade demonstrada.

A navegação deve identificar destinos e contexto com clareza; o mecanismo concreto pode ser simples. Centralizar mutações no Repository e extrair regras puras testáveis quando necessário. O estado de apresentação deve distinguir carregar, editar, salvar e falhar. Reorganizar arquivos não exige mudar o esquema ou regravar entidades.

## 5. Módulo 1 — Gestão de Turmas

### Estado atual

Cadastro e edição de turma, alunos e transferências já existem. Há lista/CSV e OCR. Os campos semanais estão no formulário de turma. A turma não tem bloqueio por data de criação; o filtro é por ano.

### Requisitos planejados

- **M1.01:** criar/editar nome, etapa, disciplina, ano e turno; duração pode permanecer como preferência opcional. Horários semanais não são pré-requisito para salvar nem para chamar.
- **M1.02:** disponibilizar toda turma durante seu ano completo. Exemplo: turma cadastrada em outubro de 2026 permite escolher março de 2026. A presença dos alunos nessa data depende de sua matrícula real.
- **M1.03:** manter nome, número, identidade interna, `personId`, status, ingresso/saída e observações. Alunos transferidos/remanejados continuam visíveis no histórico pertinente.
- **M1.04:** cadastro individual, colagem com um nome por linha e CSV UTF-8, sempre com prévia, correção, contagem de incluídos/ignorados e confirmação. Conservar números explícitos válidos; tratar conflitos/homônimos sem descartar silenciosamente pessoas diferentes.
- **M1.05:** ordem alfabética e ordem oficial da chamada, com reordenação manual. Alterar nome/número/posição não troca o ID usado por notas e chamadas.
- **M1.06:** transferência conserva matrícula e registros anteriores, cria vínculo de destino e impede dupla contagem indevida na data de transição.
- **M1.07:** retirar os acessos de câmera/galeria/OCR da experiência futura e, após verificar referências, as dependências exclusivas de fotografia. Alunos já importados são matrículas comuns e permanecem intactos. Manter a tabela legada `import_drafts` e seus dados; oferecer posteriormente consulta/conversão dos nomes pendentes para a revisão de lista, sem confirmação automática nem descarte silencioso. Não executar `DROP TABLE` como efeito da retirada visual.

**Aceitação:** criar turma sem grade, acessar qualquer data do ano, importar lista/CSV revisado e transferir aluno mantendo suas notas/chamadas. A atualização sem OCR conserva todos os IDs e totais anteriores.

## 6. Módulo 2 — Frequência

### Estado atual

O calendário e a contagem por aula existem. Aulas são geradas pela grade semanal ou adicionadas uma a uma como extraordinárias. O formulário atual de chamada usa cartões P/F/FJ/NR e exige iniciar a aula como realizada.

### Requisitos planejados

- **M2.01:** calendário anual com seleção livre de datas dentro do ano da turma, inclusive retroativas; nenhuma exigência de dia da semana cadastrado ou de aula previamente gerada.
- **M2.02:** ao escolher a data, o professor informa a quantidade de horários/aulas daquele dia. Como limite inicial, reaproveitar 1–12; zero significa nenhuma aula, sem faltas. Não exigir hora de relógio nem grade semanal. Recuperar aulas e registros já existentes na data, sem duplicar a chave `(turma, data, ordinal)`.
- **M2.03:** tabela compacta com número/nome na linha e P/F por horário, cabeçalhos claros e área de toque adequada. NR é o estado inicial de célula não preenchida. Manter FJ e observações em ação complementar acessível; não converter registros FJ legados para P ou F.
- **M2.04:** marcar todos presentes e copiar uma aula são atalhos explícitos. Substituir marcações existentes exige confirmação. Copiar NR produz NR no destino, sem conservar uma falta antiga inadvertidamente.
- **M2.05:** salvar por aula/célula com indicação de pendências e erros; reabrir mostra exatamente a data e os horários gravados. Alterar o número de horários nunca exclui chamadas existentes: para horários já registrados, usar cancelamento explícito ou manter o histórico.
- **M2.06:** aula cancelada/não realizada continua no calendário e sai dos totais. Reativação também é explícita. Acesso ao calendário, criação de turma ou troca de filtros não gera faltas.
- **M2.07:** edição retroativa recalcula bimestre e ano a partir do registro. Dados do calendário legado permanecem editáveis; grade semanal pode ficar como informação legada/opcional, sem ser requisito nem fonte de regeneração automática do passado.

### Contrato de cálculo

Para uma matrícula e período, considerar somente aulas **realizadas** cuja data esteja no intervalo inclusivo de ingresso/saída. O denominador da frequência é o número de marcações válidas P/F/FJ dessas aulas; NR e ausência de marcação ficam fora. Mostrar também total de aulas realizadas elegíveis e quantidade ainda sem registro, para não apresentar uma frequência incompleta como cobertura integral.

- `P`: presenças; `F`: faltas comuns; `FJ`: faltas justificadas, sempre em total separado.
- Se FJ conta como ausência: `frequência = 100 × P / (P + F + FJ)`.
- Se FJ é abonada pela configuração: `frequência = 100 × (P + FJ) / (P + F + FJ)`.
- Denominador zero: “sem registros válidos”, sem inventar 0% ou 100%.
- Duas aulas, P/F: uma presença e uma falta. F/F: duas faltas.
- Não inferir falta por comparação com aulas previstas, calendário vazio ou matrícula adicionada posteriormente.

**Aceitação:** turma sem grade → qualquer data do ano → escolher dois horários → registrar P/F → reabrir e ver uma falta. Repetir com FJ/NR, cancelamento, matrícula tardia e dados antigos.

## 7. Módulo 3 — Atividades e Notas

### Estado atual

Avaliações, notas decimais e soma por bimestre existem; campo vazio é pendente. `SchoolRules.recovery` contém quatro regras puras, mas ainda não há atividade/configuração de recuperação integrada à persistência e às telas.

### Requisitos planejados

- **M3.01:** avaliações por turma/bimestre com nome, tipo, data, máximo, descrição e observações; editar e excluir com confirmação. Reduzir máximo não pode invalidar notas existentes silenciosamente.
- **M3.02:** lançamento rápido em tabela; aceitar vírgula decimal, validar `0 ≤ nota ≤ máximo`, conservar pendências e sinalizar erro de gravação. Não converter campo vazio em zero.
- **M3.03:** mostrar soma lançada, máximo disponível, pendências e resultado parcial/final. Manter a regra atual de soma limitada à escala, sem normalização proporcional implícita. Exibir escala e faixas configuráveis com valores numéricos além da cor.
- **M3.04:** recuperação explicitamente configurada: substituir avaliação específica; usar recuperação se maior que a nota bimestral; somar até limite; ou apenas registrar. Persistir vínculo/regra e mostrar nota anterior, recuperação e resultado. Não reescrever as notas originais para simular o resultado.
- **M3.05:** filtros de recuperação obrigatória/facultativa/dispensa conforme limites editáveis; resultado pendente continua identificado como incompleto.
- **M3.06:** perfil com atividades, notas, frequência, observações e quatro bimestres. Duplicação futura de atividade copia definição para a turma escolhida, sem copiar notas ou IDs das matrículas.

A primeira versão reorganizada utilizável precisa do cadastro/lançamento/soma e consulta por aluno. Recuperação integrada e duplicação são incrementos próprios no roadmap. A regra de nota anual não será presumida: até configuração explícita da escola/professor, mostrar os quatro resultados e a situação de pendência, sem declarar aprovação/reprovação anual automática.

**Aceitação:** lançar 0, nota decimal e pendência, alternar turma/bimestre e reiniciar sem perda; soma e filtros coerentes. Cada regra de recuperação deve ter exemplo esperado, teste e transparência na tela antes de ser disponibilizada.

## 8. Módulo 4 — Relatórios

### Estado atual

Boletim e perfil fornecem consultas; não há central própria de relatórios nem arquivos exportados. Existe resumo anual de faltas por aluno, mas não um fechamento anual com regra de nota definida.

### Requisitos planejados

- **M4.01:** página Relatórios com seleção de turma/ano, bimestre ou ano completo e acesso ao aluno.
- **M4.02:** notas em ordem crescente/decrescente; frequência com faltas comuns e justificadas separadas; faltosos com critério/limite visível e editável, incluindo tratamento explícito de FJ.
- **M4.03:** resultado bimestral por turma e relatório individual; resumo anual com quatro bimestres, faltas totais e identificação de períodos incompletos.
- **M4.04:** títulos com turma, disciplina, ano e período. Mostrar cobertura da chamada e notas pendentes; não ordenar um aluno sem nota como se tivesse nota zero sem sinalização.
- **M4.05:** reutilizar as regras dos módulos 2 e 3. Filtros de relatório não alteram nem excluem alunos. Consultas básicas chegam antes da exportação avançada.

**Aceitação:** totais da mesma matrícula/período coincidem em chamada, notas, perfil e relatório; cancelamento/edição atualiza as consultas; sem dados há mensagem clara.

## 9. Módulo 5 — Exportação Inteligente

### Estado atual

Não implementado. A importação CSV de alunos não fornece a exportação de notas. A chamada usa o número oficial, sem disposição independente para conselho.

### Requisitos planejados

- **M5.01:** prévia de exportação com disposição salva por turma (e nome de modelo, se necessário), independente da ordem/número da chamada.
- **M5.02:** permitir ocultar alunos **somente dessa saída**, mover suas posições e inserir/remover linhas vazias explícitas. A linha de exportação referencia o ID da matrícula ou é um espaço vazio; não usa nome/número como identidade.
- **M5.03:** conservar posições ao reabrir e acrescentar alunos novos ao final, sem deslocar posições personalizadas. Transferidos podem permanecer na disposição histórica. Renomear/reordenar a chamada não rearranja automaticamente a exportação.
- **M5.04:** copiar notas para o conselho como texto tabulado, incluindo modo “somente notas” sem cabeçalho. Preservar tabulações, linhas vazias, nota zero e células pendentes vazias; informar quais colunas e em que ordem serão copiadas. Copiar apenas após ação do professor.
- **M5.05:** CSV UTF-8 compatível com Excel, com delimitador/decimal definidos para pt-BR, escape correto de aspas e campos e opção de BOM. Excel pode abrir CSV; XLSX é um formato adicional, não pré-requisito para essa compatibilidade.
- **M5.06:** PDF retirado da versão 1.0. Pode ser produzido externamente no Excel a partir do futuro XLSX; não implementar gerador PDF no aplicativo.
- **M5.07:** XLSX somente após uma prova técnica de compatibilidade Android, memória, tamanho, licença e leitura no Excel/LibreOffice. Se não viável, entregar CSV e cópia tabulada e registrar a limitação.
- **M5.08:** seletor de arquivos Android para salvar, sem permissão ampla de armazenamento nem envio a servidor. Arquivos são derivados dos registros; exportar não modifica o banco escolar.

Exemplo de disposição: linha 1 = matrícula C; linha 2 = vazia; linha 3 = matrícula A; matrícula B oculta. Tanto a cópia de notas quanto os formatos que usam essa disposição devem conservar o espaço da linha 2. A chamada continua em sua própria ordem A/B/C. Metadados impressos e cabeçalhos devem ser mostrados na prévia, sem deslocar silenciosamente as linhas destinadas ao conselho.

**Aceitação:** ocultar/mover/inserir espaço, salvar e reabrir; os mesmos espaços aparecem na prévia, texto copiado e CSV. Número oficial e totais permanecem iguais antes/depois. XLSX não bloqueia uma versão utilizável.

## 10. Módulo 6 — Configurações

### Estado atual

Há configurações por turma e tema pelo sistema. Não há backup/restauração, central de preferências ou memória durável da última seleção.

### Requisitos planejados

- **M6.01:** ano selecionado e quatro bimestres editáveis, com validação de limites e sobreposição. Sugerir intervalos não equivale a homologar o calendário escolar.
- **M6.02:** centralizar escala, faixas e regra de FJ sem perder diferenças já configuradas por turma; explicitar o alcance de cada preferência. Tema: sistema/claro/escuro. Guardar última turma, ano e preferências locais de navegação.
- **M6.03:** backup manual completo com turmas, matrículas, períodos, aulas, chamadas, avaliações, notas e configurações de exportação quando existirem; preservar também dados legados e rascunhos de importação ainda não resolvidos.
- **M6.04:** arquivo versionado com versão do formato/esquema, identificação do app e integridade verificável; seletor Android para criar/abrir. Ler um snapshot consistente, sem simplesmente copiar um banco aberto ignorando arquivos WAL.
- **M6.05:** restauração com validação de formato, versão, integridade e vínculos antes da substituição; prévia de conteúdo e confirmação explícita. Fazer cópia de segurança da base atual antes de substituir. Se falhar, preservar a base anterior. A versão inicial restaura um conjunto completo; não oferecer fusão parcial sem regras/testes próprios.
- **M6.06:** backup/restauração funcionam offline; arquivo gerado só é colocado no destino escolhido pelo professor. Nenhum dado pessoal é incluído em logs, fixtures, repositório ou artefatos de teste.

**Aceitação:** backup → alteração controlada em base sintética → restauração reproduz todos os IDs, vínculos, notas, chamadas e totais; arquivo corrompido ou de versão incompatível não muda a base. O backup básico é prioridade de segurança, independentemente da exportação avançada.

## 11. Estratégia de preservação e atualização

### 11.1. Identidade e assinatura

Conservar `applicationId`, nome do banco e identidade das entidades. Incrementar `versionCode` em novas versões distribuídas. Antes da primeira atualização, comparar o certificado do APK já instalado com o da versão candidata; documentar a posse/guarda da chave estável fora do repositório e do APK.

Se a chave do debug instalado não puder ser recuperada, **não há garantia de instalação por cima preservando o banco privado**. Interromper a distribuição dessa atualização até definir e validar um resgate local dos dados da versão antiga. Para builds debug, um procedimento autorizado de suporte via ADB pode ser estudado e testado com cópia consistente do SQLite e WAL; isso não está implementado nem executado nesta etapa. Nunca prometer que uma chave nova atualizará um APK assinado por outra.

### 11.2. Evolução do Room

1. Obter/versionar o esquema exato v1 gerado pela base compilada e uma fixture de banco sintética representativa.
2. Preferir manter o esquema para mudanças exclusivamente de navegação. Se necessário, adicionar campos/tabelas com valores padrão explícitos e migração progressiva.
3. Preservar IDs, matrículas transferidas, índices e relações. Comparar registros e agregações antes/depois, além de contagens.
4. Testar cada caminho de versão suportado com `MigrationTestHelper`, incluindo erro/rollback. Não criar a migração antes de fechar o desenho da alteração correspondente.
5. Preservar `import_drafts` após retirar o OCR e os campos legados de grade semanal ao simplificar a chamada. Evitar exclusão de estrutura sem necessidade.
6. Testar atualização real do APK sobre uma instalação com dados fictícios e certificado compatível. Reabrir e conferir aulas duplas, FJ, pendências, zero, transferências e bimestres.

A tabela futura de disposição de exportação e os metadados de recuperação serão detalhados nas respectivas etapas; esta especificação define seus contratos, não determina uma alteração imediata para versão 2.

## 12. Validação e critérios gerais de entrega

Cada incremento deve incluir teste unitário das regras afetadas, integração Room quando houver persistência e validação de interface do fluxo alterado. A base já testada não dispensa testes de migração, assinatura ou atualização.

Conjunto mínimo de regressão: turmas de anos diferentes; matrícula anterior/posterior à data; transferência; duas aulas P/F; FJ nas duas políticas; NR; cancelamento/reativação; nota zero/decimal/pendente; quatro bimestres; alternância rápida de contexto; reabertura; backup/restauração; exportação sem alterar chamada.

Antes de distribuir código novo: testes pertinentes aprovados, `assembleDebug` ou build da variante estável aprovado, APK existente e assinatura conferida; instalar sem desinstalar e conferir dados sintéticos. Testes reais de câmera não serão requisito da arquitetura futura após a retirada do OCR, mas os dados já importados continuam na regressão.

O primeiro marco utilizável reorganizado será: **turmas + frequência por data sem grade obrigatória + avaliações/notas + consultas básicas**, com preservação e backup validados. Exportação inteligente, XLSX e refinamentos adicionais não bloqueiam esse marco. A autorização para implementar começa na Etapa 1 do [ROADMAP](ROADMAP.md); nenhuma implementação modular faz parte da Etapa 0.

## Adendo vigente — Registros de Aula facultativos (Etapa 5.3)

[ETAPA53.md](ETAPA53.md) define o módulo secundário de texto livre por turma/data. Não altera os seis módulos escolares nem as quatro páginas principais. Sem pendências obrigatórias, ligação com horários/chamadas ou campos burocráticos. Vários registros por dia, operações explícitas e cópia exata. Preservação via Room v6 e backup 7 com leitura dos anteriores; etapa visual final depende de autorização.
