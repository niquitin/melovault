package com.bsdung.musicassistantcar.playback.alac;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderException;

@UnstableApi
public class AlacDecoderException extends DecoderException {
    public AlacDecoderException(String message) {
        super(message);
    }

    public AlacDecoderException(Throwable cause) {
        super(cause);
    }

    public AlacDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
