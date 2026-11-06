package com.android.gamespace.widget.tiles

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class FlashlightTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val cameraManager: CameraManager? by lazy {
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var torchCameraId: String? = null
    private var torchEnabled = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchEnabled = enabled
                refreshState()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.flashlight_title)
        icon?.setImageResource(R.drawable.ic_flashlight)
        torchCameraId = findTorchCameraId()
        refreshState()
        runCatching {
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        }
    }

    override fun onDetachedFromWindow() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
        super.onDetachedFromWindow()
    }

    private fun findTorchCameraId(): String? = runCatching {
        val cm = cameraManager ?: return null
        cm.cameraIdList.firstOrNull { id ->
            val chars = cm.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private fun refreshState() {
        summary?.text = context.getString(if (torchEnabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = torchEnabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val camId = torchCameraId ?: findTorchCameraId().also { torchCameraId = it } ?: return
        val target = !torchEnabled
        torchEnabled = target
        refreshState()
        Thread {
            runCatching { cameraManager?.setTorchMode(camId, target) }
        }.start()
    }
}
