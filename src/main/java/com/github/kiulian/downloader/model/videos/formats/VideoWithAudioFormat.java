package com.github.kiulian.downloader.model.videos.formats;


import com.github.kiulian.downloader.model.videos.quality.AudioQuality;
import com.google.gson.JsonObject;

public class VideoWithAudioFormat extends VideoFormat {

    private final Integer averageBitrate;
    private final Integer audioSampleRate;
    private final AudioQuality audioQuality;

    public VideoWithAudioFormat(JsonObject json, boolean isAdaptive, String clientVersion) {
        super(json, isAdaptive, clientVersion);
        audioSampleRate = json.getAsJsonPrimitive("audioSampleRate").getAsInt();
        averageBitrate = json.getAsJsonPrimitive("averageBitrate").getAsInt();

        AudioQuality audioQuality = null;
        if (json.has("audioQuality")) {
            String[] split = json.getAsJsonPrimitive("audioQuality").getAsString().split("_");
            String quality = split[split.length - 1].toLowerCase();
            try {
                audioQuality = AudioQuality.valueOf(quality);
            } catch (IllegalArgumentException ignore) {
            }
        }
        this.audioQuality = audioQuality;
    }

    @Override
    public String type() {
        return AUDIO_VIDEO;
    }

    public Integer averageBitrate() {
        return averageBitrate;
    }

    public AudioQuality audioQuality() {
        return audioQuality != null ? audioQuality : itag.audioQuality();
    }

    public Integer audioSampleRate() {
        return audioSampleRate;
    }

}
