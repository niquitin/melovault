/*
 * AlacFile.java
 *
 * Copyright (c) 2011 Peter McQuillan
 * All Rights Reserved.
 *
 * Distributed under the BSD Software License.
 */
package com.bsdung.musicassistantcar.playback.alac;

public class AlacFile {

    byte[] input_buffer;
    int ibIdx = 0;
    int input_buffer_bitaccumulator = 0;

    int samplesize = 0;
    int numchannels = 0;
    int bytespersample = 0;

    LeadingZeros lz = new LeadingZeros();

    private int buffer_size = 65536;
    int[] predicterror_buffer_a = new int[buffer_size];
    int[] predicterror_buffer_b = new int[buffer_size];

    int[] outputsamples_buffer_a = new int[buffer_size];
    int[] outputsamples_buffer_b = new int[buffer_size];

    int[] uncompressed_bytes_buffer_a = new int[buffer_size];
    int[] uncompressed_bytes_buffer_b = new int[buffer_size];

    public int setinfo_max_samples_per_frame = 0;
    public int setinfo_7a = 0;
    public int setinfo_sample_size = 0;
    public int setinfo_rice_historymult = 0;
    public int setinfo_rice_initialhistory = 0;
    public int setinfo_rice_kmodifier = 0;
    public int setinfo_7f = 0;
    public int setinfo_80 = 0;
    public int setinfo_82 = 0;
    public int setinfo_86 = 0;
    public int setinfo_8a_rate = 0;

    public int[] predictor_coef_table = new int[1024];
    public int[] predictor_coef_table_a = new int[1024];
    public int[] predictor_coef_table_b = new int[1024];

    public void ensureBufferSize(int samples) {
        if (samples > buffer_size) {
            buffer_size = Math.max(samples * 2, 65536);
            predicterror_buffer_a = new int[buffer_size];
            predicterror_buffer_b = new int[buffer_size];
            outputsamples_buffer_a = new int[buffer_size];
            outputsamples_buffer_b = new int[buffer_size];
            uncompressed_bytes_buffer_a = new int[buffer_size];
            uncompressed_bytes_buffer_b = new int[buffer_size];
        }
    }
}
