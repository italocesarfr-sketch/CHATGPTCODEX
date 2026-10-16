# Etapa 2 — Navegação e gestão de turmas

Escopo autorizado: incrementos 2.1–2.3 do ROADMAP. A autorização informa que a instalação original tem somente dados de teste descartáveis. A assinatura permanente continua obrigatória antes do uso real, mas não bloqueia esta implementação. A Etapa 3 não está autorizada.

## Implementação

- Quatro páginas independentes na navegação inferior: Cadastrar Turma, Fazer Chamada, Atividades e Notas e Relatórios. Perfis, lista e lançamentos são destinos secundários. Backup continua na barra superior. Paleta verde/neutra, tema pelo sistema, controles espaçados e quatro bimestres visíveis na tela pequena do CI.
- Seletor comum por ano e ID de turma, com disciplina/turno para distinguir nomes iguais. Último ano/turma gravados em SharedPreferences privadas; ID ausente usa uma turma disponível no mesmo ano, ou estado vazio. Nenhuma entidade escolar é criada pela navegação.
- Trocas aguardam a fila das gravações válidas, que conserva os IDs de origem. Uma nota inválida pede confirmação antes de sair, inclusive após recriação da Activity. Formulários modais mantêm a turma de origem.
- Cadastro começa em Nova Turma / Editar Turma Existente, com nome, disciplina, etapa, turno e ano. Horário semanal é opcional, nas configurações adicionais; valores antigos continuam no banco, incluindo a precisão original de escala e limites de nota. Edições sem mudança de horário não regeneram aulas. Ano da turma existente permanece bloqueado para preservar histórico.
- Lista de alunos com edição, ingresso/saída/status/observações, perfil e transferências; visualização alfabética ou número e reordenação por Subir/Descer.
- Colagem e CSV UTF-8 com conferência persistida em `import_drafts`. BOM, delimitadores, aspas, acentos, ordem e números explícitos são tratados. Linhas inválidas/ambíguas produzem erro sem importação parcial. Nome semelhante exige correção/exclusão ou confirmação de homônimo. Número repetido bloqueia o cadastro. Números vazios são atribuídos após o maior utilizado/informado.
- Nenhuma importação cadastra automaticamente. Todas as linhas precisam ser conferidas e a matrícula exige confirmação final; cancelar conserva o rascunho. Rascunhos de fotografia das versões antigas são recuperados na mesma conferência textual, inclusive incompletos.
- Câmera, galeria, recorte, ML Kit, Exif e integração de tarefas Play Services foram removidos. Matrículas já importadas e a tabela de rascunhos permanecem. Imagens sintéticas antigas de teste são apenas referências históricas, sem biblioteca OCR no APK.

## Preservação e limites

`applicationId=br.com.diariodeclasse`, banco `diario.db`, Room v1 e seu JSON/identidade permanecem iguais. Nenhuma migração foi necessária. Nenhuma rotina de inicialização apaga ou troca o banco. Backup, cópia anterior validada, confirmação literal e transação escolar da Etapa 1 continuam ativos.

O backup passa a emitir formato **2**, acrescentando `selection` ao payload para guardar o contexto visual; lê os formatos 1 e 2. Backups anteriores, inclusive resgate ADB, continuam aceitos. O app antigo só lê formato 1 e não deve receber backups novos. Preferências são aplicadas após o commit escolar; se sua gravação falhar, os registros restaurados permanecem e um aviso solicita selecionar turma/ano novamente. SQLite e SharedPreferences não formam uma transação única. Não há perda de notas/chamadas nessa falha de contexto visual.

As quatro páginas reutilizam calendário, chamada P/F/FJ/NR, atividades, cálculos e boletim existentes. A nova tabela P/F e definição simplificada dos horários por data ficam para a Etapa 3. Enquanto isso, numa turma sem horário semanal, use **+ Reposição / aula extraordinária** para criar cada aula na data escolhida. Não existe bloqueio pela data de criação da turma; ingresso real do aluno continua sendo respeitado.

Relatórios permanecem consultas na tela; exportação avançada, recuperação integrada e outras etapas não foram antecipadas. Tema acompanha o sistema. A validação de usabilidade com TalkBack, fonte ampliada, tablets e aparelho físico permanece para refinamento posterior.

## Validação concluída

Versão **0.2.0**, `versionCode=3`, código testado [`dc66b57f3065103cdb1a67ac622ae5a36ae602c0`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/dc66b57f3065103cdb1a67ac622ae5a36ae602c0). [Execução aprovada no Actions](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139).

| Verificação | Resultado |
| --- | --- |
| `testDebugUnitTest` | 18 testes aprovados, incluindo os 28 cenários Java de domínio |
| `assembleDebug` e `assembleDebugAndroidTest` | Compilação aprovada |
| Testes Android, emulador API 30 | 31 aprovados; 1 sonda exclusiva de atualização ignorada na suíte comum |
| Atualização controlada | Sondas antes/depois aprovadas, mesma chave descartável do runner e `adb install -r`, sem desinstalar entre versões |
| Preservação da fixture v1 | Todas as linhas/células das oito tabelas comparadas antes/depois; resgate sintético também conferido |
| Room v1 | JSON e identidade inalterados; nenhuma migração necessária |
| Release sem chave permanente | Bloqueado conforme esperado |
| APK publicado | Mesmo SHA-256 do arquivo usado nos testes; assinatura v2 verificada pelo `apksigner` |

Os testes abrangem as quatro páginas, turma/ano com nomes iguais em anos distintos, recriação da Activity, leitura por nova instância das preferências, troca de turma após nota válida e aviso para nota inválida, criação sem grade, inclusão/edição de aluno, configurações exatas preservadas ao editar nome, reordenação/transferência com histórico, CSV/BOM/aspas/acentos/números, conflitos/homônimos com rollback e conferência da lista até a confirmação final. Cancelar conserva o rascunho sem matricular. Backups antigos continuam legíveis; ida/volta do contexto e rascunho incompleto foi testada, assim como falha ao gravar a preferência após restaurar os registros escolares.

As cinco capturas sintéticas foram coletadas antes da limpeza do emulador, e as quatro páginas foram conferidas visualmente. O seletor de bimestres foi ajustado após detectar corte do quarto botão em tela pequena e o candidato foi novamente testado. Não houve instalação/teste em celular físico nesta entrega. TalkBack, fonte ampliada, tema escuro e outros tamanhos/APIs ainda precisam da validação mais ampla prevista no refinamento.

Localmente, a suíte Java de 28 cenários também passou. O sandbox não conseguiu baixar o Gradle e não tem SDK Android configurado; Java/SDK/Gradle e a compilação Android foram executados no Actions. O APK foi depois baixado e verificado no workspace, sem depender de uma compilação local presumida.

## APK e projeto

- [Baixar o ZIP do APK de teste](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139/artifacts/12072308459): contém `app-debug.apk`, SHA-256 e metadados públicos.
- [Baixar os resultados e capturas sintéticas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38960690139/artifacts/12072841819).
- [Código completo do candidato testado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/tree/dc66b57f3065103cdb1a67ac622ae5a36ae602c0).
- Caminho do APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Cópia verificada neste workspace: `/workspace/diario-de-classe/app/build/outputs/apk/debug/app-debug.apk`.
- Tamanho do APK: **10,259,485 bytes**.
- SHA-256: `c7d8b2cb2dbf98e750a6e311c6d9fe5234bfdf692592e5671a88b0818c147f72`.
- Certificado SHA-256: `cca0319751beabc37716ef6c3cdbd29f1b92af3a83ede33c9050372c3898b80a`.
- [Metadados de validação](baseline/etapa2-validacao.json).

O download foi descompactado, seu ZIP/APK conferido e o hash local coincide com o arquivo do CI. O artefato tem retenção de 30 dias; uma nova execução permite recompilar, mas a chave debug não tem continuidade garantida.

## Assinatura e próxima etapa

O certificado deste APK é diferente dos artefatos de referência **0.1.0** e **0.1.1/Etapa 1** analisados. Portanto, **este debug não faz atualização direta sobre esses artefatos**. Não foi lido o certificado do aplicativo que está no celular do responsável. A atualização controlada no emulador prova preservação do banco quando a assinatura é compatível; não prova compatibilidade com uma instalação física desconhecida.

A autorização informa que os dados antigos são testes descartáveis e admite futura instalação limpa com uma chave permanente. Nenhuma chave privada foi publicada ou criada como substituta temporária de produção. A estratégia segura continua sendo definir e guardar a chave permanente externamente e configurar o workflow de release com certificado fixado antes do uso real; consulte [ASSINATURA](ASSINATURA.md). Nenhuma instalação física foi apagada/modificada por este trabalho.

A Etapa 2 está concluída dentro desse escopo de teste. **Aguardar autorização para a Etapa 3**, sem nova tabela P/F, nova lógica de frequência ou exportação avançada nesta execução.
