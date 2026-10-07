package com.bsdung.musicassistantcar.playback.alac;

import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;

@UnstableApi
public final class AlacAudioRenderer extends DecoderAudioRenderer<AlacDecoder> {

  private static final String TAG = "AlacAudioRenderer";

  public AlacAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(eventHandler, eventListener, audioSink);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    String mimeType = format.sampleMimeType;
    if (mimeType == null || !MimeTypes.isAudio(mimeType)) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    }
    if (!MimeTypes.AUDIO_ALAC.equals(mimeType)) {
      return C.FORMAT_UNSUPPORTED_SUBTYPE;
    }
    if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
      return C.FORMAT_UNSUPPORTED_DRM;
    }
    int pcmEncoding = format.pcmEncoding != Format.NO_VALUE ? format.pcmEncoding : C.ENCODING_PCM_16BIT;
    if (!sinkSupportsFormat(Util.getPcmFormat(pcmEncoding, format.channelCount, format.sampleRate))) {
      if (!sinkSupportsFormat(Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate))) {
        return C.FORMAT_UNSUPPORTED_SUBTYPE;
      }
    }
    return C.FORMAT_HANDLED;
  }

  @Override
  protected AlacDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws AlacDecoderException {
    Log.i(TAG, "ALAC_RENDERER: SOFTWARE_FALLBACK (creating AlacDecoder for " + format.sampleRate + "Hz, " + format.channelCount + "ch)");
    return new AlacDecoder(format);
  }

  @Override
  protected Format getOutputFormat(AlacDecoder decoder) {
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(decoder.getChannelCount())
        .setSampleRate(decoder.getSampleRate())
        .setPcmEncoding(decoder.getPcmEncoding())
        .build();
  }
}
