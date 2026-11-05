# MTH File Manager

Aplicativo Android de gerenciamento de arquivos, desenvolvido em Kotlin com Jetpack Compose e Material 3.

## Tecnologias
- Kotlin 2.0.21
- Jetpack Compose + Material 3
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- AndroidX DocumentFile / Storage Access Framework

## Requisitos
- Android Studio atualizado
- JDK 17
- Android SDK 35

## Abrir o projeto
1. Clone este repositório.
2. Abra a pasta raiz no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Execute o módulo `app` em um dispositivo ou emulador.

Se o Android Studio solicitar um Gradle Wrapper, gere-o na pasta raiz com `gradle wrapper --gradle-version 8.9` (com Gradle instalado) e sincronize novamente.

## Funcionalidades já implementadas
- Tela inicial Material 3.
- Tema claro/escuro seguindo a configuração do sistema.
- Seleção de uma pasta de armazenamento pelo seletor oficial do Android.
- Persistência da autorização de acesso concedida pelo usuário.
- Navegação por pastas e subpastas dentro da pasta autorizada.
- Listagem de arquivos e pastas, com pastas primeiro.
- Abertura de arquivos em aplicativos compatíveis.
- Criação de pastas.
- Renomeação e exclusão de itens com confirmação para exclusão.
- Suporte ao cartão SD quando o Android disponibilizar a unidade no seletor.

## Limitações e próximas etapas
O Android restringe o acesso a determinadas áreas, como diretórios privados de outros aplicativos e partes de `Android/data` e `Android/obb`. O aplicativo só acessa a árvore de pastas que o usuário autorizar.

Ainda serão implementados: copiar, mover, pesquisa, favoritos, recentes, categorias com conteúdo real, lixeira, compactação ZIP, análise de armazenamento e visualizadores internos.

## Identidade
- Nome comercial: MTH File Manager
- Pacote: `com.mth.filemanager`
- Repositório: [jefmth/MthExplore](https://github.com/jefmth/MthExplore)
