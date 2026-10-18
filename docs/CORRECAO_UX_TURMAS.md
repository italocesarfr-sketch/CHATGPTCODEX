# Edição, arquivamento e exclusão de turmas — Noctua 0.11.4 / 20

Base inspecionada: `09d8e10`, Noctua 0.11.3 / 19. Git limpo e marca oficial/limpeza de Relatórios preservadas.

## Fluxo

Cadastrar Turma apresenta **+ Nova Turma**, **Editar Turma** e **Turmas Arquivadas**, mantendo Gerenciar alunos. A página administrativa redundante foi eliminada. O formulário original conserva todos os campos, configurações adicionais e salvamento, com **Arquivar** e **Excluir** ao final da área rolável.

Arquivar pede uma confirmação simples e reutiliza `archiveClass`. Turmas Arquivadas abre o mesmo formulário, permitindo reativar e acessar relatórios, registros de aula e exportação sem reativação automática. Salvar a edição de uma turma arquivada não a seleciona como ativa.

Excluir abre diretamente **Excluir turma?**, identifica o nome da turma persistida e informa a irreversibilidade. **Não** cancela sem escrita; **Sim, excluir** usa a cor de erro e autoriza a transação. Não há frase digitada, contagens ou etapas adicionais. O nome e ID usados pertencem à turma que abriu o formulário; não são obtidos novamente do seletor global ao confirmar. Edições dos campos continuam sendo gravadas exclusivamente por **Salvar turma**; arquivar/excluir não salva os rascunhos dos campos.

Durante a conferência/gravação, os botões de confirmação e fechamento ficam indisponíveis para impedir duplicação ou fechamento prematuro. Falhas exibem mensagem curta, mantêm o diálogo e não apresentam sucesso. Fechar e tentar novamente obtém uma nova prévia. Após sucesso, volta a Cadastrar Turma, resolve e memoriza uma seleção ativa válida a partir do banco confirmado e mostra uma confirmação discreta.

## Integridade intocada

Nenhum arquivo de persistência, regras, relatórios, exportação, backup ou assinatura foi alterado. Room **v7**, as 14 tabelas e backup **8** permanecem idênticos; nenhuma migração nova. Pacote `br.com.diariodeclasse`, banco `diario.db` e marca oficial preservados.

O diálogo obtém internamente o mesmo `ClassDeletionPreview`. Somente após **Sim, excluir**, passa a autorização à API existente `deleteClass(plan, plan.confirmation)`. O Repository continua conferindo o fingerprint, vínculos e estado atual dentro da mesma transação, preservando históricos por IDs, origem, notas, recomposições e frequência. Não há DELETE direto na interface nem alteração de cascatas. Prévia obsoleta, falha de preservação ou de exclusão continua provocando rollback integral. A arquitetura detalhada permanece em [CORRECAO_EXCLUSAO_REMANEJAMENTOS.md](CORRECAO_EXCLUSAO_REMANEJAMENTOS.md); sua descrição de confirmação digitada documenta a interface anterior e é substituída somente neste aspecto.

## Validação

Testes Compose atualizados para o fluxo unificado: ausência de Gerenciar Turma, campos de edição, ações inferiores, nome correto no diálogo, ausência de digitação, cancelamento sem escrita, exclusão confirmada, seleção persistida, arquivamento/cancelamento/consulta/reativação e recriação da Activity. Incluído erro por trigger fictício com rollback, ausência de mensagem de sucesso e tentativa posterior bem-sucedida. Mantida a regressão Room de origens/intermediárias sucessivas, recomposições, relatórios/exportações, falhas transacionais, backup/restauração e reabertura.

CI atualiza a referência assinada para a release real 0.11.3, fixada por hash e certificado público. Mantém as sondas de atualização com a chave permanente já configurada, sem criar chaves ou modificar Secrets. Os resultados do CI e as capturas estão registrados abaixo; compilação local não equivale a teste em aparelho físico.

## Investigação e correção dos testes interrompidos

- O primeiro teste de arquivamento comparava também a preferência de turma selecionada com a origem agora arquivada. A asserção passou a exigir o snapshot **inteiro**, alterando no esperado somente `selection = AppSelection(2026, h.id)` e `classArchives = [ClassArchive(c.id)]`. Depois de reativar, exige os dados iniciais e mantém a seleção ativa em H. Nenhuma tabela escolar é ignorada nessa comparação.
- O timeout não ocorria ao remanejar ou salvar: o boletim já mostrava corretamente original **0,00** e vigente **5,00**. As hipóteses iniciais de nó mesclado/posição não resolveram. A captura de diagnóstico comprovou **“Turma arquivada.” de um caso anterior sobre o nome no momento do toque**. A condição “Turma excluída. não existe” era verdadeira antes de a confirmação atual chegar à tela. Os cenários agora aguardam as gravações, isolam a fila de notificações entre fixtures e observam primeiro a confirmação atual aparecer, depois desaparecer. O toque real no nome, a navegação, a origem histórica e a recriação da Activity continuam obrigatórios; não foram substituídos por chamada direta ao callback.
- Um ensaio apresentou NPE interna de `AccessibilityInteractionClient` durante a mudança de janela do launcher API 30. A espera reconsulta uma raiz nova somente para essa exceção transitória do framework, limitada aos mesmos 12 segundos. Outros erros e a ausência final do ícone continuam falhando. Nenhuma arte, recurso de marca ou comportamento de produção foi alterado para contornar o teste.

Os três ensaios incompletos permanecem consultáveis: [primeiro](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39161257020), [segundo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39162400156) e [diagnóstico com captura](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39168873735). Eles não publicaram um release validado.

## Capturas reais do emulador

Somente fixtures fictícias. Formulário rolável em tela de 320 × 640; sem alteração da identidade noturna ou da coruja oficial.

- [Editar Turma — campos](capturas/ux-turmas/editar-turma.png).
- [Editar Turma — Salvar, Arquivar e Excluir](capturas/ux-turmas/editar-acoes.png).
- [Pop-up simples de exclusão](capturas/ux-turmas/excluir-turma.png).
- [Boletim na turma de destino após excluir a origem](capturas/ux-turmas/historico-no-destino.png).
- [Perfil e histórico consolidados após o toque real](capturas/ux-turmas/perfil-historico.png).

## Resultado final aprovado

**Noctua 0.11.4 / versionCode 20**, código validado [`9f7f151`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/9f7f1519267454d84010b01b1c9adb7d0caafec7). [GitHub Actions 39169682897](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39169682897): ambos os jobs aprovados. A publicação posterior de documentação/capturas não altera o código testado.

- **102 testes JVM e 172 Android aprovados, zero falhas.** Os quatro casos condicionais não executados na suíte normal são sondas dependentes da fase de instalação/processo; as sondas legadas e de frequência foram aprovadas separadamente em duas + duas fases, e a atualização release em seis fases. O ensaio histórico `ReleaseContinuity` permanece substituído pela atualização da release real com a chave permanente.
- Edição normal com configurações ocultas precisas; ausência de Gerenciar Turma; ações no formulário; nome correto no pop-up; cancelamento integral; exclusão sem digitação; arquivamento/reativação; seleção ativa persistida; falha por trigger sem sucesso falso e rollback completo; tentativa posterior; consulta real de boletim/perfil no destino e recriação da Activity: aprovados.
- Regressão Room com origens/intermediárias sucessivas, históricos sem duplicação, notas zero/pendentes, recomposição, frequência P/F/FJ, referências, exclusão/restauração com falha, backup e reinício: aprovada. **Room v7 e backup 8 sem alteração**; arquivos de persistência, regras, relatórios, exportação, tema, recursos da marca, esquemas e workflow release manual conferidos idênticos à base `09d8e10`.
- **39 XLSX, 39 CSV e 117 TSV** lidos independentemente: 30 conjuntos anteriores, seis pós-exclusão e três gerados pelo Android. Valores, células vazias/zero, Unicode, ordem e dimensões conferidos.
- **Atualização real 0.11.3 (19) → 0.11.4 (20)** com `adb install -r`, sem desinstalar, usando a assinatura permanente existente: semente anterior, conferência posterior, backup/restauração, encerramento/reabertura do processo, exclusão de origens sucessivas e nova reabertura aprovados. Mesmos IDs, preferências e registros fictícios; nenhum certificado, segredo ou chave privada foi criado ou modificado.

[Baixar APK release assinado — ZIP](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39169682897/artifacts/12152067256). Extrair `app-release.apk`. O nome histórico do artefato é `noctua-etapa6-release`, mas a versão interna verificada é **0.11.4 / 20**. [Resultados e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39169682897/artifacts/12152640534) · [Provas da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39169682897/artifacts/12152291647) · [Metadados verificáveis](CORRECAO_UX_TURMAS_RESULTADOS.json).

APK baixado, existente e conferido em `app/build/outputs/apk/release/app-release.apk`, **8,452,686 bytes**. SHA-256: `ee977e5ae1f5ebe970f354f94eb8aeb75c1fa17e234060ff2d776ea2ff8d7eb1`. `apksigner verify --verbose` aprovado, assinatura v2, um signatário. Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`, idêntico ao APK original 0.11.3. Pacote `br.com.diariodeclasse`, nome Noctua, API mínima 26 e operação sem permissão INTERNET mantidos. Os hashes dos ZIPs e o APK efetivamente usado nas sondas também foram comparados.

**Limitações:** validação automática em emulador Android API 30, com dados exclusivamente fictícios; não substitui um novo teste físico do proprietário. A atualização da release foi comprovada nesse ambiente controlado, sem promessa sobre instalações com outro certificado. Esta correção não acrescenta funcionalidades escolares ou muda o banco. Para atualizar a instalação com a mesma chave, use o APK release indicado, preservando a instalação existente.
