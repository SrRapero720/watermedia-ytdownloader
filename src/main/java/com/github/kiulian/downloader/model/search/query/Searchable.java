package com.github.kiulian.downloader.model.search.query;

import com.google.gson.JsonObject;

public abstract class Searchable {

    protected final String query;
    protected final String searchPath;

    protected abstract String extractQuery(JsonObject json);
    protected abstract String extractSearchPath(JsonObject json);

    public Searchable(JsonObject json) {
        super();
        this.query = extractQuery(json);
        this.searchPath = extractSearchPath(json);
    }

    public String query() {
        return query;
    }

    public String searchPath() {
        return searchPath;
    }

}
