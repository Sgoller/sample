/*
 * Copyright © 2016 Intel Corporation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intel.webrtc.sample.utils;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.intel.webrtc.base.FilterCallback;
import com.intel.webrtc.base.VideoFrameFilterInterface;

import org.webrtc.EglBase;
import org.webrtc.GlUtil;

import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;

public class WoogeenBrightenFilter implements VideoFrameFilterInterface {
    // Vertex coordinates in Normalized Device Coordinates, i.e. (-1, -1) is bottom-left and (1,1) is top-right.
    private static final FloatBuffer FULL_RECTANGLE_BUF =
            GlUtil.createFloatBuffer(new float[]{
                    -1.0f, -1.0f,  // Bottom left.
                    1.0f, -1.0f,  // Bottom right.
                    -1.0f, 1.0f,  // Top left.
                    1.0f, 1.0f,  // Top right.
            });

    // Texture coordinates - (0, 0) is bottom-left and (1, 1) is top-right.
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF =
            GlUtil.createFloatBuffer(new float[]{
                    0.0f, 1.0f,
                    1.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f
            });

    private static final String VERTEX_SHADER_STRING =
            "varying vec2 interp_tc;\n"
                    + "attribute vec4 in_pos;\n"
                    + "attribute vec4 in_tc;\n"
                    + "\n"
                    + "uniform mat4 texMatrix;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    gl_Position = in_pos;\n"
                    + "    interp_tc = (texMatrix * in_tc).xy;\n"
                    + "}\n";

    private static final String BRIGHTEN_FRAGMENT_SHADER_STRING =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 interp_tc;\n"
                    + "\n"
                    + "uniform samplerExternalOES oes_tex;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  lowp vec4 textureColor = texture2D(oes_tex, interp_tc);\n"
                    + "  gl_FragColor = vec4((textureColor.rgb + vec3(0.25)), textureColor.w);\n"
                    + "}\n";

    //OpenGL program id.
    private int glProgramId = 0;
    private int[] glFramebufferId;
    private int[] glFramebufferTextureId;

    //Frame size.
    private int frameWidth = 0;
    private int frameHeight = 0;

    //shader string
    private String vertexShaderString;
    private String fragmentShaderString;

    //EglBase that holds the context the app runs on.
    private EglBase eglBase;
    private boolean isInitialized = false;

    //Singleton instance.
    private static WoogeenBrightenFilter filterInstance;

    //All OpenGL actions are excused on filter handler thread.
    private static HandlerThread filterThread;
    private static Handler filterHandler;

    public static WoogeenBrightenFilter create(final EglBase.Context sharedContext) {
        return create(sharedContext, null, null);
    }

    public static WoogeenBrightenFilter create(final EglBase.Context sharedContext,
                                               final String vertexShaderString,
                                               final String fragmentShaderString) {
        filterThread = new HandlerThread("FilterHandlerThread");
        filterThread.start();
        filterHandler = new Handler(filterThread.getLooper());
        try {
            final CountDownLatch barrier = new CountDownLatch(1);
            filterHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (filterInstance == null) {
                        filterInstance = new WoogeenBrightenFilter(sharedContext,
                                                                   vertexShaderString,
                                                                   fragmentShaderString);
                    }
                    barrier.countDown();
                }
            });
            barrier.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return filterInstance;
    }

    private WoogeenBrightenFilter(EglBase.Context sharedContext, String vertexShaderString,
                                  String fragmentShaderString) {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }

        this.vertexShaderString = vertexShaderString == null ? VERTEX_SHADER_STRING
                                                             : vertexShaderString;
        this.fragmentShaderString = fragmentShaderString == null ? BRIGHTEN_FRAGMENT_SHADER_STRING
                                                                 : fragmentShaderString;

        eglBase = EglBase.create(sharedContext, EglBase.CONFIG_PIXEL_BUFFER);
        eglBase.createDummyPbufferSurface();
    }

    private void initGL(String vertexShaderString, String fragmentShaderString) {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }
        int vertexShader;
        int fragmentShader;
        int programId;
        int[] link = new int[1];

        eglBase.makeCurrent();
        vertexShader = loadShader(vertexShaderString, GLES20.GL_VERTEX_SHADER);
        if (vertexShader == 0) {
            Log.e("Load Program", "Vertex Shader Failed");
            eglBase.detachCurrent();
            return;
        }
        fragmentShader = loadShader(fragmentShaderString, GLES20.GL_FRAGMENT_SHADER);
        if (fragmentShader == 0) {
            Log.e("Load Program", "Fragment Shader Failed");
            eglBase.detachCurrent();
            return;
        }

        programId = GLES20.glCreateProgram();

        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d("Load Program", "Linking Failed");
            eglBase.detachCurrent();
            return;
        }

        glProgramId = programId;
        GLES20.glUseProgram(glProgramId);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(glProgramId, "oes_tex"), 0);
        GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(glProgramId, "in_pos"));
        GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(glProgramId, "in_pos"), 2,
                                     GLES20.GL_FLOAT, false, 0, FULL_RECTANGLE_BUF);
        GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(glProgramId, "in_tc"));
        GLES20.glVertexAttribPointer(GLES20.glGetAttribLocation(glProgramId, "in_tc"), 2,
                                     GLES20.GL_FLOAT, false, 0, FULL_RECTANGLE_TEX_BUF);

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        GlUtil.checkNoGLES2Error("ImageFilter.initGL");

        eglBase.detachCurrent();
    }

    private int loadShader(final String strSource, final int iType) {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    //init FBO
    private void initFrameBuffer() {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }
        if (glFramebufferId == null) {
            glFramebufferId = new int[1];
            glFramebufferTextureId = new int[1];

            eglBase.makeCurrent();
            GLES20.glGenFramebuffers(1, glFramebufferId, 0);

            GLES20.glGenTextures(1, glFramebufferTextureId, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glFramebufferTextureId[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameWidth, frameHeight, 0,
                                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                                   GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                                   GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                                   GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                                   GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, glFramebufferId[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                                          GLES20.GL_TEXTURE_2D, glFramebufferTextureId[0], 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            eglBase.detachCurrent();
        }
    }

    private void reInitFrameBuffer() {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }

        if (glFramebufferId != null) {
            eglBase.makeCurrent();
            GLES20.glDeleteFramebuffers(1, glFramebufferId, 0);
            GLES20.glDeleteTextures(1, glFramebufferTextureId, 0);
            glFramebufferId = null;
            glFramebufferTextureId = null;
            eglBase.detachCurrent();
        }

        initFrameBuffer();
    }

    @Override
    public void filterTextureFrame(final int textureId, final int frameWidth, final int frameHeight,
                                   final float[] transformMatrix, final FilterCallback callback) {

        filterHandler.post(new Runnable() {
            @Override
            public void run() {
                textureFrameAvailableOnThread(textureId, frameWidth, frameHeight, transformMatrix,
                                              callback);
            }
        });
    }

    private void textureFrameAvailableOnThread(int textureId, int width, int height,
                                               float[] transformMatrix, FilterCallback callback) {
        if (Thread.currentThread() != filterHandler.getLooper().getThread()) {
            throw new RuntimeException("Wrong Thread");
        }

        if (frameWidth != width || frameHeight != height) {
            frameWidth = width;
            frameHeight = height;

            reInitFrameBuffer();
        }

        if (!isInitialized) {
            initGL(vertexShaderString, fragmentShaderString);
            initFrameBuffer();
            isInitialized = true;
        }

        if (glFramebufferId == null) {
            return;
        }

        eglBase.makeCurrent();

        GLES20.glViewport(0, 0, frameWidth, frameHeight);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, glFramebufferId[0]);

        if (textureId != -1) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        }

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(glProgramId, "texMatrix"), 1, false,
                                  transformMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, frameWidth, frameHeight);
        eglBase.detachCurrent();

        //Finish processing texture, trigger callback.
        callback.onComplete(glFramebufferTextureId[0], true);
    }
}
