package com.github.kiulian.downloader.parser;

import com.github.kiulian.downloader.Config;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.YoutubeException.BadPageException;
import com.github.kiulian.downloader.cipher.Cipher;
import com.github.kiulian.downloader.cipher.CipherFactory;
import com.github.kiulian.downloader.downloader.Downloader;
import com.github.kiulian.downloader.downloader.YoutubeCallback;
import com.github.kiulian.downloader.downloader.request.*;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.downloader.response.ResponseImpl;
import com.github.kiulian.downloader.extractor.Extractor;
import com.github.kiulian.downloader.model.playlist.PlaylistDetails;
import com.github.kiulian.downloader.model.playlist.PlaylistInfo;
import com.github.kiulian.downloader.model.playlist.PlaylistVideoDetails;
import com.github.kiulian.downloader.model.search.*;
import com.github.kiulian.downloader.model.search.query.*;
import com.github.kiulian.downloader.model.subtitles.SubtitlesInfo;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.*;
import com.google.gson.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ParserImpl implements Parser {
    private static final Gson GSON = new Gson();
    private static final String ANDROID_APIKEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    private final Config config;
    private final Downloader downloader;
    private final Extractor extractor;
    private final CipherFactory cipherFactory;

    public ParserImpl(Config config, Downloader downloader, Extractor extractor, CipherFactory cipherFactory) {
        this.config = config;
        this.downloader = downloader;
        this.extractor = extractor;
        this.cipherFactory = cipherFactory;
    }

    @Override
    public Response<VideoInfo> parseVideo(RequestVideoInfo request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<VideoInfo> result = executorService.submit(() -> parseVideo(request.getVideoId(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            VideoInfo result = parseVideo(request.getVideoId(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    private VideoInfo parseVideo(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        // try to spoof android
        // workaround for issue https://github.com/sealedtx/java-youtube-downloader/issues/97
        VideoInfo videoInfo = parseVideoAndroid(videoId, callback);
        if (videoInfo == null) {
            videoInfo = parseVideoWeb(videoId, callback);
        }
        if (callback != null) {
            callback.onFinished(videoInfo);
        }
        return videoInfo;
    }

    private VideoInfo parseVideoAndroid(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        String url = "https://youtubei.googleapis.com/youtubei/v1/player?key=" + ANDROID_APIKEY;

        String body =
                "{" +
                "  \"videoId\": \"" + videoId + "\"," +
                "  \"context\": {" +
                "    \"client\": {" +
                "      \"hl\": \"en\"," +
                "      \"gl\": \"US\"," +
                "      \"clientName\": \"ANDROID_TESTSUITE\"," +
                "      \"clientVersion\": \"1.9\"," +
                "      \"androidSdkVersion\": 31" +
                "    }" +
                "  }" +
                "}";

        RequestWebpage request = new RequestWebpage(url, "POST", body)
                .header("Content-Type", "application/json");

        Response<String> response = downloader.downloadWebpage(request);
        if (!response.ok()) {
            return null;
        }

        JsonObject playerResponse;
        try {
            playerResponse = JsonParser.parseString(response.data()).getAsJsonObject();
        } catch (Exception ignore) {
            return null;
        }

        VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
        if (videoDetails.isDownloadable()) {
            JsonObject context = playerResponse.getAsJsonObject("responseContext");
            String clientVersion = extractor.extractClientVersionFromContext(context);
            List<Format> formats;
            try {
                formats = parseFormats(playerResponse, null, clientVersion);
            } catch (YoutubeException e) {
                if (callback != null) {
                    callback.onError(e);
                }
                throw e;
            }

            List<SubtitlesInfo> subtitlesInfo = parseCaptions(playerResponse);
            return new VideoInfo(videoDetails, formats, subtitlesInfo);
        } else {
            return new VideoInfo(videoDetails, Collections.emptyList(), Collections.emptyList());
        }

    }

    private VideoInfo parseVideoWeb(String videoId, YoutubeCallback<VideoInfo> callback) throws YoutubeException {
        String htmlUrl = "https://www.youtube.com/watch?v=" + videoId;

        Response<String> response = downloader.downloadWebpage(new RequestWebpage(htmlUrl));
        if (!response.ok()) {
            YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", htmlUrl, response.error().getMessage()));
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        String html = response.data();

        JsonObject playerConfig;
        try {
            playerConfig = extractor.extractPlayerConfigFromHtml(html);
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }

        JsonObject args = playerConfig.getAsJsonObject("args");
        JsonObject playerResponse = args.getAsJsonObject("player_response");

        if (!playerResponse.has("streamingData") && !playerResponse.has("videoDetails")) {
            YoutubeException e = new YoutubeException.BadPageException("streamingData and videoDetails not found");
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }

        VideoDetails videoDetails = parseVideoDetails(videoId, playerResponse);
        if (videoDetails.isDownloadable()) {
            String jsUrl;
            try {
                jsUrl = extractor.extractJsUrlFromConfig(playerConfig, videoId);
            } catch (YoutubeException e) {
                if (callback != null) {
                    callback.onError(e);
                }
                throw e;
            }
            JsonObject context = playerConfig.getAsJsonObject("args").getAsJsonObject("player_response").getAsJsonObject("responseContext");
            String clientVersion = extractor.extractClientVersionFromContext(context);
            List<Format> formats;
            try {
                formats = parseFormats(playerResponse, jsUrl, clientVersion);
            } catch (YoutubeException e) {
                if (callback != null) {
                    callback.onError(e);
                }
                throw e;
            }
            List<SubtitlesInfo> subtitlesInfo = parseCaptions(playerResponse);
            return new VideoInfo(videoDetails, formats, subtitlesInfo);
        } else {
            return new VideoInfo(videoDetails, Collections.emptyList(), Collections.emptyList());
        }
    }

    private VideoDetails parseVideoDetails(String videoId, JsonObject playerResponse) {
        if (!playerResponse.has("videoDetails")) {
            return new VideoDetails(videoId);
        }

        JsonObject videoDetails = playerResponse.getAsJsonObject("videoDetails");
        String liveHLSUrl = null;
        JsonPrimitive isLive = videoDetails.getAsJsonPrimitive("isLive");
        if (isLive != null && isLive.getAsBoolean()) {
            if (playerResponse.has("streamingData")) {
                liveHLSUrl = playerResponse.getAsJsonObject("streamingData").getAsJsonPrimitive("hlsManifestUrl").getAsString();
            }
        }
        return new VideoDetails(videoDetails, liveHLSUrl);
    }

    private List<Format> parseFormats(JsonObject playerResponse, String jsUrl, String clientVersion) throws YoutubeException {
        if (!playerResponse.getAsJsonObject().has("streamingData")) {
            throw new YoutubeException.BadPageException("streamingData not found");
        }

        JsonObject streamingData = playerResponse.getAsJsonObject("streamingData");
        JsonArray jsonFormats = new JsonArray();
        if (streamingData.has("formats")) {
            jsonFormats.addAll(streamingData.getAsJsonArray("formats"));
        }
        JsonArray jsonAdaptiveFormats = new JsonArray();
        if (streamingData.has("adaptiveFormats")) {
            jsonAdaptiveFormats.addAll(streamingData.getAsJsonArray("adaptiveFormats"));
        }

        List<Format> formats = new ArrayList<>(jsonFormats.size() + jsonAdaptiveFormats.size());
        populateFormats(formats, jsonFormats, jsUrl, false, clientVersion);
        populateFormats(formats, jsonAdaptiveFormats, jsUrl, true, clientVersion);
        return formats;
    }

    private void populateFormats(List<Format> formats, JsonArray jsonFormats, String jsUrl, boolean isAdaptive, String clientVersion) throws YoutubeException.CipherException {
        for (int i = 0; i < jsonFormats.size(); i++) {
            JsonObject json = jsonFormats.get(i).getAsJsonObject();
            JsonPrimitive type = json.getAsJsonPrimitive("type");
            if ("FORMAT_STREAM_TYPE_OTF".equals(type != null ? type.getAsString() : ""))
                continue; // unsupported otf formats which cause 404 not found

            int itagValue = json.getAsJsonPrimitive("itag").getAsInt();
            Itag itag;
            try {
                itag = Itag.valueOf("i" + itagValue);
            } catch (IllegalArgumentException e) {
                System.err.println("Error parsing format: unknown itag " + itagValue);
                continue;
            }

            try {
                Format format = parseFormat(json, jsUrl, itag, isAdaptive, clientVersion);
                formats.add(format);
            } catch (YoutubeException.CipherException e) {
                throw e;
            } catch (YoutubeException e) {
                System.err.println("Error " + e.getMessage() + " parsing format: " + json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Format parseFormat(JsonObject json, String jsUrl, Itag itag, boolean isAdaptive, String clientVersion) throws YoutubeException {
        if (json.has("signatureCipher")) {
            JsonObject jsonCipher = new JsonObject();
            String[] cipherData = json.getAsJsonPrimitive("signatureCipher").getAsString().replace("\\u0026", "&").split("&");
            for (String s : cipherData) {
                String[] keyValue = s.split("=");
                jsonCipher.addProperty(keyValue[0], keyValue[1]);
            }
            if (!jsonCipher.has("url")) {
                throw new YoutubeException.BadPageException("Could not found url in cipher data");
            }
            String urlWithSig = jsonCipher.getAsJsonPrimitive("url").getAsString();
            try {
                urlWithSig = URLDecoder.decode(urlWithSig, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            if (urlWithSig.contains("signature")
                    || (!jsonCipher.has("s") && (urlWithSig.contains("&sig=") || urlWithSig.contains("&lsig=")))) {
                // do nothing, this is pre-signed videos with signature
            } else if (jsUrl != null) {
                String s = jsonCipher.getAsJsonPrimitive("s").getAsString();
                try {
                    s = URLDecoder.decode(s, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                Cipher cipher = cipherFactory.createCipher(jsUrl);

                String signature = cipher.getSignature(s);
                String decipheredUrl = urlWithSig + "&sig=" + signature;
                json.addProperty("url", decipheredUrl);
            } else {
                throw new YoutubeException.BadPageException("deciphering is required but no js url");
            }
        }

        boolean hasVideo = itag.isVideo() || json.has("size") || json.has("width");
        boolean hasAudio = itag.isAudio() || json.has("audioQuality");

        if (hasVideo && hasAudio)
            return new VideoWithAudioFormat(json, isAdaptive, clientVersion);
        else if (hasVideo)
            return new VideoFormat(json, isAdaptive, clientVersion);
        return new AudioFormat(json, isAdaptive, clientVersion);
    }

    private List<SubtitlesInfo> parseCaptions(JsonObject playerResponse) {
        if (!playerResponse.has("captions")) {
            return Collections.emptyList();
        }
        JsonObject captions = playerResponse.getAsJsonObject("captions");

        JsonObject playerCaptionsTracklistRenderer = captions.getAsJsonObject("playerCaptionsTracklistRenderer");
        if (playerCaptionsTracklistRenderer == null || playerCaptionsTracklistRenderer.isEmpty()) {
            return Collections.emptyList();
        }

        JsonArray captionsArray = playerCaptionsTracklistRenderer.getAsJsonArray("captionTracks");
        if (captionsArray == null || captionsArray.isEmpty()) {
            return Collections.emptyList();
        }

        List<SubtitlesInfo> subtitlesInfo = new ArrayList<>();
        for (int i = 0; i < captionsArray.size(); i++) {
            JsonObject subtitleInfo = captionsArray.get(i).getAsJsonObject();
            String language = subtitleInfo.getAsJsonPrimitive("languageCode").getAsString();
            String url = subtitleInfo.getAsJsonPrimitive("baseUrl").getAsString();
            String vssId = subtitleInfo.getAsJsonPrimitive("vssId").getAsString();

            if (language != null && url != null && vssId != null) {
                boolean isAutoGenerated = vssId.startsWith("a.");
                subtitlesInfo.add(new SubtitlesInfo(url, language, isAutoGenerated, true));
            }
        }
        return subtitlesInfo;
    }

    @Override
    public Response<PlaylistInfo> parsePlaylist(RequestPlaylistInfo request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<PlaylistInfo> result = executorService.submit(() -> parsePlaylist(request.getPlaylistId(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            PlaylistInfo result = parsePlaylist(request.getPlaylistId(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }

    }

    private PlaylistInfo parsePlaylist(String playlistId, YoutubeCallback<PlaylistInfo> callback) throws YoutubeException {
        String htmlUrl = "https://www.youtube.com/playlist?list=" + playlistId;

        Response<String> response = downloader.downloadWebpage(new RequestWebpage(htmlUrl));
        if (!response.ok()) {
            YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", htmlUrl, response.error().getMessage()));
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        String html = response.data();

        JsonObject initialData;
        try {
            initialData = extractor.extractInitialDataFromHtml(html);
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }

        if (!initialData.has("metadata")) {
            throw new YoutubeException.BadPageException("Invalid initial data json");
        }

        PlaylistDetails playlistDetails = parsePlaylistDetails(playlistId, initialData);

        List<PlaylistVideoDetails> videos;
        try {
            videos = parsePlaylistVideos(initialData, playlistDetails.videoCount());
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        return new PlaylistInfo(playlistDetails, videos);
    }

    private PlaylistDetails parsePlaylistDetails(String playlistId, JsonObject initialData) {
        String title = initialData.getAsJsonObject("metadata")
                .getAsJsonObject("playlistMetadataRenderer")
                .getAsJsonPrimitive("title").getAsString();
        JsonArray sideBarItems = initialData.getAsJsonObject("sidebar").getAsJsonObject("playlistSidebarRenderer").getAsJsonArray("items");
        String author = null;
        try {
            // try to retrieve author, some playlists may have no author
            author = sideBarItems.get(1).getAsJsonObject()
                    .getAsJsonObject("playlistSidebarSecondaryInfoRenderer")
                    .getAsJsonObject("videoOwner")
                    .getAsJsonObject("videoOwnerRenderer")
                    .getAsJsonObject("title")
                    .getAsJsonArray("runs")
                    .get(0).getAsJsonObject()
                    .getAsJsonPrimitive("text").getAsString();
        } catch (Exception ignored) {
        }
        JsonArray stats = sideBarItems.get(0).getAsJsonObject()
                .getAsJsonObject("playlistSidebarPrimaryInfoRenderer")
                .getAsJsonArray("stats");
        int videoCount = extractor.extractIntegerFromText(stats.get(0).getAsJsonObject().getAsJsonArray("runs").get(0).getAsJsonObject().getAsJsonPrimitive("text").getAsString());
        long viewCount = extractor.extractLongFromText(stats.get(1).getAsJsonObject().getAsJsonPrimitive("simpleText").getAsString());

        return new PlaylistDetails(playlistId, title, author, videoCount, viewCount);
    }

    private List<PlaylistVideoDetails> parsePlaylistVideos(JsonObject initialData, int videoCount) throws YoutubeException {
        JsonObject content;

        try {
            content = initialData.getAsJsonObject("contents")
                    .getAsJsonObject("twoColumnBrowseResultsRenderer")
                    .getAsJsonArray("tabs").get(0).getAsJsonObject()
                    .getAsJsonObject("tabRenderer")
                    .getAsJsonObject("content")
                    .getAsJsonObject("sectionListRenderer")
                    .getAsJsonArray("contents").get(0).getAsJsonObject()
                    .getAsJsonObject("itemSectionRenderer")
                    .getAsJsonArray("contents").get(0).getAsJsonObject()
                    .getAsJsonObject("playlistVideoListRenderer");
        } catch (NullPointerException | ClassCastException e) {
            throw new YoutubeException.BadPageException("Playlist initial data not found");
        }

        List<PlaylistVideoDetails> videos;
        if (videoCount > 0) {
            videos = new ArrayList<>(videoCount);
        } else {
            videos = new LinkedList<>();
        }
        JsonObject context = initialData.getAsJsonObject("responseContext");
        String clientVersion = extractor.extractClientVersionFromContext(context);

        populatePlaylist(content, videos, clientVersion);
        return videos;
    }

    private void populatePlaylist(JsonObject content, List<PlaylistVideoDetails> videos, String clientVersion) throws YoutubeException {
        JsonArray contents;
        if (content.has("contents")) { // parse first items (up to 100)
            contents = content.getAsJsonArray("contents");
        } else if (content.has("continuationItems")) { // parse continuationItems
            contents = content.getAsJsonArray("continuationItems");
        } else if (content.has("continuations")) { // load continuation
            JsonObject nextContinuationData = content.getAsJsonArray("continuations")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("nextContinuationData");
            String continuation = nextContinuationData.getAsJsonPrimitive("continuation").getAsString();
            String ctp = nextContinuationData.getAsJsonPrimitive("clickTrackingParams").getAsString();
            loadPlaylistContinuation(continuation, ctp, videos, clientVersion);
            return;
        } else { // nothing found
            return;
        }

        for (int i = 0; i < contents.size(); i++) {
            JsonObject contentsItem = contents.get(i).getAsJsonObject();
            if (contentsItem.has("playlistVideoRenderer")) {
                videos.add(new PlaylistVideoDetails(contentsItem.getAsJsonObject("playlistVideoRenderer")));
            } else {
                if (contentsItem.has("continuationItemRenderer")) {
                    JsonObject continuationEndpoint = contentsItem.getAsJsonObject("continuationItemRenderer")
                            .getAsJsonObject("continuationEndpoint");
                    String continuation = continuationEndpoint.getAsJsonObject("continuationCommand").getAsJsonPrimitive("token").getAsString();
                    String ctp = continuationEndpoint.getAsJsonPrimitive("clickTrackingParams").getAsString();
                    loadPlaylistContinuation(continuation, ctp, videos, clientVersion);
                }
            }
        }
    }

    private void loadPlaylistContinuation(String continuation, String ctp, List<PlaylistVideoDetails> videos, String clientVersion) throws YoutubeException {
        JsonObject content;
        String url = "https://www.youtube.com/youtubei/v1/browse?key=" + ANDROID_APIKEY;

        JsonObject context = new JsonObject();
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", "2.20201021.03.00");
        context.add("client", client);

        JsonObject clickTracking = new JsonObject();
        clickTracking.addProperty("clickTrackingParams", ctp);

        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("continuation", continuation);
        body.add("clickTracking", clickTracking);

        RequestWebpage request = new RequestWebpage(url, "POST", body.toString())
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", clientVersion)
                .header("Content-Type", "application/json");

        Response<String> response = downloader.downloadWebpage(request);
        if (!response.ok()) {
            throw new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", url, response.error().getMessage()));
        }
        String html = response.data();

        try {
            JsonObject jsonResponse = JsonParser.parseString(html).getAsJsonObject();

            if (jsonResponse.has("continuationContents")) {
                content = jsonResponse
                        .getAsJsonObject("continuationContents")
                        .getAsJsonObject("playlistVideoListContinuation");
            } else {
                content = jsonResponse.getAsJsonArray("onResponseReceivedActions")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("appendContinuationItemsAction");
            }
            populatePlaylist(content, videos, clientVersion);
        } catch (YoutubeException e) {
            throw e;
        } catch (Exception e) {
            throw new YoutubeException.BadPageException("Could not parse playlist continuation json");
        }
    }

    @Override
    public Response<PlaylistInfo> parseChannelsUploads(RequestChannelUploads request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<PlaylistInfo> result = executorService.submit(() -> parseChannelsUploads(request.getChannelId(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            PlaylistInfo result = parseChannelsUploads(request.getChannelId(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    private PlaylistInfo parseChannelsUploads(String channelId, YoutubeCallback<PlaylistInfo> callback) throws YoutubeException {
        String playlistId = null;
        if (channelId.length() == 24 && channelId.startsWith("UC")) { // channel id pattern
            playlistId = "UU" + channelId.substring(2); // replace "UC" with "UU"
        } else { // channel name
            String channelLink = "https://www.youtube.com/c/" + channelId + "/videos?view=57";

            Response<String> response = downloader.downloadWebpage(new RequestWebpage(channelLink));
            if (!response.ok()) {
                YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", channelLink, response.error().getMessage()));
                if (callback != null) {
                    callback.onError(e);
                }
                throw e;
            }
            String html = response.data();

            Scanner scan = new Scanner(html);
            scan.useDelimiter("list=");
            while (scan.hasNext()) {
                String pId = scan.next();
                if (pId.startsWith("UU")) {
                    playlistId = pId.substring(0, 24);
                    break;
                }
            }
        }
        if (playlistId == null) {
            final YoutubeException e = new YoutubeException.BadPageException("Upload Playlist not found");
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        return parsePlaylist(playlistId, callback);
    }

    @Override
    public Response<List<SubtitlesInfo>> parseSubtitlesInfo(RequestSubtitlesInfo request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<List<SubtitlesInfo>> result = executorService.submit(() -> parseSubtitlesInfo(request.getVideoId(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            List<SubtitlesInfo> result = parseSubtitlesInfo(request.getVideoId(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    private List<SubtitlesInfo> parseSubtitlesInfo(String videoId, YoutubeCallback<List<SubtitlesInfo>> callback) throws YoutubeException {
        String xmlUrl = "https://video.google.com/timedtext?hl=en&type=list&v=" + videoId;

        Response<String> response = downloader.downloadWebpage(new RequestWebpage(xmlUrl));
        if (!response.ok()) {
            YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", xmlUrl, response.error().getMessage()));
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        String xml = response.data();
        List<String> languages;
        try {
            languages = extractor.extractSubtitlesLanguagesFromXml(xml);
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }

        List<SubtitlesInfo> subtitlesInfo = new ArrayList<>();
        for (String language : languages) {
            String url = String.format("https://www.youtube.com/api/timedtext?lang=%s&v=%s",
                    language, videoId);
            subtitlesInfo.add(new SubtitlesInfo(url, language, false));
        }

        return subtitlesInfo;
    }

    @Override
    public Response<SearchResult> parseSearchResult(RequestSearchResult request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<SearchResult> result = executorService.submit(() -> parseSearchResult(request.query(), request.encodeParameters(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            SearchResult result = parseSearchResult(request.query(), request.encodeParameters(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    @Override
    public Response<SearchResult> parseSearchContinuation(RequestSearchContinuation request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<SearchResult> result = executorService.submit(() -> parseSearchContinuation(request.continuation(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            SearchResult result = parseSearchContinuation(request.continuation(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    @Override
    public Response<SearchResult> parseSearcheable(RequestSearchable request) {
        if (request.isAsync()) {
            ExecutorService executorService = config.getExecutorService();
            Future<SearchResult> result = executorService.submit(() -> parseSearchable(request.searchPath(), request.getCallback()));
            return ResponseImpl.fromFuture(result);
        }
        try {
            SearchResult result = parseSearchable(request.searchPath(), request.getCallback());
            return ResponseImpl.from(result);
        } catch (YoutubeException e) {
            return ResponseImpl.error(e);
        }
    }

    private SearchResult parseSearchResult(String query, String parameters, YoutubeCallback<SearchResult> callback) throws YoutubeException {
        String searchQuery;
        try {
            searchQuery = URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            searchQuery = query;
            e.printStackTrace();
        }
        String url = "https://www.youtube.com/results?search_query=" + searchQuery;
        if (parameters != null) {
            url += "&sp=" + parameters;
        }
        try {
            return parseHtmlSearchResult(url);
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
    }

    private SearchResult parseSearchable(String searchPath, YoutubeCallback<SearchResult> callback) throws YoutubeException {
        String url = "https://www.youtube.com" + searchPath;
        try {
            return parseHtmlSearchResult(url);
        } catch (YoutubeException e) {
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
    }

    private SearchResult parseHtmlSearchResult(String url) throws YoutubeException {
        Response<String> response = downloader.downloadWebpage(new RequestWebpage(url));
        if (!response.ok()) {
            throw new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", url, response.error().getMessage()));
        }

        String html = response.data();
        
        JsonObject initialData = extractor.extractInitialDataFromHtml(html);
        JsonArray rootContents;
        try {
            rootContents = initialData.getAsJsonObject("contents")
                    .getAsJsonObject("twoColumnSearchResultsRenderer")
                    .getAsJsonObject("primaryContents")
                    .getAsJsonObject("sectionListRenderer")
                    .getAsJsonArray("contents");
        } catch (NullPointerException e) {
            throw new YoutubeException.BadPageException("Search result root contents not found");
        }
        
        long estimatedCount = extractor.extractLongFromText(initialData.getAsJsonPrimitive("estimatedResults").getAsString());
        String clientVersion = extractor.extractClientVersionFromContext(initialData.getAsJsonObject("responseContext"));
        SearchContinuation continuation = getSearchContinuation(rootContents, clientVersion);
        return parseSearchResult(estimatedCount, rootContents, continuation);
    }

    private SearchResult parseSearchContinuation(SearchContinuation continuation, YoutubeCallback<SearchResult> callback) throws YoutubeException {
        String url = "https://www.youtube.com/youtubei/v1/search?key=" + ANDROID_APIKEY + "&prettyPrint=false";

        JsonObject context = new JsonObject();
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", "2.20201021.03.00");
        context.add("client", client);

        JsonObject clickTracking = new JsonObject();
        clickTracking.addProperty("clickTrackingParams", continuation.clickTrackingParameters());


        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("continuation", continuation.token());
        body.add("clickTracking", clickTracking);

        RequestWebpage request = new RequestWebpage(url, "POST", body.toString())
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", continuation.clientVersion())
                .header("Content-Type", "application/json");

        Response<String> response = downloader.downloadWebpage(request);
        if (!response.ok()) {
            YoutubeException e = new YoutubeException.DownloadException(String.format("Could not load url: %s, exception: %s", url, response.error().getMessage()));
            if (callback != null) {
                callback.onError(e);
            }
            throw e;
        }
        String html = response.data();

        JsonObject jsonResponse;
        JsonArray rootContents;
        try {
            jsonResponse = JsonParser.parseString(html).getAsJsonObject();
            if (jsonResponse.has("onResponseReceivedCommands")) {
                rootContents = jsonResponse.getAsJsonArray("onResponseReceivedCommands")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("appendContinuationItemsAction")
                        .getAsJsonArray("continuationItems");
            } else {
                throw new YoutubeException.BadPageException("Could not find continuation data");
            }
        } catch (YoutubeException e) {
            throw e;
        } catch (Exception e) {
            throw new YoutubeException.BadPageException("Could not parse search continuation json");
        }
        
        long estimatedResults = extractor.extractLongFromText(jsonResponse.getAsJsonPrimitive("estimatedResults").getAsString());
        SearchContinuation nextContinuation = getSearchContinuation(rootContents, continuation.clientVersion());
        return parseSearchResult(estimatedResults, rootContents, nextContinuation);
    }

    private SearchContinuation getSearchContinuation(JsonArray rootContents, String clientVersion) {
        if (rootContents.size() > 1) {
            if (rootContents.get(1).getAsJsonObject().has("continuationItemRenderer")) {
                JsonObject endPoint = rootContents.get(1).getAsJsonObject()
                        .getAsJsonObject("continuationItemRenderer")
                        .getAsJsonObject("continuationEndpoint");
                String token = endPoint.getAsJsonObject("continuationCommand").getAsJsonPrimitive("token").getAsString();
                String ctp = endPoint.getAsJsonPrimitive("clickTrackingParams").getAsString();
                return new SearchContinuation(token, clientVersion, ctp);
            }
        }
        return null;
    }

    private SearchResult parseSearchResult(long estimatedResults, JsonArray rootContents, SearchContinuation continuation) throws BadPageException {
        JsonArray contents;

        try {
            contents = rootContents.get(0).getAsJsonObject()
                    .getAsJsonObject("itemSectionRenderer")
                    .getAsJsonArray("contents");
        } catch (NullPointerException e) {
            throw new YoutubeException.BadPageException("Search result contents not found");
        }

        List<SearchResultItem> items = new ArrayList<>(contents.size());
        Map<QueryElementType, QueryElement> queryElements = new HashMap<>();
        for (int i = 0; i < contents.size(); i++) {
            final SearchResultElement element = parseSearchResultElement(contents.get(i).getAsJsonObject());
            if (element != null) {
                if (element instanceof SearchResultItem) {
                    items.add((SearchResultItem) element);
                } else {
                    QueryElement queryElement = (QueryElement) element;
                    queryElements.put(queryElement.type(), queryElement);
                }
            }
        }
        if (continuation == null) {
            return new SearchResult(estimatedResults, items, queryElements);
        } else {
            return new ContinuatedSearchResult(estimatedResults, items, queryElements, continuation);
        }
    }

    private static SearchResultElement parseSearchResultElement(JsonObject jsonItem) {
        String rendererKey = jsonItem.keySet().iterator().next();
        JsonObject jsonRenderer = jsonItem.getAsJsonObject(rendererKey);
        switch (rendererKey) {
        case "videoRenderer":
            return new SearchResultVideoDetails(jsonRenderer, false);
        case "movieRenderer":
            return new SearchResultVideoDetails(jsonRenderer, true);
        case "playlistRenderer":
            return new SearchResultPlaylistDetails(jsonRenderer);
        case "channelRenderer":
            return new SearchResultChannelDetails(jsonRenderer);
        case "shelfRenderer":
            return new SearchResultShelf(jsonRenderer);
        case "showingResultsForRenderer":
            return new QueryAutoCorrection(jsonRenderer);
        case "didYouMeanRenderer":
            return new QuerySuggestion(jsonRenderer);
        case "horizontalCardListRenderer":
            return new QueryRefinementList(jsonRenderer);
       default:
           System.out.println("Unknown search result element type " + rendererKey);
           System.out.println(jsonItem);
           return null;
        }
    }
}
