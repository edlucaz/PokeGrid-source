package online.idleworld.pokegrid.config

/** Poke Idle World: the original 4-account target this app was built for. */
object GameConfig {
    const val GAME_HOST = "poke.idleworld.online"
    const val LOGIN_URL = "https://poke.idleworld.online/login"
    const val START_URL = "$LOGIN_URL?ref=CJNKGPB"
    const val PANEL_COUNT = 4

    // Calibrated against this game's own WebSocket/REST protocol and DOM (see assets/scripts).
    const val ENABLE_RESOURCE_ALERTS = true
    const val ENABLE_DOCK_TOGGLE = true
    const val ENABLE_SELLGUARD = true
}
