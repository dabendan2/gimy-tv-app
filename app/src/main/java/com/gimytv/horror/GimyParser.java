package com.gimytv.horror;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class GimyParser {
    private static final String TAG = "GimyHorror_Parser";

    public static String fetchHtml(String urlStr) {
        try {
            Log.i(TAG, "Fetching HTML from: " + urlStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            is.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch HTML from URL: " + urlStr, e);
            return "";
        }
    }

    public static ArrayList<Movie> parseMoviesFromHtml(String html) {
        ArrayList<Movie> movies = new ArrayList<>();
        int index = html.indexOf("class=\"box-video-list\"");
        if (index == -1) {
            index = html.indexOf("layout-box");
        }
        if (index != -1) {
            String listHtml = html.substring(index);
            int endList = listHtml.indexOf("</ul>");
            if (endList != -1) {
                listHtml = listHtml.substring(0, endList);
            }

            String[] items = listHtml.split("<li");
            for (int i = 1; i < items.length; i++) {
                String block = items[i];

                String id = "";
                int hrefIdx = block.indexOf("href=\"/vod/");
                if (hrefIdx != -1) {
                    int start = hrefIdx + 11;
                    int end = block.indexOf(".html\"", start);
                    if (end != -1) id = block.substring(start, end);
                }
                if (id.isEmpty()) continue;

                String title = "";
                int titleIdx = block.indexOf("title=\"");
                if (titleIdx != -1) {
                    int start = titleIdx + 7;
                    int end = block.indexOf("\"", start);
                    if (end != -1) title = block.substring(start, end);
                }

                String imageUrl = "";
                int imgIdx = block.indexOf("data-original=\"");
                if (imgIdx != -1) {
                    int start = imgIdx + 15;
                    int end = block.indexOf("\"", start);
                    if (end != -1) imageUrl = block.substring(start, end);
                } else {
                    int styleIdx = block.indexOf("url('");
                    if (styleIdx != -1) {
                        int start = styleIdx + 5;
                        int end = block.indexOf("'", start);
                        if (end != -1) imageUrl = block.substring(start, end);
                    }
                }

                String note = "HD";
                int noteIdx = block.indexOf("class=\"note");
                if (noteIdx != -1) {
                    int start = block.indexOf(">", noteIdx) + 1;
                    int end = block.indexOf("</span>", start);
                    if (end != -1) note = block.substring(start, end).trim().replaceAll("<[^>]*>", "");
                }

                String subtitle = "";
                int subIdx = block.indexOf("class=\"subtitle");
                if (subIdx != -1) {
                    int start = block.indexOf(">", subIdx) + 1;
                    int end = block.indexOf("</div>", start);
                    if (end != -1) subtitle = block.substring(start, end).replace("&nbsp;", "").trim();
                }

                movies.add(new Movie(id, title, imageUrl, note, subtitle));
            }
            Log.d(TAG, "Successfully parsed " + movies.size() + " movies from HTML list block.");
        } else {
            Log.e(TAG, "Failed to find movie list container 'class=\"box-video-list\"' or 'layout-box' in HTML.");
        }
        return movies;
    }

    public static String[] parseMovieDetails(String detailHtml) {
        String synopsis = "暫無簡介";
        int synIdx = detailHtml.indexOf("class=\"details-content-all\">");
        if (synIdx == -1) {
            synIdx = detailHtml.indexOf("<span class=\"details-content-all\">");
        }
        if (synIdx != -1) {
            int start = detailHtml.indexOf(">", synIdx) + 1;
            int end = detailHtml.indexOf("</span>", start);
            if (end != -1) {
                synopsis = detailHtml.substring(start, end).trim();
                synopsis = synopsis.replaceAll("<[^>]*>", "");
            }
        } else {
            Log.w(TAG, "Could not find synopsis marker 'class=\"details-content-all\"' in detail HTML.");
        }

        String playPath = "";
        int playIdx = detailHtml.indexOf("href=\"/ep/");
        if (playIdx != -1) {
            int start = playIdx + 6;
            int end = detailHtml.indexOf("\"", start);
            if (end != -1) {
                playPath = detailHtml.substring(start, end);
            }
        }
        if (playPath.isEmpty()) {
            Log.w(TAG, "Could not parse play path (href=\"/ep/\") in detail HTML.");
        } else {
            Log.d(TAG, "Parsed play path successfully: " + playPath);
        }
        return new String[]{synopsis, playPath};
    }

    public static String parseM3U8Url(String playHtml) {
        String m3u8Url = "";
        int pdIdx = playHtml.indexOf("var player_data=");
        if (pdIdx != -1) {
            int start = pdIdx + 16;
            int end = playHtml.indexOf("</script>", start);
            if (end != -1) {
                String pdJson = playHtml.substring(start, end).trim();
                int urlIdx = pdJson.indexOf("\"url\":\"");
                if (urlIdx != -1) {
                    int urlStart = urlIdx + 7;
                    int urlEnd = pdJson.indexOf("\"", urlStart);
                    if (urlEnd != -1) {
                        m3u8Url = pdJson.substring(urlStart, urlEnd);
                        m3u8Url = m3u8Url.replace("\\/", "/");
                    }
                }
            }
        }
        if (m3u8Url.isEmpty()) {
            Log.e(TAG, "Failed to parse M3U8 streaming URL from player_data JSON.");
        } else {
            Log.i(TAG, "Successfully parsed stream M3U8 URL: " + m3u8Url);
        }
        return m3u8Url;
    }

    public static ArrayList<String> parseAllLines(String detailHtml) {
        ArrayList<String> lines = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("href=\"(/ep/\\d+-\\d+-1\\.html)\"");
        java.util.regex.Matcher matcher = pattern.matcher(detailHtml);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (!lines.contains(path)) {
                lines.add(path);
            }
        }
        return lines;
    }

    public static String constructCategoryUrl(String sort, String region, String year) {
        try {
            String sortParam = "熱門推薦".equals(sort) ? "hot" : ("最新上架".equals(sort) ? "time" : "score");
            String regionParam = "全部".equals(region) ? "" : region;
            String yearParam = "全部".equals(year) ? "" : year;

            // Construct MacCMS Standard Show URL with exactly 11 hyphens (12 parameters fields)
            String[] parts = new String[12];
            parts[0] = "10"; // '10' is the 'Horror' Category ID on gimyplus.com
            parts[1] = java.net.URLEncoder.encode(regionParam, "UTF-8");
            parts[2] = "hot".equals(sortParam) ? "hits" : sortParam;
            parts[3] = "";
            parts[4] = "";
            parts[5] = "";
            parts[6] = "";
            parts[7] = "";
            parts[8] = "";
            parts[9] = "";
            parts[10] = "";
            parts[11] = yearParam.isEmpty() ? ".html" : yearParam + ".html";

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                sb.append(parts[i]);
                if (i < parts.length - 1) {
                    sb.append("-");
                }
            }
            return "https://gimyplus.com/show/" + sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to construct category URL", e);
            return "https://gimyplus.com/show/10--hits---------.html"; // Fallback standard URL
        }
    }
}
