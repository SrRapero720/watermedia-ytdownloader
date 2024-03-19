package com.github.kiulian.downloader.model;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final char[] ILLEGAL_FILENAME_CHARACTERS = {'/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':'};

    public static String removeIllegalChars(String fileName) {
        for (char c : ILLEGAL_FILENAME_CHARACTERS) {
            fileName = fileName.replace(c, '_');
        }
        return fileName;
    }

    public static void createOutDir(File outDir) throws IOException {
        if (!outDir.exists()) {
            boolean mkdirs = outDir.mkdirs();
            if (!mkdirs)
                throw new IOException("Could not create output directory: " + outDir);
        }
    }

    public static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {} 
        }
    }

    // 1:32:54
    public static int parseLengthSeconds(String text) {
        try {
            int length = 0;
            int beginIndex = 0;
            if (text.length() > 2) {
                int endIndex;
                if (text.length() > 5) {
                    // contains hours
                    endIndex = text.indexOf(':');
                    length += Integer.parseInt(text.substring(0, endIndex)) * 3600;
                    beginIndex = endIndex + 1;
                }
                endIndex = text.indexOf(':', beginIndex);
                length += Integer.parseInt(text.substring(beginIndex, endIndex)) * 60;
                beginIndex = endIndex + 1;
            }
            length += Integer.parseInt(text.substring(beginIndex));
            return length;
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            return -1;
        }
    }

    public static long parseViewCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String value = text.replaceAll("[^0-9]", "");
        if (!value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public static String parseRuns(JsonObject container) {
        if (container == null) {
            return null;
        }
        JsonArray runs = container.getAsJsonArray("runs");
        if (runs == null) {
            return null;
        } else if (runs.size() == 1) {
            return runs.get(0).getAsJsonObject().getAsJsonPrimitive("text").getAsString();
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < runs.size(); i++) {
                builder.append(runs.get(i).getAsJsonObject().getAsJsonPrimitive("text").getAsString());
            }
            return builder.toString();
        }
    }

    public static List<String> parseThumbnails(JsonObject container) {
        if (container == null) {
            return null;
        }
        JsonArray jsonThumbnails = container.getAsJsonArray("thumbnails");
        if (jsonThumbnails == null) {
            return null;
        } else {
            List<String> thumbnails = new ArrayList<>(jsonThumbnails.size());
            for (int i = 0; i < jsonThumbnails.size(); i++) {
                JsonObject jsonThumbnail = jsonThumbnails.get(i).getAsJsonObject();
                if (jsonThumbnail.has("url")) {
                    thumbnails.add(jsonThumbnail.getAsJsonPrimitive("url").getAsString());
                }
            }
            return thumbnails;
        }
    }
}
