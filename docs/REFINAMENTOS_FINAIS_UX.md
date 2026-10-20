# Noctua — sete refinamentos finais de interface

Base auditada: `0475ab2`, Noctua 0.13.0 / 23. Incremento: **0.14.0 / 24**. Escopo exclusivamente de apresentação/navegação e entrada demonstrativa, sem autenticação.

## Alterações

1. Relatórios: indicação visual **Buscar**, com lupa e Limpar preservados. Algoritmo de busca global, turmas arquivadas, anos e filtros inalterados.
2. Ações principais: **Organizar** e **Exportar**, lado a lado e com larguras equivalentes, texto centralizado em uma linha.
3. Ambos usam o mesmo `OutlinedButton`, padding, altura mínima de 48 dp, cores e estados. Nenhum preenchimento permanente de seleção.
4. Rodapé: vetor nativo de calendário, rótulo **Chamada e Diário** e acessibilidade atualizada. As quatro páginas e destinos permanecem iguais. Espaço de rótulo reservado igualmente entre abas; até três linhas em fontes ampliadas, sem reduzir fonte. Coruja oficial permanece no launcher e cabeçalho.
5. Organizar exportação: mesmos modos, cartões e preferências. Controles de salvar/cancelar/concluir e exportar agrupados no rodapé acessível; cartões com padding consistente, checkbox centralizado e setas de 48 dp para mover. Linhas vazias, ocultação e confirmação mantidas. Não há novo editor nem alteração na ordenação dos dados.
6. Exportar: prévia antecipada, espaçamento menor e ações de arquivo/cópia reunidas no rodapé. Chamada e relatório anual agrupados em **Outras planilhas**. Opções continuam recolhíveis. Mesma matriz, cabeçalhos, células, formatos, callbacks de geração/gravação e clipboard. Ações têm rolagem própria quando necessário para não bloquear conteúdo com fonte ampliada. Scaffolds internos não duplicam insets do Android.
7. Entrada demonstrativa com logo oficial, Noctua, E-mail, Senha e Mostrar/Ocultar, Entrar, Criar conta e Esqueci minha senha. Identificação explícita: **Prévia de entrada • acesso livre, sem autenticação.** Entrar funciona com campos vazios ou quaisquer valores. Os outros botões mostram somente **Funcionalidade disponível em uma versão futura.** Nenhuma conta é criada.

## Entrada: estado e privacidade

`NoctuaStart` envolve a interface existente. Somente o booleano de navegação usa SavedInstanceState. Os campos usam `remember`, sem Repository, preferências, arquivos, logs ou serviços; são descartados ao entrar ou recriar a tela. Não há permissão de internet no APK, nem dependência de autenticação/autofill adicionada.

Cold launch mostra a prévia. Entrar substitui a tela na composição, sem adicioná-la a uma pilha. Recriar a Activity depois de entrar conserva o trabalho e não volta à prévia. Voltar na raiz encerra a Activity como antes. Retomar uma Activity existente não obriga nova passagem. Essa flag não é sessão autenticada nem proteção de acesso. A seleção persistida de ano/turma continua sendo a já existente.

A página é rolável, considera barras do sistema e teclado, tem teclado E-mail e Password e ações de toque adequadas. O aviso de versão futura dispensa o teclado e usa snackbar; nunca simula recuperação ou criação de conta.

## Integridade e assinatura

Sem mudança em entidades, esquema Room v7 (14 tabelas), migrações, `diario.db`, backup formato 8, IDs, repositórios, cálculos, escritores XLSX/CSV, remanejamento ou exclusão. Pacote `br.com.diariodeclasse`. Chave, Secrets e workflow manual de release preservados.

A referência do teste de atualização passa a ser o APK release 0.13.0 real: run 39421673236, artefato 12263754040, SHA-256 `83a237ad090321f1b14481dcfa3ab6a7e77c1d3fc2a975158db0f1c09a5ed9d9`. A chave existente é usada exclusivamente pelo pipeline já configurado.

## Testes e validação

Regressões antigas atravessam o botão Entrar real via regra JUnit comum, sem bypass no código de produção. Novos testes cobrem entrada livre, aviso futuro, mostrar/ocultar, campos vazios após recriação, dados/preferências idênticos, ausência de permissão de internet, Voltar, nomes/ícone novos, destinos, igualdade visual dos botões, fontes ampliadas, prévia antes da metade da tela e acessibilidade das ações.

Resultados finais, capturas, artefatos e eventual limitação serão registrados após a execução. Não há alegação de teste em aparelhos físicos do proprietário nem no Microsoft Excel instalado. O CI valida os arquivos reais com openpyxl e realiza atualização assinada em emulador com dados exclusivamente fictícios.
