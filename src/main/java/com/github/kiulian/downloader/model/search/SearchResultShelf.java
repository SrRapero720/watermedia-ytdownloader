package com.github.kiulian.downloader.model.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SearchResultShelf implements SearchResultItem {

    private final String title;
    private final List<SearchResultVideoDetails> videos;

    public SearchResultShelf(JsonObject json) {
        title = json.getAsJsonObject("title").getAsJsonPrimitive("simpleText").getAsString();
        JsonObject jsonContent = json.getAsJsonObject("content");
        
        // verticalListRenderer / horizontalMovieListRenderer
        String contentRendererKey = jsonContent.keySet().iterator().next();
        boolean isMovieShelf = contentRendererKey.contains("Movie");
        JsonArray jsonItems = jsonContent.getAsJsonObject(contentRendererKey).getAsJsonArray("items");
        videos = new ArrayList<>(jsonItems.size());
        for (int i = 0; i < jsonItems.size(); i++) {
            JsonObject jsonItem = jsonItems.get(i).getAsJsonObject();
            String itemRendererKey = jsonItem.keySet().iterator().next();
            videos.add(new SearchResultVideoDetails(jsonItem.getAsJsonObject(itemRendererKey), isMovieShelf));
        }
    }

    @Override
    public SearchResultItemType type() {
        return SearchResultItemType.SHELF;
    }

    @Override
    public SearchResultShelf asShelf() {
        return this;
    }

    @Override
    public String title() {
        return title;
    }

    public List<SearchResultVideoDetails> videos() {
        return videos;
    }

}
