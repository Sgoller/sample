/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package com.intel.webrtc.sample.utils;

import android.util.Log;

import com.intel.webrtc.base.VideoFrameGeneratorInterface;

import org.webrtc.VideoCapturer;

import java.io.IOException;
import java.io.RandomAccessFile;

public class VideoFrameGeneratorY4M implements VideoFrameGeneratorInterface {
    private final static String TAG = "VideoReaderY4M";
    private final int frameWidth;
    private final int frameHeight;
    private final int frameSize;

    // First char after header
    private final long videoStart;

    private static final String Y4M_FRAME_DELIMETER = "FRAME";

    private final RandomAccessFile mediaFileStream;

    @Override
    public int getWidth() {
        return frameWidth;
    }

    @Override
    public int getFps() {
        return 30;
    }

    @Override
    public VideoCapturer.ColorFormat getColorFormat() {
        return VideoCapturer.ColorFormat.I420;
    }

    @Override
    public int getHeight() {
        return frameHeight;
    }

    public VideoFrameGeneratorY4M(String file) throws IOException {
        mediaFileStream = new RandomAccessFile(file, "r");
        StringBuilder builder = new StringBuilder();
        for (;;) {
            int c = mediaFileStream.read();
            if (c == -1) {
                // End of file reached.
                throw new RuntimeException("Found end of file before end of header for file: " + file);
            }
            if (c == '\n') {
                // End of header found.
                break;
            }
            builder.append((char) c);
        }
        videoStart = mediaFileStream.getFilePointer();
        String header = builder.toString();
        String[] headerTokens = header.split("[ ]");
        int w = 0;
        int h = 0;
        String colorSpace = "";
        for (String tok : headerTokens) {
            char c = tok.charAt(0);
            switch (c) {
                case 'W':
                    w = Integer.parseInt(tok.substring(1));
                    break;
                case 'H':
                    h = Integer.parseInt(tok.substring(1));
                    break;
                case 'C':
                    colorSpace = tok.substring(1);
                    break;
            }
        }
        Log.d(TAG, "Color space: " + colorSpace);
        if (!colorSpace.equals("420") && !colorSpace.equals("420mpeg2")) {
            throw new IllegalArgumentException(
                    "Does not support any other color space than I420 or I420mpeg2");
        }
        if ((w % 2) == 1 || (h % 2) == 1) {
            throw new IllegalArgumentException("Does not support odd width or height");
        }
        frameWidth = w;
        frameHeight = h;
        frameSize = w * h * 3 / 2;
        Log.d(TAG, "frame dim: (" + w + ", " + h + ") frameSize: " + frameSize);
    }

    @Override
    public byte[] generateNextFrame() {
        byte[] frame = new byte[frameSize];
        try {
            byte[] frameDelim = new byte[Y4M_FRAME_DELIMETER.length() + 1];
            if (mediaFileStream.read(frameDelim) < frameDelim.length) {
                // We reach end of file, loop
                mediaFileStream.seek(videoStart);
                if (mediaFileStream.read(frameDelim) < frameDelim.length) {
                    throw new RuntimeException("Error looping video");
                }
            }
            String frameDelimStr = new String(frameDelim);
            if (!frameDelimStr.equals(Y4M_FRAME_DELIMETER + "\n")) {
                throw new RuntimeException(
                        "Frames should be delimited by FRAME plus newline, found delimter was: '"
                                + frameDelimStr + "'");
            }
            mediaFileStream.readFully(frame);
            return frame;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        try {
            mediaFileStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Problem closing file", e);
        }
    }
}
