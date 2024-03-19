package com.github.kiulian.downloader.model.search.query;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonObject;

public class QuerySuggestion extends Searchable implements QueryElement {

    private final String title;

    public QuerySuggestion(JsonObject json) {
        super(json);
        title = Utils.parseRuns(json.getAsJsonObject("didYouMean"));
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public QueryElementType type() {
        return QueryElementType.SUGGESTION;
    }

    @Override
    protected String extractQuery(JsonObject json) {
        return Utils.parseRuns(json.getAsJsonObject("correctedQuery"));
    }

    @Override
    protected String extractSearchPath(JsonObject json) {
        return json.getAsJsonObject("correctedQueryEndpoint")
                .getAsJsonObject("commandMetadata")
                .getAsJsonObject("webCommandMetadata")
                .getAsJsonPrimitive("url").getAsString();
    }
}
