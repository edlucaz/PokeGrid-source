# PokeGrid Android

Versão nativa Android (Kotlin) do PokeGrid. Um único projeto Gradle gera **dois apps
independentes** (product flavors), cada um pra um jogo/site diferente, reaproveitando toda a
parte que não muda de um jogo pro outro:

| Flavor | Site | Contas | UI | applicationId |
|---|---|---|---|---|
| `pokegrid` | poke.idleworld.online | 4 | grid 2x2, painel expansível por toque | `online.idleworld.pokegrid` |
| `pokedream` | pokedream.com.br | 2 | 1 painel em tela cheia + card pequeno pra alternar | `br.com.pokedream.grid` |

São apps separados (applicationId diferente) — dá pra instalar os dois no mesmo celular.

## Como a isolação de sessão funciona (a peça central, comum aos dois)

No Electron, cada painel é um `<webview partition="persist:contaN">`, e o Chromium mantém um
cookie jar separado por partition mesmo carregando o mesmo domínio. Não existe equivalente disso
em WebView padrão... até a **WebView Multi-Profile API**
(`androidx.webkit.Profile`/`ProfileStore`, exposta via `WebViewCompat.setProfile(webView, nome)`):
cada `GamePanel` chama `setProfile(webView, "contaN")` como a primeiríssima coisa que faz com a
WebView recém-criada, antes de qualquer `loadUrl`/`evaluateJavascript`/settings. Isso garante que
os painéis, mesmo carregando o mesmo domínio em todos, tenham cookies/localStorage totalmente
separados — o mesmo resultado das partitions do Electron.

**Requisito:** essa API só existe em WebView (Android System WebView / Chrome) relativamente
recente. Cada app checa `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` na
inicialização e avisa o usuário se não tiver suporte — nesse caso os painéis cairiam no perfil
padrão compartilhado (perderia o propósito do app), então a recomendação é atualizar o "Android
System WebView" pela Play Store.

## Estrutura

```
android/app/src/
  main/                       # tudo que os dois flavors compartilham
    java/online/idleworld/pokegrid/
      PokeGridApp.kt          # Application: cria o canal de notificação
      model/Account.kt
      data/CredentialStore.kt # AES-256-GCM via AndroidKeyStore (equivalente ao safeStorage)
      data/ErrorLog.kt        # log rotativo + compartilhar via FileProvider
      notif/Notifier.kt       # notificações de queda/pokébola/poção/revive baixos/shiny
      service/FarmService.kt  # foreground service + wake lock: farm continua com tela apagada
      service/BackgroundModeController.kt  # liga/desliga o FarmService, persiste a preferência
      web/GamePanel.kt        # WebView isolada por perfil, domain lock, watchdog, alertas
      web/InjectedScripts.kt  # carrega/templeteia os scripts de assets/scripts
    assets/scripts/           # JS portado quase 1:1 do index.html do Electron
    res/layout/dialog_accounts.xml, view_account_row.xml  # diálogo de contas (genérico, N linhas)

  pokegrid/                   # só o que é específico do flavor pokegrid
    java/.../config/GameConfig.kt   # host/URLs/nº de painéis/flags de feature
    java/.../MainActivity.kt        # grid 2x2, expandir painel
    res/layout/activity_main.xml, view_panel.xml
    res/menu/main_menu.xml

  pokedream/                  # só o que é específico do flavor pokedream
    java/.../config/GameConfig.kt
    java/.../MainActivity.kt        # painel único em tela cheia + switcher
    res/layout/activity_main.xml, view_switch_chip.xml
    res/menu/main_menu.xml (menor: sem toggle de dock/proteção de venda)
    res/drawable/, res/values/colors.xml, res/mipmap-anydpi-v26/  # ícone e cor roxa própria
```

`GamePanel`, `CredentialStore`, `InjectedScripts` etc. em `src/main` são 100% agnósticos de jogo:
recebem host/URL/nº de contas/flags de `config.GameConfig`, que é a única classe que cada flavor
define do zero (mesmo pacote/nome em `src/pokegrid` e `src/pokedream` — o Gradle escolhe a certa
na hora de compilar cada variante).

## UI do PokeDream: painel cheio + card pequeno

Pensado pra celular: em vez de espremer 2 (ou 4) jogos numa grade minúscula, **um painel ocupa a
tela inteira** e um cardzinho flutuante no rodapé (a "pill" com os 2 chips, cada um com uma
bolinha de status + nome da conta) deixa alternar com um toque. As duas contas continuam rodando
em segundo plano mesmo com a WebView escondida (`View.GONE` não pausa o WebView) — só a que está
visível ocupa a tela.

## Buildar

Precisa de JDK 17+ e Android SDK (compileSdk/targetSdk 36). Sem Android Studio, só terminal:

```bash
cd android
export ANDROID_HOME=/caminho/do/android-sdk   # ou local.properties com sdk.dir=...

./gradlew assemblePokegridDebug
# app/build/outputs/apk/pokegrid/debug/app-pokegrid-debug.apk

./gradlew assemblePokedreamDebug
# app/build/outputs/apk/pokedream/debug/app-pokedream-debug.apk

./gradlew assembleDebug   # builda os dois flavors de uma vez
```

## O que já tem (comum aos dois flavors)

- Sessão de login isolada por conta + login automático.
- Trava de domínio: navegação fora do site do jogo abre no navegador do celular.
- Modo Eco (throttle do `requestAnimationFrame`).
- Esconder chat.
- Notificação quando a conta cai da sessão.
- Credenciais cifradas com chave do AndroidKeyStore (nunca saem do aparelho).
- Watchdog de crash/travamento do painel (recria a WebView mantendo a sessão do perfil).
- Relatório de erros compartilhável.
- **Continua rodando com a tela apagada ou trocando de app** (menu "Segundo plano: ligado").

## PokeGrid (4 contas) tem, além disso

- Grid 2x2 com "expandir painel" por toque.
- Esconder menu do jogo (dock), proteção de venda (confirmação antes de vender shiny/raro),
  notificação de pokébola/poção/revive baixos e de shiny — calibrados contra o WebSocket/API real
  de poke.idleworld.online (`assets/scripts/state_collector.js`, `dock.js`, `sellguard.js`).

## PokeDream (2 contas): o que está desligado, e por quê

`GameConfig` do flavor pokedream desliga `ENABLE_RESOURCE_ALERTS`, `ENABLE_DOCK_TOGGLE` e
`ENABLE_SELLGUARD`. Motivo: essas três features leem o protocolo específico do
poke.idleworld.online (tipos de mensagem do WebSocket, endpoints REST, classe CSS `.game-dock`) —
não é algo genérico, é reverse-engineering do jogo. Eu não consegui abrir pokedream.com.br com um
navegador de verdade neste ambiente (sandbox sem saída de rede pra automação de browser; só
`curl`/fetch direto funcionaram, e o site é um SPA React que só renderiza o login via JS) pra
extrair o protocolo real dele. Então, em vez de portar um coletor que ia ficar mentindo (ex.:
alerta de "sem pokébola" o tempo todo porque o contador nunca é preenchido), preferi desligar e
deixar documentado — é trabalho normal de sequência, não uma limitação da arquitetura.

O que **continua ligado** por ser genérico/de baixo risco mesmo sem confirmação:
- Login automático: usa heurística por `autocomplete="username"`/`"current-password"` + botão de
  submit, um padrão comum, não algo específico do outro jogo. Sem gatilho de URL de login (o site
  é SPA de rota única), reforçado por um retry periódico a cada 20s.
- Esconder chat: heurística por texto do placeholder/posição na tela, não depende de nenhuma
  classe CSS específica.
- Killer de popup de promo e watchdog de WebGL: procuram por seletores/eventos padrão; se não
  encontrarem nada, não fazem nada (no-op seguro).

**Bom testar no aparelho de verdade** se o login automático realmente casa com o formulário do
PokeDream — se não bater, é ajustar os seletores em `assets/scripts/login.js`, sem mexer no resto.

## O que ainda não foi portado (deixado de fora por escopo, não por limitação técnica)

- Modo Simples/dashboard com estatísticas (ouro/h, XP/h etc.).
- Filtros de Pokédex/Mercado, som de shiny, webhook do Discord, tela de shiny capturado.
- Idioma (só português por enquanto; a estrutura de `strings.xml` já suporta `values-en/`).
- Mute de áudio por painel (WebView não tem API pública equivalente ao `setAudioMuted` do
  Electron; daria pra simular via script mutando `<audio>/<video>`, não implementado ainda).

## Segundo plano: como funciona e onde para

Ligado por padrão (menu "Segundo plano"). Ao ativar:

1. Sobe o `FarmService`, um foreground service (com notificação fixa, obrigatória pelo Android
   pra esse tipo de serviço) que segura um `PARTIAL_WAKE_LOCK` — mantém a CPU acordada mesmo com
   a tela apagada, equivalente ao `powerSaveBlocker('prevent-app-suspension')` do Electron.
2. Na primeira vez, pede pra tirar o app da otimização de bateria do Android (`ACTION_REQUEST_
   IGNORE_BATTERY_OPTIMIZATIONS`) — sem isso, o Doze do sistema pode suspender rede em segundo
   plano de qualquer jeito, foreground service ou não.

Isso cobre os dois casos pedidos: **apertar Home / abrir outro app** (a `Activity` só pausa,
nunca é destruída — as WebViews continuam rodando) e **apagar a tela** (o wake lock impede o
processador de dormir).

**O que não cobre:** arrastar o PokeGrid pra fora da lista de recentes (aquele gesto de "fechar"
o app). Isso destrói a `Activity` de verdade — as WebViews morrem junto, e o `FarmService` se
desliga sozinho (`onTaskRemoved`) em vez de deixar uma notificação de "rodando" pra algo que não
está mais rodando. Pra manter as sessões vivas mesmo nesse caso seria preciso outra arquitetura
(WebViews *headless* vivendo só no Service, sem `Activity` dona) — não implementado agora porque
esbarra em como o Android mostra diálogo de JS (`window.confirm` da proteção de venda, por
exemplo, precisa de uma `Activity` de verdade pra aparecer). Ou seja: **não feche o app pelos
recentes** se quiser manter o farm rodando — só minimizar ou apagar a tela.

## Segurança

Mesma postura do app desktop: e-mail/senha só trafegam para a tela de login oficial do jogo,
ficam cifrados no aparelho (`AndroidKeyStore` em vez de `safeStorage`/DPAPI), câmera/microfone/
localização são sempre negados, e o app não resolve captcha nem automatiza jogatina — só organiza
as sessões que você já tem.
