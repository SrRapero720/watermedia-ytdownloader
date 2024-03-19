package com.github.kiulian.downloader.model.search.query;

import com.github.kiulian.downloader.model.Utils;
import com.google.gson.JsonObject;

public class QueryAutoCorrection implements QueryElement {

    private final String query;

    public QueryAutoCorrection(JsonObject json) {
        query = Utils.parseRuns(json.getAsJsonObject("correctedQuery"));
    }

    @Override
    public String title() {
        return null;
    }

    public String query() {
        return query;
    }

    @Override
    public QueryElementType type() {
        return QueryElementType.AUTO_CORRECTION;
    }
}
