package com.github.kiulian.downloader.model.videos.formats;


import com.github.kiulian.downloader.model.Extension;
import com.google.gson.JsonObject;

public abstract class Format {

    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String AUDIO_VIDEO = "audio/video";

    private final boolean isAdaptive;

    protected final Itag itag;
    protected final String url;
    protected final String mimeType;
    protected final Extension extension;
    protected final Integer bitrate;
    protected final Long contentLength;
    protected final Long lastModified;
    protected final Long approxDurationMs;
    protected final String clientVersion;

    protected Format(JsonObject json, boolean isAdaptive, String clientVersion) {
        this.isAdaptive = isAdaptive;
        this.clientVersion = clientVersion;

        Itag itag;
        try {
            itag = Itag.valueOf("i" + json.getAsJsonPrimitive("itag").getAsInt());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            itag = Itag.unknown;
            itag.setId(json.getAsJsonPrimitive("itag").getAsInt());
        }
        this.itag = itag;

        url = json.getAsJsonPrimitive("url").getAsString().replace("\\u0026", "&");
        mimeType = json.getAsJsonPrimitive("mimeType").getAsString();
        bitrate = json.getAsJsonPrimitive("bitrate").getAsInt();
        contentLength = json.getAsJsonPrimitive("contentLength").getAsLong();
        lastModified = json.getAsJsonPrimitive("lastModified").getAsLong();
        approxDurationMs = json.getAsJsonPrimitive("approxDurationMs").getAsLong();

        if (mimeType == null || mimeType.isEmpty()) {
            extension = Extension.UNKNOWN;
        } else if (mimeType.contains(Extension.MPEG4.value())) {
            if (this instanceof AudioFormat)
                extension = Extension.M4A;
            else
                extension = Extension.MPEG4;
        } else if (mimeType.contains(Extension.WEBM.value())) {
            if (this instanceof AudioFormat)
                extension = Extension.WEBA;
            else
                extension = Extension.WEBM;
        } else if (mimeType.contains(Extension.FLV.value())) {
            extension = Extension.FLV;
        } else if (mimeType.contains(Extension._3GP.value())) {
            extension = Extension._3GP;
        } else {
            extension = Extension.UNKNOWN;
        }
    }

    public abstract String type();

    public boolean isAdaptive() {
        return isAdaptive;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public Itag itag() {
        return itag;
    }

    public Integer bitrate() {
        return bitrate;
    }

    public String mimeType() {
        return mimeType;
    }

    public String url() {
        return url;
    }

    public Long contentLength() {
        return contentLength;
    }

    public long lastModified() {
        return lastModified;
    }

    public Long duration() {
        return approxDurationMs;
    }

    public Extension extension() {
        return extension;
    }
}
