# Noctua

Incremento atual: [Etapa 4C — Remanejamento seguro](docs/ETAPA4C.md), **Noctua 0.7.0 / código 11 — entregue**. Uma matrícula vinculada no destino consolida notas, chamadas e recomposição preservadas na origem, sem copiar registros escolares. Prévia/confirmação, origem oculta com consulta histórica e destino externo. 55 testes JVM e 101 Android aprovados; atualização assinada 0.6.1 → 0.7.0 comprovada com migração aditiva e backups compatíveis. [Baixar APK de teste assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39055280256/artifacts/12109269070) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39055280256). Limitações no documento do incremento. Etapa 5 não iniciada.

Histórico: [Etapa 4B.3 — Consolidação dos relatórios](docs/ETAPA4B3.md), **Noctua 0.6.1 / código 10 — entregue**. Relatórios bimestrais, individuais e anuais distinguem original, recomposição, vigente e pendências, com frequência e matrícula integradas. 45 testes JVM e 87 Android aprovados; atualização sobre a release 0.6.0 comprovada com a mesma assinatura e nove tabelas preservadas, sem mudança do esquema. [Baixar APK de teste assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375/artifacts/12106789781) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375). Detalhes e limites no documento do incremento. Etapa 5 não iniciada.

Histórico: [Etapa 4B.2 — Recomposição de Notas](docs/ETAPA4B2.md), **Noctua 0.6.0 / código 9 — entregue**. Mantém pacote e assinatura release, com migração aditiva e leitura dos backups anteriores. [Baixar APK de teste assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081/artifacts/12105803073) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081). 39 testes JVM e 80 Android aprovados; atualização sobre a release 0.5.0 comprovada com a mesma assinatura. Detalhes e limites no documento do incremento.

**Histórico — Noctua 0.5.0 / código 8 — Etapa 4B.1 entregue.** Relatórios bimestrais, individuais, frequência, faltosos e quatro bimestres, somente leitura, com regras anteriores e Room preservados. [Baixar APK release](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083/artifacts/12101495282) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083) • [Resultados e limites](docs/ETAPA4B1.md). 31 testes JVM e 70 Android aprovados; atualização sobre a release real 0.4.1 comprovada com a mesma assinatura existente, sem perda dos registros sintéticos. As entregas abaixo permanecem como histórico.

Anteriormente Diário de Classe. Incremento **0.4.1 / código 7**: nome Android/cabeçalho e coruja discreta, com o mesmo pacote `br.com.diariodeclasse`, banco `diario.db` e funcionalidades aprovadas da 0.4.0. [Consolidação e testes](docs/NOCTUA.md). A assinatura foi configurada posteriormente pelo proprietário, e a entrega 0.5.0 acima já a reutiliza. O [passo a passo de assinatura](docs/ASSINATURA_PASSO_A_PASSO.md) continua como referência de guarda e configuração. O APK debug histórico continua sendo de teste. A versão 0.4.0 abaixo permanece como histórico funcional.

[Baixar APK debug Noctua 0.4.1](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084473884) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182) • [Resultados](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084449177). 24 testes JVM, 63 Android e dez fases de sondas aprovados. Atualização release controlada e rejeição de certificado incompatível verificadas. **Este artefato histórico é debug. A release com assinatura existente está no link da versão 0.5.0 acima.**


Aplicativo Android nativo em Kotlin, Jetpack Compose/Material 3 e Room/SQLite, com ViewModel e Repository. Android 8.0 (API 26) ou superior. Interface em português brasileiro, armazenamento privado e funcionamento offline, sem cadastro ou permissão de Internet.

## Versão 0.4.0 — Primeiro incremento da Etapa 4A

As quatro páginas, o seletor global e os comportamentos da versão 0.3.1 foram preservados. Cada atividade agora tem **Lançar notas**, **Editar** e **Excluir** diretamente na lista. Excluir pede confirmação e mostra quantas notas já foram lançadas, avisando que todas as notas vinculadas serão excluídas permanentemente.

Alterar o máximo com notas lançadas abre uma prévia: **Recalcular proporcionalmente**, **Manter notas existentes** (se couberem no novo máximo) ou **Cancelar**. A alteração e todas as notas são salvas em uma transação Room; zero e pendentes são preservados. O cálculo não arredonda internamente para as duas casas exibidas nos boletins. Os campos de edição preservam a precisão armazenada.

[Baixar APK 0.4.0 (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083077686) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801) • [Resultados e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083451234). Validação: 24 testes JVM, 62 Android, quatro sondas separadas e 28 cenários locais aprovados. APK baixado e hash conferido. Detalhes em [ETAPA4A](docs/ETAPA4A.md). Room v1, backup/restauração, matrícula padrão 01/01, chamadas e salvamento automático continuam existentes. A assinatura permanente permanece pendente antes do uso com dados reais. Os demais incrementos, recuperação, exportação e relatórios avançados aguardam autorização.

Referências: [especificação](docs/ESPECIFICACAO.md), [roadmap](docs/ROADMAP.md), [base 0.3.1](docs/CORRECAO_ETAPA3.md), [Etapa 3](docs/ETAPA3.md), [Etapa 2](docs/ETAPA2.md) e [Etapa 1](docs/ETAPA1.md).

## Compilar e baixar o APK

No repositório `italocesarfr-sketch/CHATGPTCODEX`, abra **Actions → Compilar APK Android**. Pushes em `main` também iniciam o workflow. O job de regressão publica **noctua-apk-teste** (debug interno) e **resultados-etapa4c**. Após aprovação, o job `stable-update` usa a chave release existente, verifica assinatura e testa instalação sobre a release 0.6.1. Só então publica **noctua-etapa4c-release**, o APK indicado para atualizar aquela release, e **resultados-atualizacao-release**.

O CI configura JDK 17/SDK 35, executa testes JVM, `assembleDebug`, `assembleDebugAndroidTest`, testes de interface/Room/backup em emulador API 30 e atualização da base v1. O job release executa `assembleRelease`, confere o certificado fixado e a preservação das dez tabelas, incluindo recomposições e vínculos de remanejamento, preferências e backup. Não cria nem substitui chave permanente. O workflow manual **Noctua — APK com assinatura estável** também continua disponível, sem alteração. Resultados e limites em [ETAPA4C](docs/ETAPA4C.md).

### Android Studio / terminal

Requisitos: JDK 17, Android SDK Platform 35, Build Tools 35.0.0 e rede na primeira compilação para baixar Gradle 8.9 e dependências. O aplicativo instalado funciona offline.

1. Abra o projeto no Android Studio e instale os componentes do SDK.
2. Configure `ANDROID_HOME` ou `local.properties` com `sdk.dir=/caminho/do/Android/Sdk`.
3. Execute:

```sh
./scripts/test-domain.sh
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
```

No Windows, use `gradlew.bat`. Após sucesso, o APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalação por USB, confira primeiro os certificados em [ASSINATURA](docs/ASSINATURA.md). `adb install -r app/build/outputs/apk/debug/app-debug.apk` exige mesmo pacote/certificado e versão adequada. Este debug é para testes; **não há garantia de atualização sobre instalações anteriores**. A assinatura permanente já configurada é utilizada pelo workflow release; o debug local não deve ser instalado por cima dela presumindo compatibilidade. Nunca são publicadas chaves privadas. Para atualizar a release 0.6.1 de referência, utilize o APK release validado acima, fazendo backup antes.

## Utilização

1. **Cadastrar Turma → Nova Turma**: nome, disciplina, ano, etapa e turno. Salve sem precisar definir horários semanais. Configurações adicionais opcionais mantêm duração, escala, preferências de frequência e horários anteriores.
2. **Gerenciar alunos**: adicione, edite ou transfira alunos. O ingresso padrão é 01/01 do ano letivo; altere ingresso/saída apenas para uma matrícula real diferente. A data de cadastro no aplicativo não limita a retroatividade. Matrículas antigas mantêm suas datas. A ordem alfabética é a visualização padrão; escolha Ordem da chamada para mover posições ou editar números.
3. **Colar lista / Importar CSV**: UTF-8, um nome por linha, com número opcional. Exemplo CSV: `numero;nome`. Nomes/números permanecem em rascunho até a conferência de cada linha e a confirmação final. Resolva duplicidades/números repetidos; homônimos exigem confirmação explícita. Rascunhos das versões com fotografia também aparecem aqui. A importação por fotografia foi retirada.
4. **Fazer Chamada**: escolha mês/data e toque em Registrar horários depois de informar a quantidade (1–12). Marque P/F diretamente, ou FJ/NR no menu da célula. Marcação coletiva, cópia e cancelamento exigem confirmação. Horários têm registros independentes. Horários → Alterar permite reduzir com cancelamento explícito, mantendo as marcações. Abra Bimestre para a tabela geral cronológica e os totais F/FJ. NR nunca é falta; matrícula delimita a frequência. Bimestres permite editar os períodos com prévia de impacto. Aulas previstas antigas só contam depois de confirmadas como realizadas.
5. **Atividades e Notas**: escolha bimestre, crie atividade e lance notas. Campo vazio é pendente; zero é válido. Valores válidos são salvos automaticamente. Os botões Editar e Excluir ficam na lista. Editar permite prévia proporcional ao mudar o máximo; excluir conta notas e exige confirmação permanente. Notas inválidas são sinalizadas; ao trocar de contexto, há aviso de que o valor não foi salvo. **Recomposição de Notas** abre o lançamento separado por bimestre de origem: nota de 0–10, maior resultado, filtro abaixo de 5 e Todos os alunos. Salve explicitamente; sair com rascunho exige confirmação. Atividades originais não são alteradas.
6. **Relatórios**: escolha Bimestral, Frequência, Mais faltosos ou Resumo anual. Consulte atividades/notas, total e pendências, aulas elegíveis, P/F/FJ, cobertura e frequência. O ranking segue a política de FJ e desempate estável; o anual mostra quatro resultados separados sem inventar média/aprovação. Toque no aluno para relatório individual somente leitura e navegue pelos bimestres. Nomes permanecem visíveis ao rolar as colunas. Original, recomposição e vigente são apresentados separadamente, com P/F/FJ e situação da matrícula. Detalhes em [ETAPA4B3](docs/ETAPA4B3.md).
7. **Remanejamento**: no aluno, Editar → Remanejado → data e destino → Conferir remanejamento → Confirmar. A origem fica em Mostrar remanejados, somente leitura; o destino consolida atividades e horários originais por vínculo. Destinos incompatíveis e recomposições conflitantes são bloqueados; Outra turma registra destino externo sem criar cadastros fictícios. Consulte [ETAPA4C](docs/ETAPA4C.md).
8. **Backup**, na barra superior: exporte arquivo completo pelo seletor Android. Restaurar exige prévia validada, digitar RESTAURAR e cria cópia privada dos dados anteriores. Consulte [BACKUP](docs/BACKUP.md).

## Estrutura e preservação

- `data/`: Room v3, migrações aditivas 1→2→3, Repository com transações e seleção persistente por ID/ano.
- `domain/`: cálculos de notas/frequência, normalização/duplicidades e leitor de CSV/texto.
- `ui/`: quatro páginas Compose, tabelas de frequência, formulários, conferência e fila de gravações pertencente ao processo.
- `backup/`: formato versionado, validação, restauração transacional e cópia de segurança.
- `app/schemas/`: referências imutáveis Room v1/v2 e esquema atual v3, sem migração destrutiva.
- `app/src/test/` e `app/src/androidTest/`: testes com fixtures sintéticas separadas dos dados do usuário.
- `.github/workflows/`: compilação/testes e release com chave estável fornecida externamente.

A fila aguarda o commit antes de indicar Salvo; gravações ainda pendentes podem ser interrompidas se o processo for encerrado. Erros da chamada ficam visíveis até resolver ou descartar explicitamente a intenção não salva. O backup novo inclui o contexto visual e lê também backups antigos. Edições de nomes/ordem usam os mesmos IDs; transferências preservam a matrícula de origem. Chamadas realizadas/canceladas não são apagadas ao editar a turma. Nenhum banco real ou segredo integra o repositório/artefatos.

## Próximas etapas

Etapa 4C concluída: remanejamento seguro com histórico vinculado. Aguardar autorização para os próximos incrementos. Recuperação comum permanece atividade regular, sem automação aditiva. Exportação inteligente PDF/CSV/XLSX, regras de fechamento anual e refinamento amplo de acessibilidade/desempenho seguem o ROADMAP. Relatórios atuais são consultas na tela. Não há feriados oficiais automáticos. As capturas e testes do CI não substituem a validação posterior em aparelho físico.
