# Diário de Classe

Projeto Android nativo em Kotlin + Jetpack Compose/Material 3, Room/SQLite, ViewModel e Repository. Android 8.0 (API 26) ou superior. Interface em português brasileiro. Nenhuma conta, servidor ou permissão de Internet.

## Etapa 1 — segurança dos dados

O desenvolvimento atual acrescenta backup/restauração offline e testes de atualização, mantendo as telas atuais. Consulte [BACKUP](docs/BACKUP.md), [ASSINATURA](docs/ASSINATURA.md) e [validação da Etapa 1](docs/ETAPA1.md). O debug do CI é para teste; não há garantia de compatibilidade com a assinatura já instalada. Release estável exige configuração externa segura.

## Estado atual validado

A Etapa 1 foi compilada e testada na [execução 38947014687](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38947014687): 8 testes JVM (incluindo a suíte dos 33 cenários legados), 19 instrumentados e duas sondas de atualização aprovados. O esquema Room continua na versão 1 e não foi alterado.

[Baixar APK debug de teste](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38947014687/artifacts/12067351863). O ZIP contém `app-debug.apk`, SHA-256 e metadados públicos. **O certificado difere do APK original de referência: este debug não pode atualizar diretamente aquela instalação. Não desinstale para contornar isso.** Consulte [assinatura/resgate seguro](docs/ASSINATURA.md).

O botão **Backup** na barra superior cria cópia completa ou abre uma prévia de restauração. A substituição exige digitar RESTAURAR e cria cópia privada dos dados anteriores antes de aplicar a transação. Instruções em [BACKUP](docs/BACKUP.md). Cadastro, chamada, OCR e notas atuais foram preservados; a Etapa 2 não começou.

O ambiente local original não tinha Gradle/SDK e falhou no proxy; essa limitação foi contornada pela compilação e validação remota no GitHub Actions. Os logs locais antigos são registros históricos.

## Caminho mais simples para gerar o APK

### GitHub Actions, sem instalar Android no computador

1. Descompacte este projeto e coloque seu conteúdo na raiz de um repositório GitHub (incluindo `.github/workflows/android.yml`).
2. Abra **Actions → Compilar APK Android → Run workflow**. Um push também inicia a compilação.
3. O workflow instala JDK 17 e Android SDK 35, executa `testDebugUnitTest assembleDebug` e verifica a existência do APK.
4. Se a compilação passar, baixe o artefato **diario-de-classe-apk** e extraia `app-debug.apk`.

O workflow manual **Testar Room e OCR em Android** também executa os testes instrumentados em emulador quando disparado em Actions.

O workflow também verifica a assinatura do APK e executa testes em emulador Android. O status da primeira compilação será registrado após sua execução. Apenas código e dados fictícios de teste são versionados; nenhum banco de alunos ou chave de assinatura é incluído.

### Android Studio / terminal

Pré-requisitos: JDK 17 completo (recomendado), Android SDK Platform 35, Build Tools 35.0.0, acesso inicial a `services.gradle.org`, `downloads.gradle.org`, Google Maven e Maven Central. A compilação baixa dependências; **o aplicativo instalado funciona offline**, incluindo o OCR.

1. Abra a pasta `diario-de-classe` no Android Studio e instale os componentes solicitados no SDK Manager.
2. Configure `ANDROID_HOME` para a pasta do SDK ou crie `local.properties` com `sdk.dir=/caminho/do/Android/Sdk`.
3. No terminal da pasta do projeto:

```sh
chmod +x gradlew scripts/test-domain.sh
./scripts/test-domain.sh
./gradlew testDebugUnitTest assembleDebug
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

No Windows, use `gradlew.bat testDebugUnitTest assembleDebug`.

O projeto inclui o **Gradle Wrapper oficial**, fixado no Gradle 8.9. O JAR e os scripts foram obtidos de `gradle/gradle` na tag `v8.9.0`; o workflow valida o wrapper por meio de `gradle/actions/setup-gradle`.

Caminho do APK **quando a compilação for bem-sucedida**:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalação por USB:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Também é possível copiar o APK ao celular, abri-lo e autorizar a instalação pelo gerenciador de arquivos. É um APK de depuração, não uma versão assinada para Play Store. Não desinstale o aplicativo para atualizar: a desinstalação apaga seus dados privados. Uma atualização por `adb install -r` precisa manter o mesmo certificado de depuração.

## Usar o aplicativo

1. **Criar turma**: preencha nome, disciplina, ano, etapa, turno, quantidade de aulas de segunda a domingo e duração. A vigência define quando um novo horário começa.
2. **Bimestres**: ajuste os quatro intervalos ao calendário da rede. Os intervalos iniciais são apenas sugestões trimestrais que cobrem o ano, não feriados oficiais.
3. **Alunos**: cadastro individual, colagem de nomes, CSV UTF-8 ou fotografia. Confira a data real de ingresso. A ordem inicial é alfabética; desmarque essa opção para usar a numeração oficial e os botões Subir/Descer.
4. **Calendário**: navegue por janeiro a dezembro, selecione a data e a aula. Aulas duplas têm dois registros independentes. Marque aulas sem regência como não realizadas; adicione reposições quando necessário.
5. **Fazer chamada**: inicie a aula como realizada. Marque P, F, FJ ou NR. É possível marcar todos presentes ou copiar a primeira chamada realizada do dia. Essas substituições pedem confirmação.
6. **Atividades**: escolha o bimestre, crie uma avaliação e lance as notas. Campo vazio significa pendente; zero é uma nota válida. Valores inválidos são sinalizados e não são salvos.
7. **Boletim**: consulte notas por atividade, total parcial/final e faltas comuns/justificadas. Há ordenação por notas e faltas. Toque no aluno para ver os quatro bimestres e o resumo anual.

Chamadas e notas válidas são salvas automaticamente em Room. Dados não mudam de bimestre ao navegar. Transferências criam nova matrícula vinculada ao mesmo identificador de pessoa e preservam a anterior.

## Importar alunos por fotografia

A opção está em **Criar turma** e em **Turma → Alunos**. No formulário de criação, a turma precisa ter dados válidos e é salva antes de abrir o OCR. Cancelar a importação mantém essa turma, sem cadastrar nomes não confirmados.

1. Toque em **Fotografar** (aplicativo de câmera do aparelho) ou **Galeria** (seletor Android).
2. Selecione apenas a região dos números e nomes: arraste um retângulo sobre a foto ou use os controles de bordas. Corrija a orientação com **Girar 90°** se necessário.
3. Toque em **Reconhecer nomes deste recorte**. A biblioteca `com.google.mlkit:text-recognition:16.0.1` contém o modelo latino no aplicativo. Não é a variante de modelo baixado por Google Play Services.
4. Confira cada linha, corrija o nome e o número e marque **Conferi o nome e o número**. Todas as linhas reconhecidas começam destacadas: o ML Kit não oferece uma probabilidade confiável por nome e o aplicativo não inventa porcentagens de confiança.
5. Exclua textos indevidos e adicione nomes que faltarem. Nomes iguais após normalização de acentos/espaços e nomes longos com diferença de uma letra são sinalizados como possíveis duplicidades. Confirme explicitamente quando forem pessoas diferentes. Números repetidos bloqueiam a importação.
6. Fotografe/selecione a próxima página. Ela é acrescentada ao mesmo rascunho, mantendo a ordem de páginas e linhas. Números ausentes serão preenchidos após o maior número já informado.
7. Ajuste a data de ingresso e toque em **Confirmar e cadastrar**. Uma confirmação final apresenta a turma e a quantidade. A gravação de todos os alunos e a remoção do rascunho são uma única transação: qualquer erro desfaz a importação inteira.

Os alunos confirmados entram imediatamente no cadastro usado por chamadas, avaliações e boletins. O rascunho fica salvo em tabela própria mesmo ao fechar o aplicativo. Fotografias são temporárias no cache privado, removidas ao concluir/descartar; arquivos abandonados por mais de 24 horas são limpos na próxima abertura. Não há gravação da foto na lista definitiva, envio a servidores ou registro de nomes/fotos em logs.

As duas imagens PNG e o CSV em `test-fixtures/` contêm somente nomes fictícios. A segunda página repete intencionalmente um aluno para testar duplicidades. Copie-as ao celular para experimentar o fluxo; elas não são carregadas automaticamente no banco.

**Limites de OCR:** leitura impressa em alfabeto latino; imagens de até 30 MB, decodificadas com resolução limitada para controlar memória. Cabeçalhos e colunas são filtrados por geometria e heurísticas. Letra manuscrita, nomes quebrados em várias linhas, células desalinhadas e fotos borradas podem exigir correção. Em listas com várias colunas independentes de nomes, recorte uma coluna por vez. A conferência humana é obrigatória justamente porque a extração não garante classificação perfeita.

## Arquitetura e consistência

- `data/Database.kt`: entidades, chaves estrangeiras, DAO e Room versão 1. Sem migração destrutiva automática.
- `data/DiaryRepository.kt`: transações, geração de aulas, alterações de horário, matrículas, importação e validações.
- `domain/SchoolRules.java`: cálculo de notas, recuperação (somente regra pura nesta fase), frequência e calendário.
- `domain/RosterParser.java`: agrupamento geométrico, extração e comparação de nomes.
- `ui/`: telas Compose e ViewModel com fila de gravações.
- `ocr/`: câmera/galeria, recorte e execução do ML Kit.
- `app/src/test/`: regras puras com dados sintéticos.
- `app/src/androidTest/`: testes de persistência, transações, integração e OCR real com imagem sintética.

A frequência considera apenas aulas realizadas, dentro da matrícula e com P/F/FJ. NR não entra no denominador. FJ é contado separadamente; a configuração da turma decide se conta como ausência administrativa. Notas parciais indicam pendências ou máximos insuficientes para a escala. A soma é limitada pela escala, sem normalização implícita. Mudanças de horários preservam aulas realizadas, canceladas, com observações ou com chamadas.

## Escopo pendente

Esta entrega prioriza o código da Fase 1 e a nova importação por fotografia. Ainda faltam a validação Android/APK e os seguintes complementos:

- Recuperação integrada à interface e ao boletim (as quatro regras puras já têm testes).
- Exportação PDF/CSV/XLSX de relatórios e fechamento anual explícito. Backup/restauração já foram implementados na Etapa 1.
- Duplicação de atividades entre turmas, eventos de calendário sem aula e marcação de intervalos de feriados/recessos em lote.
- Testes de câmera/galeria em aparelhos reais, acessibilidade, tablets, desempenho e publicação com assinatura de produção.

O calendário permite cancelamento por aula com motivo e reposições, mas ainda não cadastra feriados oficiais automaticamente. Relatórios nesta fase são consultas na tela. Antes de atualizar uma instalação com dados, confira o certificado e preserve uma cópia validada. Veja as limitações reais de assinatura em [ASSINATURA](docs/ASSINATURA.md).
