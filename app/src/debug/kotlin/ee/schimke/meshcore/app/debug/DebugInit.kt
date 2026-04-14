package ee.schimke.meshcore.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import ee.schimke.meshcore.app.MeshcoreApp

/**
 * Minimal ContentProvider whose only job is to run before
 * [android.app.Application.onCreate] and install a [DebugBridge]
 * implementation. Registered in `app/src/debug/AndroidManifest.xml`
 * only — release builds never see this class.
 */
class DebugInit : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val app = ctx.applicationContext as? MeshcoreApp ?: run {
            Log.w(TAG, "applicationContext is not MeshcoreApp; skipping install")
            return true
        }
        DebugBridge.instance = DebugBridgeImpl(app)
        Log.i(TAG, "DebugBridge installed")
        return true
    }

    override fun query(uri: Uri, proj: Array<String>?, sel: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<String>?): Int = 0

    companion object {
        private const val TAG = "DebugInit"
    }
}
