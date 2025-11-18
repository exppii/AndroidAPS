package app.aaps.wear.interaction.menus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionResendData
import app.aaps.core.keys.BooleanKey
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.interaction.utils.MenuListActivity

class MainMenuActivity : MenuListActivity() {

    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.label_actions_activity)
        super.onCreate(savedInstanceState)
        requestPermissions()
        rxBus.send(EventWearToMobile(ActionResendData("MainMenuListActivity")))
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        val permissions = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // Android 9.0 (Pie) and below
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 and above
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                Log.d("Permissions", "${permissions[i]} = ${if (grantResults[i] == PackageManager.PERMISSION_GRANTED) "Granted" else "Denied"}")
            }
        }
    }

    override fun provideElements(): List<MenuItem> =
        ArrayList<MenuItem>().apply {
            if (!preferences.get(BooleanKey.WearControl)) {
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_sync, getString(R.string.menu_resync)))
            } else {
                if (sp.getBoolean(R.string.key_show_wizard, true))
                    add(MenuItem(R.drawable.ic_calculator, getString(R.string.menu_wizard)))
                add(MenuItem(R.drawable.ic_e_carbs, getString(R.string.menu_ecarb)))
                add(MenuItem(R.drawable.ic_treatment, getString(R.string.menu_treatment)))
                add(MenuItem(R.drawable.ic_temptarget, getString(R.string.menu_tempt)))
                add(MenuItem(R.drawable.ic_profile, getString(R.string.status_profile_switch)))
                add(MenuItem(R.drawable.ic_settings, getString(R.string.menu_settings)))
                add(MenuItem(R.drawable.ic_status, getString(R.string.menu_status)))
                if (sp.getBoolean(R.string.key_prime_fill, false))
                    add(MenuItem(R.drawable.ic_canula, getString(R.string.menu_prime_fill)))
            }
        }

    override fun doAction(position: String) {
        when (position) {
            getString(R.string.menu_settings)         -> startActivity(Intent(this, PreferenceMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_resync)           -> rxBus.send(EventWearToMobile(ActionResendData("Re-Sync")))
            getString(R.string.status_profile_switch) -> rxBus.send(EventWearToMobile(EventData.ActionProfileSwitchSendInitialData(System.currentTimeMillis())))
            getString(R.string.menu_tempt)            -> startActivity(Intent(this, TempTargetActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_treatment)        -> startActivity(Intent(this, TreatmentActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_wizard)           -> startActivity(Intent(this, WizardActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_status)           -> startActivity(Intent(this, StatusMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_prime_fill)       -> startActivity(Intent(this, FillMenuActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            getString(R.string.menu_ecarb)            -> startActivity(Intent(this, ECarbActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
