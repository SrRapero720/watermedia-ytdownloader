package com.github.kiulian.downloader.model.search;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedList;

public class SearchResultPlaylistDetails extends AbstractSearchResultList {

    private final String playlistId;
    private final int videoCount;

    public SearchResultPlaylistDetails(JsonObject json) {
        super(json);
        playlistId = json.getAsJsonPrimitive("playlistId").getAsString();
        JsonArray thumbnailGroups = json.getAsJsonArray("thumbnails");
        thumbnails = new LinkedList<>();
        for (int i = 0; i < thumbnailGroups.size(); i++) {
            thumbnails.addAll(Utils.parseThumbnails(thumbnailGroups.get(i).getAsJsonObject()));
        }
        if (json.has("videoCount")) {
            videoCount = Integer.parseInt(json.getAsJsonPrimitive("videoCount").getAsString());
        } else {
            videoCount = -1;
        }
    }

    @Override
    public SearchResultItemType type() {
        return SearchResultItemType.PLAYLIST;
    }

    @Override
    public SearchResultPlaylistDetails asPlaylist() {
        return this;
    }

    public String playlistId() {
        return playlistId;
    }

    public int videoCount() {
        return videoCount;
    }
}
