package online.idleworld.pokegrid

import android.app.Application
import online.idleworld.pokegrid.notif.Notifier

class PokeGridApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier(this).ensureChannel()
    }
}
