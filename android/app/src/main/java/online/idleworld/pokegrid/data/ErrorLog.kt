package online.idleworld.pokegrid.data

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date

/** Mirrors the Electron app's relatorio-de-erros.log: append-only, rotated at 512KB, shareable. */
class ErrorLog(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "relatorio-de-erros.log")
    private var headerWritten = false

    @Synchronized
    fun write(origem: String, detalhe: String) {
        try {
            if (file.exists() && file.length() > 512 * 1024) {
                file.copyTo(File(appContext.filesDir, "relatorio-de-erros.antigo.log"), overwrite = true)
                file.delete()
            }
            val sb = StringBuilder()
            if (!headerWritten) {
                headerWritten = true
                sb.append("\n=== sessão de ${DateFormat.format("dd/MM/yyyy HH:mm:ss", Date())} · PokeGrid Android ===\n")
            }
            sb.append("[${DateFormat.format("dd/MM/yyyy HH:mm:ss", Date())}] [$origem] ${detalhe.take(4000)}\n")
            file.appendText(sb.toString())
        } catch (_: Exception) {
        }
    }

    fun shareIntent(): Intent {
        if (!file.exists()) file.writeText("Nenhum erro registrado até agora.\n")
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
