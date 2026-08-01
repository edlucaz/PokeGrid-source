package online.idleworld.pokegrid.model

data class Account(
    val name: String = "",
    val email: String = "",
    val senha: String = ""
) {
    val hasCredentials: Boolean
        get() = email.isNotBlank() && senha.isNotBlank()
}

/** Fixed at 4 to match the game's panel layout; panels beyond [enabledCount] just stay off. */
const val MAX_PANELS = 4
