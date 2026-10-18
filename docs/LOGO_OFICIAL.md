# Marca oficial Noctua

Base: Noctua 0.11.2 (18), commit `1c018f7`. Incremento restrito à marca: **0.11.3 / código 19**. Funcionalidades, tema, navegação, banco e assinatura não são reformulados.

## Arte do proprietário

O proprietário aprovou como identidade oficial a coruja geométrica formada pela estrutura da letra **N**. O RAR enviado contém somente `Ícone de Coruja N Geométrica.png`, RGB, 1254 × 1254 pixels. Nenhum documento do anexo foi tratado como instrução.

- Original preservado em [brand/noctua-original.png](brand/noctua-original.png).
- O recurso `app/src/main/res/drawable-nodpi/noctua_logo.png` é **idêntico byte a byte ao PNG recebido**, sem recorte, redesenho, recoloração, vetorização, nova compressão ou geração de imagem por IA.
- SHA-256 original: `ef7129c4a637fed0361645f3356c2597de2703cd524178ff7a9ba44b0ff84baa`.
- Metadados e checksum dos pixels RGB em [brand/noctua-oficial.json](brand/noctua-oficial.json).

O redimensionamento é feito nativamente pelo Android ao desenhar a imagem, preservando sua proporção. Não foi necessário gerar cópias por densidade: recursos `anydpi`/`nodpi` permitem reutilizar a mesma arte sem perdas intermediárias. O PNG original permanece no projeto como fonte de referência. A proposta vetorial anterior é apenas um registro histórico, sem aplicação no app.

## Recursos Android

| Recurso | Uso |
| --- | --- |
| `drawable-nodpi/noctua_logo.png` | Arte oficial integral, cabeçalho e ícone da área Chamada/Diário |
| `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | Fallback padrão usando a própria imagem |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Ícones adaptativos, padrão e redondo, Android 8.0+ |
| `drawable/noctua_launcher_foreground.xml` | Imagem proporcional com margens de segurança, sem modificar os pixels do arquivo |
| `values/noctua_brand.xml` | Fundo branco do ícone, correspondente à moldura branca já existente na arte |

O manifesto usa `@mipmap/ic_launcher` e `@mipmap/ic_launcher_round`. A imagem ocupa 66 dp da camada de 108 dp, com margem de 19,444444% por lado. A coruja/N cabe no círculo seguro central; a máscara escolhida pelo launcher pode recortar somente o enquadramento externo. Não se promete controlar a máscara ou sombras aplicadas por cada fabricante. Não foi criada variante monocromática, pois ela substituiria as cores oficiais.

No cabeçalho compartilhado das quatro páginas e telas secundárias, `Image`/`ContentScale.Fit` exibe a arte em 32 dp, no mesmo espaço do emblema anterior, sem tingimento pelo tema. O ícone da área Chamada/Diário usa a mesma arte em 23 dp, sem filtro de cor. Nome, tipografia, textos, demais ícones e paleta noturna permanecem iguais. Os dois vetores provisórios `drawable/ic_owl.xml` e `drawable/ic_diary.xml` foram removidos após substituir todas as referências.

## Validação

`scripts/validate-noctua-brand.py` confere os arquivos originais, checksums, configuração do manifesto, recursos padrão/adaptativos/redondos e geometria da zona segura, sem editar imagens.

Os testes Android conferem os **pixels RGB do recurso realmente empacotado**, resolução/proporção, ícone carregado pelo PackageManager/LauncherApps e ausência de corte em pontos extremos da coruja/N. Renderizam máscaras em fundos claro/escuro. Um fluxo exclusivo de emulador abre o launcher, posiciona o ícone na tela inicial e abre as configurações do aplicativo, coletando capturas reais. Outro teste consulta as quatro páginas, confere a marca sem distorção/tingimento, verifica azul e dourado e compara o backup antes/depois. A regressão anterior permanece no CI.

O workflow reutiliza a chave permanente existente e testa a atualização real **0.11.2 → 0.11.3**, sem desinstalar. Room **v7**, backup **8**, pacote `br.com.diariodeclasse` e banco `diario.db` permanecem inalterados. Não foi criada nem alterada chave, senha ou Secret.

Resultados finais, capturas e APK: em validação no CI. Sem alegação de novo teste físico nesta versão.
