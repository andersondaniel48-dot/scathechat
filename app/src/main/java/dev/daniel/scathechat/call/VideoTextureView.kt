package dev.daniel.scathechat.call

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Video renderer on a TextureView. Unlike WebRTC's SurfaceViewRenderer it is drawn as part of the
 * normal view tree, so rounded corners, clipping and dragging work. Frames are center-cropped.
 */
class VideoTextureView(context: Context) : TextureView(context), VideoSink, TextureView.SurfaceTextureListener {
    private val renderer = EglRenderer("scathe-video")
    private var released = false

    fun init(egl: EglBase.Context, mirror: Boolean) {
        renderer.init(egl, EglBase.CONFIG_PLAIN, GlRectDrawer())
        renderer.setMirror(mirror)
        surfaceTextureListener = this
        if (isAvailable) surfaceTexture?.let { onSurfaceTextureAvailable(it, width, height) }
    }

    override fun onFrame(frame: VideoFrame) {
        if (!released) renderer.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
        renderer.createEglSurface(surface)
        if (w > 0 && h > 0) renderer.setLayoutAspectRatio(w.toFloat() / h)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {
        if (w > 0 && h > 0) renderer.setLayoutAspectRatio(w.toFloat() / h)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val latch = CountDownLatch(1)
        renderer.releaseEglSurface { latch.countDown() }
        latch.await(500, TimeUnit.MILLISECONDS)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    fun release() {
        if (released) return
        released = true
        renderer.release()
    }
}
