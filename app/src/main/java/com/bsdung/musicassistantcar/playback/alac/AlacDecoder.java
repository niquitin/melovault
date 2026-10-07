package com.bsdung.musicassistantcar.playback.alac;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

@UnstableApi
public final class AlacDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, AlacDecoderException> {

  private static final int DEFAULT_BUFFER_COUNT = 16;
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 16 * 1024;

  private final AlacFile alacFile;
  private final int channelCount;
  private final int sampleRate;
  private final int bitDepth;
  private final @C.PcmEncoding int pcmEncoding;
  private final int maxSamplesPerFrame;
  private int[] pcmDecodeBuffer;

  public AlacDecoder(Format format) throws AlacDecoderException {
    this(format, DEFAULT_BUFFER_COUNT, DEFAULT_BUFFER_COUNT);
  }

  public AlacDecoder(Format format, int numInputBuffers, int numOutputBuffers)
      throws AlacDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleDecoderOutputBuffer[numOutputBuffers]);
    if (!MimeTypes.AUDIO_ALAC.equals(format.sampleMimeType)) {
      throw new AlacDecoderException("Unsupported MIME type: " + format.sampleMimeType);
    }
    List<byte[]> initData = format.initializationData;
    if (initData == null || initData.isEmpty() || initData.get(0) == null) {
      throw new AlacDecoderException("Missing ALAC initialization data (magic cookie)");
    }
    byte[] cookie = initData.get(0);
    if (cookie.length < 24) {
      throw new AlacDecoderException("Invalid ALAC cookie length: " + cookie.length);
    }

    this.bitDepth = cookie.length >= 48 ? (cookie[29] & 0xFF) : (cookie[5] & 0xFF);
    this.channelCount = cookie.length >= 48 ? (cookie[33] & 0xFF) : (cookie[9] & 0xFF);
    this.sampleRate = format.sampleRate != Format.NO_VALUE ? format.sampleRate : parseSampleRate(cookie);

    if (bitDepth == 16) {
      this.pcmEncoding = C.ENCODING_PCM_16BIT;
    } else if (bitDepth == 24) {
      this.pcmEncoding = C.ENCODING_PCM_24BIT;
    } else if (bitDepth == 32) {
      this.pcmEncoding = C.ENCODING_PCM_32BIT;
    } else {
      throw new AlacDecoderException("Unsupported ALAC bit depth: " + bitDepth);
    }

    this.alacFile = AlacDecodeUtils.create_alac(bitDepth, channelCount);
    AlacDecodeUtils.alac_set_info(alacFile, cookie);

    this.maxSamplesPerFrame = alacFile.setinfo_max_samples_per_frame > 0 ? alacFile.setinfo_max_samples_per_frame : 4096;
    this.pcmDecodeBuffer = new int[maxSamplesPerFrame * Math.max(channelCount, 2) * 3];

    int inputSize = format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    setInitialInputBufferSize(inputSize);
  }

  private static int parseSampleRate(byte[] cookie) {
    int offset = cookie.length >= 48 ? 44 : 20;
    return (((cookie[offset] & 0xFF) << 24)
        | ((cookie[offset + 1] & 0xFF) << 16)
        | ((cookie[offset + 2] & 0xFF) << 8)
        | (cookie[offset + 3] & 0xFF));
  }

  @Override
  public String getName() {
    return "AlacDecoder-Java";
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleDecoderOutputBuffer createOutputBuffer() {
    return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected AlacDecoderException createUnexpectedDecodeException(Throwable error) {
    return new AlacDecoderException("Unexpected decode error", error);
  }

  @Override
  protected AlacDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    ByteBuffer inputData = inputBuffer.data;
    if (inputData == null || inputData.remaining() == 0) {
      outputBuffer.shouldBeSkipped = true;
      return null;
    }

    try {
      int inputSize = inputData.remaining();
      byte[] inputBytes = new byte[inputSize];
      inputData.get(inputBytes);

      int requiredInts = maxSamplesPerFrame * Math.max(channelCount, 2) * 3;
      if (pcmDecodeBuffer.length < requiredInts) {
        pcmDecodeBuffer = new int[requiredInts];
      }

      int decodedBytes = AlacDecodeUtils.decode_frame(alacFile, inputBytes, pcmDecodeBuffer, 0);
      if (decodedBytes <= 0) {
        outputBuffer.shouldBeSkipped = true;
        return null;
      }

      ByteBuffer outData = outputBuffer.init(inputBuffer.timeUs, decodedBytes);
      if (bitDepth == 16) {
        int numSamples = decodedBytes / (channelCount * 2);
        int totalElements = numSamples * channelCount;
        for (int i = 0; i < totalElements; i++) {
          int s = pcmDecodeBuffer[i];
          outData.put((byte) (s & 0xFF));
          outData.put((byte) ((s >> 8) & 0xFF));
        }
      } else if (bitDepth == 24 || bitDepth == 32) {
        for (int i = 0; i < decodedBytes; i++) {
          outData.put((byte) (pcmDecodeBuffer[i] & 0xFF));
        }
      }
      outData.position(0);
      outData.limit(decodedBytes);
      return null;
    } catch (Exception e) {
      return new AlacDecoderException("ALAC frame decode failed", e);
    }
  }

  public int getChannelCount() {
    return channelCount;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public int getBitDepth() {
    return bitDepth;
  }

  public @C.PcmEncoding int getPcmEncoding() {
    return pcmEncoding;
  }
}
