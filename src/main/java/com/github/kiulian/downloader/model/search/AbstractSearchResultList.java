package com.github.kiulian.downloader.model.search;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonObject;

import java.util.List;

public abstract class AbstractSearchResultList implements SearchResultItem {

    private String title;
    protected List<String> thumbnails;
    private String author;

    public AbstractSearchResultList() {}

    public AbstractSearchResultList(JsonObject json) {
        title = json.getAsJsonObject("title").getAsJsonPrimitive("simpleText").getAsString();
        author = Utils.parseRuns(json.getAsJsonObject("shortBylineText"));
    }

    @Override
    public String title() {
        return title;
    }

    public List<String> thumbnails() {
        return thumbnails;
    }

    public String author() {
        return author;
    }
}
