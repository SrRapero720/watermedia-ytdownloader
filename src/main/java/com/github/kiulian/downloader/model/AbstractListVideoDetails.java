package com.github.kiulian.downloader.model;

import com.google.gson.JsonObject;

// Video item of a list (playlist, or search result).
public class AbstractListVideoDetails extends AbstractVideoDetails {

    public AbstractListVideoDetails(JsonObject json) {
        super(json);
        author = Utils.parseRuns(json.getAsJsonObject("shortBylineText"));
        JsonObject jsonTitle = json.getAsJsonObject("title");
        if (jsonTitle.has("simpleText")) {
            title = jsonTitle.getAsJsonPrimitive("simpleText").getAsString();
        } else {
            title = Utils.parseRuns(jsonTitle);
        }
    }
}
