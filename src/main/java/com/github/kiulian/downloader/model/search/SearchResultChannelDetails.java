package com.github.kiulian.downloader.model.search;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonObject;

public class SearchResultChannelDetails extends AbstractSearchResultList {

    private final String channelId;
    private final String videoCountText;
    private final String subscriberCountText;
    private final String description;

    public SearchResultChannelDetails(JsonObject json) {
        super(json);
        channelId = json.getAsJsonPrimitive("channelId").getAsString();
        videoCountText = Utils.parseRuns(json.getAsJsonObject("videoCountText"));
        if (json.has("subscriberCountText")) {
            subscriberCountText = json.getAsJsonObject("subscriberCountText").getAsJsonPrimitive("simpleText").getAsString();
        } else {
            subscriberCountText = null;
        }
        description = Utils.parseRuns(json.getAsJsonObject("descriptionSnippet"));
        thumbnails = Utils.parseThumbnails(json.getAsJsonObject("thumbnail"));
    }

    @Override
    public SearchResultItemType type() {
        return SearchResultItemType.CHANNEL;
    }

    @Override
    public SearchResultChannelDetails asChannel() {
        return this;
    }
    public String channelId() {
        return channelId;
    }

    public String videoCountText() {
        return videoCountText;
    }

    public String subscriberCountText() {
        return subscriberCountText;
    }

    public String description() {
        return description;
    }
}
