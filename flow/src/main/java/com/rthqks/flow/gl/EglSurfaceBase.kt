/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rthqks.flow.gl

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Common base class for EGL surfaces.
 *
 *
 * There can be multiple surfaces associated with a single context.
 */
open class EglSurfaceBase protected constructor(// EglCore object we're associated with.  It may be associated with multiple surfaces.
    protected var mEglCore: EglCore
) {

    private var mEGLSurface = EGL14.EGL_NO_SURFACE
    private var mWidth = -1
    private var mHeight = -1

    /**
     * Returns the surface's width, in pixels.
     *
     *
     * If this is called on a window surface, and the underlying surface is in the process
     * of changing size, we may not see the new size right away (e.g. in the "surfaceChanged"
     * callback).  The size should match after the next buffer swap.
     */
    val width: Int
        get() = if (mWidth < 0) {
            mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH)
        } else {
            mWidth
        }

    /**
     * Returns the surface's height, in pixels.
     */
    val height: Int
        get() = if (mHeight < 0) {
            mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT)
        } else {
            mHeight
        }

    /**
     * Creates a window surface.
     *
     *
     * @param surface May be a Surface or SurfaceTexture.
     */
    fun createWindowSurface(surface: Any) {
        check(!(mEGLSurface !== EGL14.EGL_NO_SURFACE)) { "surface already created" }
        mEGLSurface = mEglCore.createWindowSurface(surface)

        // Don't cache width/height here, because the size of the underlying surface can change
        // out from under us (see e.g. HardwareScalerActivity).
        //mWidth = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        //mHeight = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
    }

    /**
     * Creates an off-screen surface.
     */
    fun createOffscreenSurface(width: Int, height: Int) {
        check(!(mEGLSurface !== EGL14.EGL_NO_SURFACE)) { "surface already created" }
        mEGLSurface = mEglCore.createOffscreenSurface(width, height)
        mWidth = width
        mHeight = height
    }

    /**
     * Release the EGL surface.
     */
    fun releaseEglSurface() {
        mEglCore.releaseSurface(mEGLSurface)
        mEGLSurface = EGL14.EGL_NO_SURFACE
        mHeight = -1
        mWidth = mHeight
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        if (CURRENT_EGL_SURFACE.getAndSet(mEGLSurface) != mEGLSurface) {
            mEglCore.makeCurrent(mEGLSurface)
        }
    }

    /**
     * Makes our EGL context and surface current for drawing, using the supplied surface
     * for reading.
     */
    fun makeCurrentReadFrom(readSurface: EglSurfaceBase) {
        mEglCore.makeCurrent(mEGLSurface, readSurface.mEGLSurface)
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    fun swapBuffers(): Boolean {
        val result = mEglCore.swapBuffers(mEGLSurface)
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed")
        }
        return result
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        mEglCore.setPresentationTime(mEGLSurface, nsecs)
    }

    fun isCurrent(): Boolean = mEglCore.isCurrent(mEGLSurface)

    companion object {
        const val TAG = "EglSurfaceBase"
        val CURRENT_EGL_SURFACE = AtomicReference<EGLSurface>()
    }
}
