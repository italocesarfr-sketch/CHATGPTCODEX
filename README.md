# Noctua

Anteriormente Diário de Classe. Incremento **0.4.1 / código 7**: nome Android/cabeçalho e coruja discreta, com o mesmo pacote `br.com.diariodeclasse`, banco `diario.db` e funcionalidades aprovadas da 0.4.0. [Consolidação e testes](docs/NOCTUA.md). **A primeira release permanente depende do proprietário:** siga o [passo a passo de assinatura](docs/ASSINATURA_PASSO_A_PASSO.md). O APK debug continua sendo de teste. A versão 0.4.0 abaixo permanece como histórico funcional.

[Baixar APK debug Noctua 0.4.1](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084473884) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182) • [Resultados](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084449177). 24 testes JVM, 63 Android e dez fases de sondas aprovados. Atualização release controlada e rejeição de certificado incompatível verificadas. **Este APK é de teste; a release permanente ainda depende da chave do proprietário.**


Aplicativo Android nativo em Kotlin, Jetpack Compose/Material 3 e Room/SQLite, com ViewModel e Repository. Android 8.0 (API 26) ou superior. Interface em português brasileiro, armazenamento privado e funcionamento offline, sem cadastro ou permissão de Internet.

## Versão 0.4.0 — Primeiro incremento da Etapa 4A

As quatro páginas, o seletor global e os comportamentos da versão 0.3.1 foram preservados. Cada atividade agora tem **Lançar notas**, **Editar** e **Excluir** diretamente na lista. Excluir pede confirmação e mostra quantas notas já foram lançadas, avisando que todas as notas vinculadas serão excluídas permanentemente.

Alterar o máximo com notas lançadas abre uma prévia: **Recalcular proporcionalmente**, **Manter notas existentes** (se couberem no novo máximo) ou **Cancelar**. A alteração e todas as notas são salvas em uma transação Room; zero e pendentes são preservados. O cálculo não arredonda internamente para as duas casas exibidas nos boletins. Os campos de edição preservam a precisão armazenada.

[Baixar APK 0.4.0 (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083077686) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801) • [Resultados e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083451234). Validação: 24 testes JVM, 62 Android, quatro sondas separadas e 28 cenários locais aprovados. APK baixado e hash conferido. Detalhes em [ETAPA4A](docs/ETAPA4A.md). Room v1, backup/restauração, matrícula padrão 01/01, chamadas e salvamento automático continuam existentes. A assinatura permanente permanece pendente antes do uso com dados reais. Os demais incrementos, recuperação, exportação e relatórios avançados aguardam autorização.

Referências: [especificação](docs/ESPECIFICACAO.md), [roadmap](docs/ROADMAP.md), [base 0.3.1](docs/CORRECAO_ETAPA3.md), [Etapa 3](docs/ETAPA3.md), [Etapa 2](docs/ETAPA2.md) e [Etapa 1](docs/ETAPA1.md).

## Compilar e baixar o APK

No repositório `italocesarfr-sketch/CHATGPTCODEX`, abra **Actions → Compilar APK Android**. Pushes em `main` também iniciam o workflow. Após todos os testes aprovados, baixe o artefato **noctua-apk-teste**: contém o APK debug, SHA-256 e metadados públicos de assinatura/compatibilidade. O artefato **resultados-noctua** contém resultados e capturas de tela sintéticas, incluindo os testes de assinatura/atualização release com chaves descartáveis. Para distribuição permanente, use somente o workflow release após a configuração descrita no guia.

O CI configura JDK 17/SDK 35, executa `testDebugUnitTest assembleDebug assembleDebugAndroidTest`, testes de interface/Room/backup em emulador API 30 e atualização da base v1 com a mesma chave descartável. Verifica que o APK publicado é o mesmo testado. A execução e os metadados atuais estão registrados em [NOCTUA](docs/NOCTUA.md); o teste adicional valida também a atualização 0.4.0 para release com chave descartável e a rejeição de assinatura incompatível.

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

Para instalação por USB, confira primeiro os certificados em [ASSINATURA](docs/ASSINATURA.md). `adb install -r app/build/outputs/apk/debug/app-debug.apk` exige mesmo pacote/certificado e versão adequada. Este debug é para testes; **não há garantia de atualização sobre instalações anteriores**. A assinatura permanente precisa ser configurada antes do uso real. A autorização para instalar uma versão limpa com dados descartáveis não comprova compatibilidade de assinatura. Nunca são publicadas chaves privadas.

## Utilização

1. **Cadastrar Turma → Nova Turma**: nome, disciplina, ano, etapa e turno. Salve sem precisar definir horários semanais. Configurações adicionais opcionais mantêm duração, escala, preferências de frequência e horários anteriores.
2. **Gerenciar alunos**: adicione, edite ou transfira alunos. O ingresso padrão é 01/01 do ano letivo; altere ingresso/saída apenas para uma matrícula real diferente. A data de cadastro no aplicativo não limita a retroatividade. Matrículas antigas mantêm suas datas. A ordem alfabética é a visualização padrão; escolha Ordem da chamada para mover posições ou editar números.
3. **Colar lista / Importar CSV**: UTF-8, um nome por linha, com número opcional. Exemplo CSV: `numero;nome`. Nomes/números permanecem em rascunho até a conferência de cada linha e a confirmação final. Resolva duplicidades/números repetidos; homônimos exigem confirmação explícita. Rascunhos das versões com fotografia também aparecem aqui. A importação por fotografia foi retirada.
4. **Fazer Chamada**: escolha mês/data e toque em Registrar horários depois de informar a quantidade (1–12). Marque P/F diretamente, ou FJ/NR no menu da célula. Marcação coletiva, cópia e cancelamento exigem confirmação. Horários têm registros independentes. Horários → Alterar permite reduzir com cancelamento explícito, mantendo as marcações. Abra Bimestre para a tabela geral cronológica e os totais F/FJ. NR nunca é falta; matrícula delimita a frequência. Bimestres permite editar os períodos com prévia de impacto. Aulas previstas antigas só contam depois de confirmadas como realizadas.
5. **Atividades e Notas**: escolha bimestre, crie atividade e lance notas. Campo vazio é pendente; zero é válido. Valores válidos são salvos automaticamente. Os botões Editar e Excluir ficam na lista. Editar permite prévia proporcional ao mudar o máximo; excluir conta notas e exige confirmação permanente. Notas inválidas são sinalizadas; ao trocar de contexto, há aviso de que o valor não foi salvo.
6. **Relatórios**: boletim bimestral com atividades, totais e faltas comuns/justificadas, ordenação por notas/faltas e perfil individual com histórico anual. Tabela com rolagem horizontal.
7. **Backup**, na barra superior: exporte arquivo completo pelo seletor Android. Restaurar exige prévia validada, digitar RESTAURAR e cria cópia privada dos dados anteriores. Consulte [BACKUP](docs/BACKUP.md).

## Estrutura e preservação

- `data/`: Room v1, Repository com transações e seleção persistente por ID/ano.
- `domain/`: cálculos de notas/frequência, normalização/duplicidades e leitor de CSV/texto.
- `ui/`: quatro páginas Compose, tabelas de frequência, formulários, conferência e fila de gravações pertencente ao processo.
- `backup/`: formato versionado, validação, restauração transacional e cópia de segurança.
- `app/schemas/`: referência imutável Room v1, sem migração destrutiva.
- `app/src/test/` e `app/src/androidTest/`: testes com fixtures sintéticas separadas dos dados do usuário.
- `.github/workflows/`: compilação/testes e release com chave estável fornecida externamente.

A fila aguarda o commit antes de indicar Salvo; gravações ainda pendentes podem ser interrompidas se o processo for encerrado. Erros da chamada ficam visíveis até resolver ou descartar explicitamente a intenção não salva. O backup novo inclui o contexto visual e lê também backups antigos. Edições de nomes/ordem usam os mesmos IDs; transferências preservam a matrícula de origem. Chamadas realizadas/canceladas não são apagadas ao editar a turma. Nenhum banco real ou segredo integra o repositório/artefatos.

## Próximas etapas

Aguardar autorização para os próximos incrementos da Etapa 4A e seguintes. Recuperação integrada, exportação inteligente PDF/CSV/XLSX, fechamento anual e refinamento amplo de acessibilidade/desempenho seguem o ROADMAP. Relatórios atuais são consultas na tela. Não há feriados oficiais automáticos. As capturas e testes do CI não substituem a validação posterior em aparelho físico.
