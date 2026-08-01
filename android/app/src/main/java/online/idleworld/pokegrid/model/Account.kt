package online.idleworld.pokegrid.model

data class Account(
    val name: String = "",
    val email: String = "",
    val senha: String = ""
) {
    val hasCredentials: Boolean
        get() = email.isNotBlank() && senha.isNotBlank()
}
