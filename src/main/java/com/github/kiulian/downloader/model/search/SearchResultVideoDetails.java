package com.github.kiulian.downloader.model.search;

import com.github.kiulian.downloader.model.AbstractListVideoDetails;
import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SearchResultVideoDetails extends AbstractListVideoDetails implements SearchResultItem {

    private final boolean isMovie;
    private String description;
    private String viewCountText;
    private long viewCount;
    // Scheduled diffusion (seconds time stamp)
    private long startTime;
    // Subtitled, CC, ...
    private List<String> badges;
    // Animated images
    private List<String> richThumbnails;

    public SearchResultVideoDetails(JsonObject json, boolean isMovie) {
        super(json);
        this.isMovie = isMovie;
        if (json.has("lengthText")) {
            String lengthText = json.getAsJsonObject("lengthText").getAsJsonPrimitive("simpleText").getAsString();
            lengthSeconds = Utils.parseLengthSeconds(lengthText);
        }
        if (isMovie) {
            description = Utils.parseRuns(json.getAsJsonObject("descriptionSnippet"));
        } else if (json.has("detailedMetadataSnippets")) {
            description = Utils.parseRuns(json.getAsJsonArray("detailedMetadataSnippets")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("snippetText"));
        }
        if (json.has("upcomingEventData")) {
            String startTimeText = json.getAsJsonObject("upcomingEventData").getAsJsonPrimitive("startTime").getAsString();
            startTime = Long.parseLong(startTimeText);
            viewCount = -1;
        } else if (json.has("viewCountText")) {
            JsonObject jsonCount = json.getAsJsonObject("viewCountText");
            if (jsonCount.has("simpleText")) {
                viewCountText = jsonCount.getAsJsonPrimitive("simpleText").getAsString();
                viewCount = Utils.parseViewCount(viewCountText);
            } else if (jsonCount.has("runs")) {
                viewCountText = Utils.parseRuns(jsonCount);
                viewCount = -1;
            }
        }
        if (json.has("badges")) {
            JsonArray jsonBadges = json.getAsJsonArray("badges");
            badges = new ArrayList<>(jsonBadges.size());
            for (int i = 0; i < jsonBadges.size(); i++) {
                JsonObject jsonBadge = jsonBadges.get(i).getAsJsonObject();
                if (jsonBadge.has("metadataBadgeRenderer")) {
                    badges.add(jsonBadge.getAsJsonObject("metadataBadgeRenderer").getAsJsonPrimitive("label").getAsString());
                }
            }
        }
        if (json.has("richThumbnail")) {
            try {
                JsonArray jsonThumbs = json.getAsJsonObject("richThumbnail")
                        .getAsJsonObject("movingThumbnailRenderer")
                        .getAsJsonObject("movingThumbnailDetails")
                        .getAsJsonArray("thumbnails");
                richThumbnails = new ArrayList<>(jsonThumbs.size());
                for (int i = 0; i < jsonThumbs.size(); i++) {
                    richThumbnails.add(jsonThumbs.get(i).getAsJsonObject().getAsJsonPrimitive("url").getAsString());
                }
            } catch (NullPointerException ignored) {}
        }
    }

    @Override
    public SearchResultItemType type() {
        return SearchResultItemType.VIDEO;
    }

    @Override
    public SearchResultVideoDetails asVideo() {
        return this;
    }

    public boolean isMovie() {
        return isMovie;
    }

    public boolean isLive() {
        return viewCount == -1;
    }

    public String viewCountText() {
        return viewCountText;
    }

    public long viewCount() {
        return viewCount;
    }

    public long startTime() {
        return startTime;
    }

    public List<String> badges() {
        return badges;
    }

    public List<String> richThumbnails() {
        return richThumbnails;
    }

    public String description() {
        return description;
    }
}
