# Diário de Classe

Aplicativo Android nativo em Kotlin, Jetpack Compose/Material 3 e Room/SQLite, com ViewModel e Repository. Android 8.0 (API 26) ou superior. Interface em português brasileiro, armazenamento privado e funcionamento offline, sem cadastro ou permissão de Internet.

## Versão 0.2.0 — Etapa 2

Quatro páginas: **Cadastrar Turma**, **Fazer Chamada**, **Atividades e Notas** e **Relatórios**. Todas compartilham o seletor de turma/ano, que memoriza os últimos IDs selecionados. Esta atualização reformula a navegação e a gestão de turmas. **A Etapa 3 não foi implementada**: chamadas, cálculos e boletins usam o fluxo anterior.

[Baixar APK 0.2.0 de teste](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139/artifacts/12072308459) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139) • [Resultados e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139/artifacts/12072841819). São 18 testes JVM, 31 testes Android e duas sondas de atualização aprovados. APK baixado e hash verificado: 10,259,485 bytes.

**Assinatura:** o certificado deste debug difere dos artefatos anteriores 0.1.0 e Etapa 1 analisados. Ele não atualiza diretamente esses APKs; a assinatura da instalação física não foi inspecionada. A chave permanente segue pendente antes do uso real, conforme autorização. Banco Room v1 e backup/restauração foram preservados.

Consulte [especificação](docs/ESPECIFICACAO.md), [roadmap](docs/ROADMAP.md) e [entrega da Etapa 2](docs/ETAPA2.md). A validação da Etapa 1 está registrada em [ETAPA1](docs/ETAPA1.md).

## Compilar e baixar o APK

No repositório `italocesarfr-sketch/CHATGPTCODEX`, abra **Actions → Compilar APK Android**. Pushes em `main` também iniciam o workflow. Após todos os testes aprovados, baixe o artefato **diario-de-classe-apk**: contém o APK, SHA-256 e metadados públicos de assinatura/compatibilidade. O artefato **resultados-etapa2** contém resultados e capturas de tela sintéticas.

O CI configura JDK 17/SDK 35, executa `testDebugUnitTest assembleDebug assembleDebugAndroidTest`, testes de interface/Room/backup em emulador API 30 e atualização da base v1 com a mesma chave descartável. Verifica que o APK publicado é o mesmo testado. A execução e os metadados desta versão estão registrados em [ETAPA2](docs/ETAPA2.md).

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
4. **Fazer Chamada**: selecione bimestre/data. Bimestres permite ajustar os quatro períodos. Em turma sem horário semanal, use **+ Reposição / aula extraordinária** para adicionar cada aula; em seguida inicie e marque P/F/FJ/NR. Copiar chamada e marcar todos presentes pedem confirmação. Aulas duplas têm registros independentes. NR nunca é falta. A turma está disponível de janeiro a dezembro, mas o ingresso do aluno delimita sua frequência.
5. **Atividades e Notas**: escolha bimestre, crie atividade e lance notas. Campo vazio é pendente; zero é válido. Valores válidos são salvos automaticamente. Notas inválidas são sinalizadas; ao trocar de contexto, há aviso de que o valor não foi salvo.
6. **Relatórios**: boletim bimestral com atividades, totais e faltas comuns/justificadas, ordenação por notas/faltas e perfil individual com histórico anual. Tabela com rolagem horizontal.
7. **Backup**, na barra superior: exporte arquivo completo pelo seletor Android. Restaurar exige prévia validada, digitar RESTAURAR e cria cópia privada dos dados anteriores. Consulte [BACKUP](docs/BACKUP.md).

## Estrutura e preservação

- `data/`: Room v1, Repository com transações e seleção persistente por ID/ano.
- `domain/`: cálculos de notas/frequência, normalização/duplicidades e leitor de CSV/texto.
- `ui/`: quatro páginas Compose, formulários, conferência e fila de gravações.
- `backup/`: formato versionado, validação, restauração transacional e cópia de segurança.
- `app/schemas/`: referência imutável Room v1, sem migração destrutiva.
- `app/src/test/` e `app/src/androidTest/`: testes com fixtures sintéticas separadas dos dados do usuário.
- `.github/workflows/`: compilação/testes e release com chave estável fornecida externamente.

O backup novo inclui o contexto visual e lê também backups antigos. Edições de nomes/ordem usam os mesmos IDs; transferências preservam a matrícula de origem. Chamadas realizadas/canceladas não são apagadas ao editar a turma. Nenhum banco real ou segredo integra o repositório/artefatos.

## Próximas etapas

Aguardar autorização para Etapa 3 (nova chamada). Recuperação integrada, exportação inteligente PDF/CSV/XLSX, fechamento anual e refinamento amplo de acessibilidade/desempenho seguem o ROADMAP. Relatórios atuais são consultas na tela. Não há feriados oficiais automáticos. As capturas e testes do CI não substituem a validação posterior em aparelho físico.
