package com.android.gamespace.display

import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.util.Log

/**
 * The two colour controls of SurfaceFlinger, reached the way system_server reaches them
 * (DisplayTransformManager): a transaction on the service, no more than a few floats long. The
 * caller is the system uid, which is all SurfaceFlinger asks of these transactions.
 *
 * Both are applied on top of whatever the system has set, and each other:
 *
 * - saturation is its own factor, kept apart from the colour matrix, so it never gets in the way
 *   of Night Light or the other tints;
 * - the colour matrix is the one that those tints are written to, so writing it replaces them.
 *   [DisplayEnhancer] only does that while none of them is on.
 *
 * The codes and the interface name below have to be the ones that DisplayTransformManager has in
 * the framework this is built with.
 */
internal class SurfaceFlingerColor {

    private var binder: IBinder? = null

    /** 1.0 leaves the colours alone. */
    fun setSaturation(value: Float): Boolean = transact(TRANSACTION_SATURATION) {
        writeFloat(value)
    }

    /** Null puts the identity back. [matrix] is 16 floats in the layout of [DisplayPreset.writeMatrix]. */
    fun setColorMatrix(matrix: FloatArray?): Boolean = transact(TRANSACTION_COLOR_MATRIX) {
        if (matrix == null) {
            writeInt(0)
        } else {
            writeInt(1)
            for (i in 0 until MATRIX_SIZE) writeFloat(matrix[i])
        }
    }

    private inline fun transact(code: Int, write: Parcel.() -> Unit): Boolean {
        val service = binder ?: ServiceManager.getService(SERVICE_NAME)?.also { binder = it }
            ?: return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(INTERFACE_TOKEN)
            data.write()
            service.transact(code, data, null, 0)
        } catch (e: Exception) {
            // SurfaceFlinger was restarted, or refused: ask for it again next time, and carry on.
            // Nothing here is worth stopping a game for.
            binder = null
            Log.w(TAG, "SurfaceFlinger transaction $code failed", e)
            false
        } finally {
            data.recycle()
        }
    }

    private companion object {
        const val TAG = "SurfaceFlingerColor"
        const val SERVICE_NAME = "SurfaceFlinger"
        const val INTERFACE_TOKEN = "android.ui.ISurfaceComposer"
        const val TRANSACTION_COLOR_MATRIX = 1015
        const val TRANSACTION_SATURATION = 1022
        const val MATRIX_SIZE = 16
    }
}
