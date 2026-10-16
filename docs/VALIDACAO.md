# Validação — 16/10/2026

## Executado

- Compilação das três classes Java de domínio pelo módulo `jdk.compiler` do Java 21, com `-source 17 -target 17`. O ambiente não possui `javac` no PATH nem `ct.sym` para `--release 17`; foi usado o compilador modular disponível.
- `./scripts/test-domain.sh`: **33 cenários aprovados**. Resultado em `domain-tests.log`.
- `GRADLE_USER_HOME=/workspace/diario-de-classe/.tools/gradle ./gradlew assembleDebug`: **falhou no download inicial**, saída 7. Não chegou a compilar Kotlin, gerar código Room ou empacotar Android.
- Conferência do caminho `app/build/outputs/apk/debug/app-debug.apk`: **arquivo inexistente**.
- Inspeção de código, manifestos XML e referências do projeto. Nenhuma permissão de Internet declarada para uso; o manifesto remove permissões de rede transitivas.

O erro real é `curl: (7) Failed to connect to proxy port 8080`. A política do ambiente aponta para `http://proxy:8080`; o serviço não aceita conexão a partir do executor. Gradle 8.9, Android SDK Platform 35, Build Tools 35.0.0 e dependências Google/Maven não estão instalados nem disponíveis em cache. Não houve erro de compilação Android analisável, pois a compilação não começou. Não houve APK gerado.

## Testes escritos, ainda não executados

`RepositoryInstrumentedTest`:

- Rascunho não matricula alunos sem revisão e sobrevive ao fechamento/reabertura do banco.
- Várias páginas são confirmadas em uma transação; duplicidade causa rollback completo.
- Aluno importado participa de chamadas simples/duplas e de avaliações.
- Alternância de bimestre preserva notas.
- Cancelamento remove faltas dos totais, preservando o registro.
- Alteração de horário mantém aulas realizadas.
- Numeração duplicada bloqueia toda a importação.
- Copiar uma chamada sem marcação para um aluno substitui a marcação anterior da aula de destino por NR.
- Matrícula tardia não recebe faltas anteriores.

`OfflineOcrInstrumentedTest`:

- Gera imagem de uma lista fictícia em memória, roda o ML Kit integrado e verifica nomes/números/ordem e exclusão do cabeçalho.
- Verifica que o pacote do app não tem a permissão de Internet.

Execução em Android 8+ conectado por ADB ou emulador:

```sh
./gradlew testDebugUnitTest connectedDebugAndroidTest
```

## Roteiro de aceitação manual pendente

1. Instale o APK. Ative modo avião **antes da primeira abertura** para comprovar disponibilidade offline do modelo.
2. Crie turma fictícia, quarta com duas aulas e quinta com uma; ajuste bimestres.
3. Use as imagens `test-fixtures/lista-ficticia-pagina-1.png` e `lista-ficticia-pagina-2.png` como listas sintéticas. Na criação, abra Importar alunos por fotografia. Cancele a câmera e verifique retorno sem matrícula.
4. Fotografe lista sintética com cabeçalho, três nomes e colunas de frequência. Recorte para números/nomes, gire se preciso e reconheça.
5. Confira destaque de todas as linhas. Corrija uma leitura, exclua uma linha e adicione outra manualmente. Confirmação deve ficar bloqueada enquanto houver linhas não conferidas.
6. Selecione segunda página pela galeria, incluindo um nome repetido. Confira aviso; remova repetição. Verifique também número repetido e homônimo deliberado.
7. Saia e reabra a tela/app. Rascunho deve continuar presente, sem alunos definitivos. Confira e confirme; os alunos devem aparecer na turma.
8. Abra aula dupla: P na primeira e F na segunda deve resultar em uma falta. Copie uma chamada e edite a segunda. Cancele a aula com falta e confira atualização.
9. Registre ingresso posterior a uma aula: aluno não pode ser marcado naquela chamada.
10. Cadastre atividade, digite 0 para um aluno e deixe outro pendente. Mude de bimestre, volte e reinicie o aplicativo: valores devem permanecer.
11. Troque horário com data de vigência e confira chamadas anteriores.
12. Teste tema claro/escuro, fonte ampliada, paisagem e tablet; verifique legibilidade do recorte e rolagem horizontal do boletim.

Esse roteiro não foi executado no ambiente de origem. Não houve teste visual ou de câmera real; não há alegação de fluxo Android validado.
