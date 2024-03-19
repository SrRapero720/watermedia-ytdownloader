package com.github.kiulian.downloader.model.videos;


import com.github.kiulian.downloader.model.AbstractVideoDetails;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class VideoDetails extends AbstractVideoDetails {

    private List<String> keywords;
    private String shortDescription;
    private long viewCount;
    private int averageRating;
    private boolean isLiveContent;
    private String liveUrl;

    public VideoDetails(String videoId) {
        this.videoId = videoId;
    }

    public VideoDetails(JsonObject json, String liveHLSUrl) {
        super(json);
        title = json.getAsJsonPrimitive("title").getAsString();
        author = json.getAsJsonPrimitive("author").getAsString();
        isLive = json.getAsJsonPrimitive("isLive").getAsBoolean();

        keywords = new ArrayList<>();
        List<JsonElement> keywordsJson = json.has("keywords") ? json.getAsJsonArray("keywords").asList() : new ArrayList<>();

        for (JsonElement e: keywordsJson) {
            keywords.add(e.getAsString());
        }

        shortDescription = json.getAsJsonPrimitive("shortDescription").getAsString();
        averageRating = json.getAsJsonPrimitive("averageRating").getAsInt();
        viewCount = json.getAsJsonPrimitive("viewCount").getAsLong();
        isLiveContent = json.getAsJsonPrimitive("isLiveContent").getAsBoolean();
        liveUrl = liveHLSUrl;
    }

    @Override
    public boolean isDownloadable()  {
        return !isLive() && !(isLiveContent && lengthSeconds() == 0);
    }

    public List<String> keywords() {
        return keywords;
    }

    public String description() {
        return shortDescription;
    }

    public long viewCount() {
        return viewCount;
    }

    public int averageRating() {
        return averageRating;
    }

    public boolean isLiveContent() {
        return isLiveContent;
    }

    public String liveUrl() {
        return liveUrl;
    }
}
