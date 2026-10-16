# Diário de Classe

Aplicativo Android nativo em Kotlin, Jetpack Compose/Material 3 e Room/SQLite, com ViewModel e Repository. Android 8.0 (API 26) ou superior. Interface em português brasileiro, armazenamento privado e funcionamento offline, sem cadastro ou permissão de Internet.

## Versão 0.3.0 — Etapa 3

Quatro páginas: **Cadastrar Turma**, **Fazer Chamada**, **Atividades e Notas** e **Relatórios**, com seletor global de turma/ano memorizado. A chamada agora usa qualquer data do ano letivo, 1–12 horários informados pelo professor, P/F independentes, menu FJ/NR e consulta bimestral. Nenhuma grade semanal é necessária e navegar não gera aulas. Nomes ficam fixos durante a rolagem horizontal.

[Baixar APK 0.3.0 de teste (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986/artifacts/12073719360) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986) • [Relatórios/capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986/artifacts/12074790782). Validação: 18 testes JVM, 44 Android, quatro sondas separadas e 28 cenários de domínio aprovados. APK baixado e hash conferido. Detalhes em [ETAPA3](docs/ETAPA3.md). Banco Room v1, registros antigos, backup/restauração e funcionalidades anteriores foram preservados. **A assinatura estável continua pendente antes do uso real**; verifique o certificado antes de instalar por cima de outra versão. Não há garantia de atualização direta de um APK debug anterior.

Consulte [especificação](docs/ESPECIFICACAO.md), [roadmap](docs/ROADMAP.md) e [entrega da Etapa 3](docs/ETAPA3.md). As entregas anteriores permanecem em [ETAPA1](docs/ETAPA1.md) e [ETAPA2](docs/ETAPA2.md).

## Compilar e baixar o APK

No repositório `italocesarfr-sketch/CHATGPTCODEX`, abra **Actions → Compilar APK Android**. Pushes em `main` também iniciam o workflow. Após todos os testes aprovados, baixe o artefato **diario-de-classe-apk**: contém o APK, SHA-256 e metadados públicos de assinatura/compatibilidade. O artefato **resultados-etapa3** contém resultados e capturas de tela sintéticas.

O CI configura JDK 17/SDK 35, executa `testDebugUnitTest assembleDebug assembleDebugAndroidTest`, testes de interface/Room/backup em emulador API 30 e atualização da base v1 com a mesma chave descartável. Verifica que o APK publicado é o mesmo testado. A execução e os metadados desta versão estão registrados em [ETAPA3](docs/ETAPA3.md).

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
2. **Gerenciar alunos**: adicione, edite ou transfira alunos. Use a data real de ingresso. A ordem alfabética é a visualização padrão; escolha Ordem da chamada para mover posições ou editar números.
3. **Colar lista / Importar CSV**: UTF-8, um nome por linha, com número opcional. Exemplo CSV: `numero;nome`. Nomes/números permanecem em rascunho até a conferência de cada linha e a confirmação final. Resolva duplicidades/números repetidos; homônimos exigem confirmação explícita. Rascunhos das versões com fotografia também aparecem aqui. A importação por fotografia foi retirada.
4. **Fazer Chamada**: escolha mês/data e toque em Registrar horários depois de informar a quantidade (1–12). Marque P/F diretamente, ou FJ/NR no menu da célula. Marcação coletiva, cópia e cancelamento exigem confirmação. Horários têm registros independentes. Horários → Alterar permite reduzir com cancelamento explícito, mantendo as marcações. Abra Bimestre para a tabela geral cronológica e os totais F/FJ. NR nunca é falta; matrícula delimita a frequência. Bimestres permite editar os períodos com prévia de impacto. Aulas previstas antigas só contam depois de confirmadas como realizadas.
5. **Atividades e Notas**: escolha bimestre, crie atividade e lance notas. Campo vazio é pendente; zero é válido. Valores válidos são salvos automaticamente. Notas inválidas são sinalizadas; ao trocar de contexto, há aviso de que o valor não foi salvo.
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

Aguardar autorização para Etapa 4 (organização de notas/consultas). Recuperação integrada, exportação inteligente PDF/CSV/XLSX, fechamento anual e refinamento amplo de acessibilidade/desempenho seguem o ROADMAP. Relatórios atuais são consultas na tela. Não há feriados oficiais automáticos. As capturas e testes do CI não substituem a validação posterior em aparelho físico.
