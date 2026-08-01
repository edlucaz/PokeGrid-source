package online.idleworld.pokegrid.config

/**
 * PokeDream (pokedream.com.br): 2-account variant.
 *
 * The site is a client-routed React SPA (server does a catch-all fallback to index.html for any
 * path), so there's no separate LOGIN_URL to detect a navigation to — the login screen is a
 * client-side state, not a distinct page load. GamePanel handles this by not gating auto-login
 * on a URL match and instead retrying periodically (safe: login.js is a no-op when it can't find
 * a matching form).
 *
 * ENABLE_RESOURCE_ALERTS/DOCK/SELLGUARD are off because those features parse
 * poke.idleworld.online's specific WebSocket message types, REST endpoints and DOM class names
 * (assets/scripts/state_collector.js, dock.js, sellguard.js) — this game's own protocol/markup
 * hasn't been reverse-engineered yet, so leaving them on would either no-op silently or, for the
 * resource thresholds, fire bogus "out of pokéballs" alerts forever. Chat-hide stays on since its
 * heuristic (a text field in the lower half of the screen) is layout-based, not protocol-based.
 */
object GameConfig {
    const val GAME_HOST = "pokedream.com.br"
    const val LOGIN_URL = "https://pokedream.com.br/"
    const val START_URL = "https://pokedream.com.br/"
    const val PANEL_COUNT = 2

    const val ENABLE_RESOURCE_ALERTS = false
    const val ENABLE_DOCK_TOGGLE = false
    const val ENABLE_SELLGUARD = false
}
