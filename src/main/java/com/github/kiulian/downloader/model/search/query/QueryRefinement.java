package com.github.kiulian.downloader.model.search.query;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonObject;

import java.util.List;

public class QueryRefinement extends Searchable {

    private final List<String> thumbnails;

    public QueryRefinement(JsonObject json) {
        super(json);
        thumbnails = Utils.parseThumbnails(json.getAsJsonObject("thumbnail"));
    }

    public List<String> thumbnails() {
        return thumbnails;
    }

    @Override
    protected String extractQuery(JsonObject json) {
        return Utils.parseRuns(json.getAsJsonObject("query"));
    }

    @Override
    protected String extractSearchPath(JsonObject json) {
        return json.getAsJsonObject("searchEndpoint")
                .getAsJsonObject("commandMetadata")
                .getAsJsonObject("webCommandMetadata")
                .getAsJsonPrimitive("url").getAsString();
    }

}
