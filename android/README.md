# PokeGrid Android

Versão nativa Android (Kotlin) do PokeGrid: quatro contas de Poke Idle World rodando ao mesmo
tempo, cada uma com sessão de login isolada, num único app. É a contraparte mobile do app
Electron na raiz deste repositório — mesma proposta, tecnologia diferente.

## Como a isolação de sessão funciona (a peça central)

No Electron, cada painel é um `<webview partition="persist:contaN">`, e o Chromium mantém um
cookie jar separado por partition mesmo carregando o mesmo domínio. Não existe equivalente disso
em WebView padrão... até a **WebView Multi-Profile API**
(`androidx.webkit.Profile`/`ProfileStore`, exposta via `WebViewCompat.setProfile(webView, nome)`):
cada `GamePanel` chama `setProfile(webView, "contaN")` como a primeiríssima coisa que faz com a
WebView recém-criada, antes de qualquer `loadUrl`/`evaluateJavascript`/settings. Isso garante que
os 4 painéis, mesmo carregando `poke.idleworld.online` nos quatro, tenham cookies/localStorage
totalmente separados — o mesmo resultado das partitions do Electron.

**Requisito:** essa API só existe em WebView (Android System WebView / Chrome) relativamente
recente. O app checa `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` na
inicialização e avisa o usuário se não tiver suporte — nesse caso os 4 painéis cairiam no perfil
padrão compartilhado (perderia o propósito do app), então a recomendação é atualizar o "Android
System WebView" pela Play Store.

## Estrutura

```
android/
  app/src/main/java/online/idleworld/pokegrid/
    MainActivity.kt          # grid 2x2, toolbar, expandir painel, diálogo de contas
    PokeGridApp.kt           # Application: cria o canal de notificação
    model/Account.kt
    data/CredentialStore.kt  # AES-256-GCM via AndroidKeyStore (equivalente ao safeStorage)
    data/ErrorLog.kt         # log rotativo + compartilhar via FileProvider
    notif/Notifier.kt        # notificações de queda/pokébola/poção/revive baixos/shiny
    web/GamePanel.kt         # WebView isolada por perfil, domain lock, watchdog, alertas
    web/InjectedScripts.kt   # carrega/templeteia os scripts de assets/scripts
  app/src/main/assets/
    scripts/                 # JS portado quase 1:1 do index.html do Electron
    presets/justpokedex.js   # userscript opcional (calculadora de IV), pronto pra plugar
```

Os scripts em `assets/scripts/` (login, eco, chat, dock, sellguard, popup killer, coletor de
estado/alertas) são o mesmo JavaScript puro que já rodava no `index.html` via
`webview.executeJavaScript(...)` — só trocaram de host (Kotlin `evaluateJavascript` em vez de
Electron). Isso mantém o comportamento no jogo idêntico ao da versão desktop nesses pontos.

## Buildar

Precisa de JDK 17+ e Android SDK (compileSdk/targetSdk 36). Sem Android Studio, só terminal:

```bash
cd android
# aponte pro seu SDK (ou crie local.properties com sdk.dir=/caminho/do/sdk)
export ANDROID_HOME=/caminho/do/android-sdk
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

## O que já tem (paridade com o Electron)

- 4 painéis com sessão de login isolada e login automático.
- Trava de domínio: navegação fora de `poke.idleworld.online` abre no navegador do celular.
- Modo Eco (throttle do `requestAnimationFrame`).
- Esconder chat / esconder menu do jogo.
- Proteção de venda (confirmação nativa antes de vender shiny/raro).
- Fecha popup de promoção sozinho.
- Notificações: conta caiu, pokébolas/poções/revives baixos, shiny.
- Credenciais cifradas com chave do AndroidKeyStore (nunca saem do aparelho).
- Watchdog de crash/travamento do painel (recria a WebView mantendo a sessão do perfil).
- "Expandir painel" por toque (equivalente ao Ctrl+1..4 do desktop, adaptado pra tela pequena).
- Relatório de erros compartilhável.

## O que ainda não foi portado (deixado de fora por escopo, não por limitação técnica)

- Modo Simples/dashboard com estatísticas (ouro/h, XP/h etc.) — o coletor de estado
  (`state_collector.js`) foi simplificado pra só alimentar os alertas de recurso baixo.
- Filtros de Pokédex/Mercado, som de shiny, webhook do Discord, tela de shiny capturado.
- Idioma (só português por enquanto; a estrutura de `strings.xml` já suporta `values-en/`).
- Mute de áudio por painel (WebView não tem API pública equivalente ao `setAudioMuted` do
  Electron; daria pra simular via script mutando `<audio>/<video>`, não implementado ainda).
- Execução em segundo plano: como qualquer app Android normal, farmar para de fato rodar quando
  o app vai pra background por tempo suficiente (o sistema pausa a Activity). O Electron não tem
  essa restrição por ser desktop. Contornar isso exigiria um foreground `Service` dedicado —
  não implementado nesta primeira versão.

## Segurança

Mesma postura do app desktop: e-mail/senha só trafegam para a tela de login oficial do jogo,
ficam cifrados no aparelho (aqui, `AndroidKeyStore` em vez de `safeStorage`/DPAPI), câmera/
microfone/localização são sempre negados, e o app não resolve captcha nem automatiza jogatina —
só organiza as sessões que você já tem.
