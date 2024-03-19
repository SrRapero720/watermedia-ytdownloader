package com.github.kiulian.downloader.model.search.query;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;

@SuppressWarnings("serial")
public class QueryRefinementList extends ArrayList<QueryRefinement> implements QueryElement {

    private final String title;

    public QueryRefinementList(JsonObject json) {
        super(json.getAsJsonArray("cards").size());
        title = json.getAsJsonObject("header")
                .getAsJsonObject("richListHeaderRenderer")
                .getAsJsonObject("title")
                .getAsJsonPrimitive("simpleText").getAsString();
        JsonArray jsonCards = json.getAsJsonArray("cards");
        for (int i = 0; i < jsonCards.size(); i++) {
            JsonObject jsonRenderer = jsonCards.get(i).getAsJsonObject().getAsJsonObject("searchRefinementCardRenderer");
            add(new QueryRefinement(jsonRenderer));
        }
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public QueryElementType type() {
        return QueryElementType.REFINEMENT_LIST;
    }
}
