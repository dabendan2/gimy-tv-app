package com.gimytv.horror;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class GimyParser {
    private static final String TAG = "GimyHorror_Parser";

    public static String extractBetween(String source, String startToken, String endToken, int fromIndex) {
        if (source == null) return "";
        int startIdx = source.indexOf(startToken, fromIndex);
        if (startIdx == -1) return "";
        int startPos = startIdx + startToken.length();
        int endIdx = source.indexOf(endToken, startPos);
        if (endIdx == -1) return "";
        return source.substring(startPos, endIdx);
    }

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

        // 1. Check if it's the search page
        if (html.contains("details-info-min") || html.contains("class=\"search-list\"") || html.contains("search-item")) {
            Log.i(TAG, "Detected Gimy search results page. Using specialized search parser.");
            if (html.contains("class=\"search-list\"") || html.contains("search-item")) {
                String[] blocks = html.split("<article class=\"search-item\"");
                if (blocks.length == 1) {
                    blocks = html.split("class=\"search-item\"");
                }
                for (int i = 1; i < blocks.length; i++) {
                    String block = blocks[i];
                    
                    String id = "";
                    int hrefIdx = block.indexOf("href=\"/vod/");
                    if (hrefIdx != -1) {
                        int start = hrefIdx + 11;
                        int end = block.indexOf(".html\"", start);
                        if (end != -1) id = block.substring(start, end);
                    }
                    if (id.isEmpty()) continue;
                    
                    String title = "";
                    int titleIdx = block.indexOf("class=\"search-item__title\">");
                    if (titleIdx != -1) {
                        int aIdx = block.indexOf("<a ", titleIdx);
                        if (aIdx != -1) {
                            int start = block.indexOf(">", aIdx) + 1;
                            int end = block.indexOf("</a>", start);
                            if (end != -1) title = block.substring(start, end).trim();
                        }
                    }
                    if (title.isEmpty()) {
                        int ariaIdx = block.indexOf("aria-label=\"");
                        if (ariaIdx != -1) {
                            int start = ariaIdx + 12;
                            int end = block.indexOf("\"", start);
                            if (end != -1) title = block.substring(start, end);
                        }
                    }
                    
                    String imageUrl = "";
                    int imgIdx = block.indexOf("src=\"");
                    if (imgIdx != -1) {
                        int start = imgIdx + 5;
                        int end = block.indexOf("\"", start);
                        if (end != -1) imageUrl = block.substring(start, end);
                    }
                    
                    String note = "HD";
                    int metaIdx = block.indexOf("class=\"search-item__meta\">");
                    if (metaIdx != -1) {
                        int start = metaIdx + 26;
                        int end = block.indexOf("</p>", start);
                        if (end != -1) {
                            String metaStr = block.substring(start, end).replaceAll("<[^>]*>", "").trim();
                            String[] parts = metaStr.split("·");
                            if (parts.length > 0) {
                                note = parts[parts.length - 1].trim();
                            }
                        }
                    }
                    
                    String subtitle = "";
                    int subIdx = block.indexOf("主演:");
                    if (subIdx == -1) {
                        subIdx = block.indexOf("主演：");
                    }
                    if (subIdx != -1) {
                        int start = subIdx + 3;
                        int end = block.indexOf("</p>", start);
                        if (end != -1) {
                            subtitle = block.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                        }
                    }
                    
                    movies.add(new Movie(id, title, imageUrl, note, subtitle));
                }
            } else {
                String[] blocks = html.split("class=\"details-info-min");
                // Skip the first element which is the header html
                for (int i = 1; i < blocks.length; i++) {
                    String block = blocks[i];
                    
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
                    } else {
                        int stateIdx = block.indexOf("<span>狀態：</span>");
                        if (stateIdx != -1) {
                            int start = stateIdx + 14;
                            int end = block.indexOf("</li>", start);
                            if (end != -1) note = block.substring(start, end).trim().replaceAll("<[^>]*>", "");
                        }
                    }
                    
                    String subtitle = "";
                    int subIdx = block.indexOf("主演：</span>");
                    if (subIdx != -1) {
                        int start = subIdx + 9;
                        int end = block.indexOf("</li>", start);
                        if (end != -1) {
                            subtitle = block.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                        }
                    }
                    
                    movies.add(new Movie(id, title, imageUrl, note, subtitle));
                }
            }
            Log.d(TAG, "Search parser successfully parsed " + movies.size() + " movies from search results.");
            return movies;
        }

        if (html.contains("class=\"grid\"")) {
            Log.i(TAG, "Detected new Gimy grid layout. Using specialized grid parser.");
            int gridIdx = html.indexOf("class=\"grid\"");
            String gridHtml = html.substring(gridIdx);
            String[] blocks = gridHtml.split("<article");
            for (int i = 1; i < blocks.length; i++) {
                String block = blocks[i];
                
                String id = "";
                int hrefIdx = block.indexOf("href=\"/vod/");
                if (hrefIdx != -1) {
                    int start = hrefIdx + 11;
                    int end = block.indexOf(".html\"", start);
                    if (end != -1) id = block.substring(start, end);
                }
                if (id.isEmpty()) continue;
                
                String title = "";
                int titleIdx = block.indexOf("class=\"card__title\">");
                if (titleIdx != -1) {
                    int start = titleIdx + 20;
                    int end = block.indexOf("</h3>", start);
                    if (end != -1) title = block.substring(start, end).trim();
                }
                if (title.isEmpty()) {
                    int ariaIdx = block.indexOf("aria-label=\"");
                    if (ariaIdx != -1) {
                        int start = ariaIdx + 12;
                        int end = block.indexOf("\"", start);
                        if (end != -1) title = block.substring(start, end).trim();
                    }
                }
                
                String imageUrl = "";
                int imgIdx = block.indexOf("src=\"");
                if (imgIdx != -1) {
                    int start = imgIdx + 5;
                    int end = block.indexOf("\"", start);
                    if (end != -1) imageUrl = block.substring(start, end);
                }
                if (imageUrl.isEmpty() || imageUrl.contains("logow") || imageUrl.contains("logob")) {
                    int origIdx = block.indexOf("data-original=\"");
                    if (origIdx != -1) {
                        int start = origIdx + 15;
                        int end = block.indexOf("\"", start);
                        if (end != -1) imageUrl = block.substring(start, end);
                    }
                }
                
                String note = "HD";
                int badgeIdx = block.indexOf("class=\"card__badge\">");
                if (badgeIdx != -1) {
                    int start = badgeIdx + 20;
                    int end = block.indexOf("</span>", start);
                    if (end != -1) note = block.substring(start, end).trim().replaceAll("<[^>]*>", "");
                }
                
                String subtitle = "";
                int metaIdx = block.indexOf("class=\"card__meta\">");
                if (metaIdx != -1) {
                    int start = metaIdx + 19;
                    int end = block.indexOf("</p>", start);
                    if (end != -1) {
                        subtitle = block.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                    }
                }
                
                movies.add(new Movie(id, title, imageUrl, note, subtitle));
            }
            Log.d(TAG, "Grid parser successfully parsed " + movies.size() + " movies.");
            return movies;
        }

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
            // New layout fallback: search for id="desc" or class="desc-block"
            int descIdx = detailHtml.indexOf("id=\"desc\"");
            if (descIdx == -1) {
                descIdx = detailHtml.indexOf("class=\"desc-block\"");
            }
            if (descIdx != -1) {
                int divStart = detailHtml.indexOf("<div>", descIdx);
                if (divStart != -1) {
                    int start = divStart + 5;
                    int end = detailHtml.indexOf("</div>", start);
                    if (end != -1) {
                        synopsis = detailHtml.substring(start, end).trim();
                        synopsis = synopsis.replaceAll("<[^>]*>", "");
                    }
                }
            } else {
                Log.w(TAG, "Could not find synopsis marker 'class=\"details-content-all\"' or 'id=\"desc\"' in detail HTML.");
            }
        }

        String playPath = "";
        String epSuffix = extractBetween(detailHtml, "href=\"/ep/", "\"", 0);
        if (!epSuffix.isEmpty()) {
            playPath = "/ep/" + epSuffix;
        }
        if (playPath.isEmpty()) {
            Log.w(TAG, "Could not parse play path (href=\"/ep/\") in detail HTML.");
        } else {
            Log.d(TAG, "Parsed play path successfully: " + playPath);
        }

        String region = "";
        int regionIdx = detailHtml.indexOf("國家/地區：</span>");
        if (regionIdx == -1) {
            regionIdx = detailHtml.indexOf("地區：</span>");
        }
        if (regionIdx != -1) {
            int start = detailHtml.indexOf("</span>", regionIdx);
            if (start != -1) {
                start += 7;
                int end = detailHtml.indexOf("</li>", start);
                if (end != -1) {
                    region = detailHtml.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                }
            }
        }
        if (region.isEmpty()) {
            int langIdx = detailHtml.indexOf("\"inLanguage\":\"");
            if (langIdx == -1) {
                langIdx = detailHtml.indexOf("\"inLanguage\": \"");
            }
            if (langIdx != -1) {
                int start = detailHtml.indexOf(":", langIdx);
                if (start != -1) {
                    start = detailHtml.indexOf("\"", start);
                    if (start != -1) {
                        start += 1;
                        int end = detailHtml.indexOf("\"", start);
                        if (end != -1) {
                            String lang = detailHtml.substring(start, end).trim();
                            if ("漢語普通話".equals(lang) || "國語".equals(lang)) {
                                region = "華語";
                            } else if ("粵語".equals(lang)) {
                                region = "香港";
                            } else if ("日語".equals(lang)) {
                                region = "日本";
                            } else if ("韓語".equals(lang)) {
                                region = "韓國";
                            } else if ("泰語".equals(lang)) {
                                region = "泰國";
                            } else if ("英語".equals(lang)) {
                                region = "歐美";
                            } else {
                                region = lang;
                            }
                        }
                    }
                }
            }
        }

        String year = "";
        int yearIdx = detailHtml.indexOf("年代：</span>");
        if (yearIdx != -1) {
            int start = detailHtml.indexOf("</span>", yearIdx);
            if (start != -1) {
                start += 7;
                int end = detailHtml.indexOf("</li>", start);
                if (end != -1) {
                    String rawYear = detailHtml.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(20\\d{2}|19\\d{2})\\b");
                    java.util.regex.Matcher m = p.matcher(rawYear);
                    if (m.find()) {
                        year = m.group(1);
                    } else {
                        year = rawYear;
                    }
                }
            }
        }
        if (year.isEmpty()) {
            int dateIdx = detailHtml.indexOf("\"datePublished\":\"");
            if (dateIdx == -1) {
                dateIdx = detailHtml.indexOf("\"datePublished\": \"");
            }
            if (dateIdx != -1) {
                int start = detailHtml.indexOf(":", dateIdx);
                if (start != -1) {
                    start = detailHtml.indexOf("\"", start);
                    if (start != -1) {
                        start += 1;
                        int end = detailHtml.indexOf("\"", start);
                        if (end != -1 && end - start >= 4) {
                            year = detailHtml.substring(start, start + 4);
                        }
                    }
                }
            }
        }

        String category = "";

        int breadcrumbIdx = detailHtml.indexOf("\"BreadcrumbList\"");
        if (breadcrumbIdx != -1) {
            int startJson = detailHtml.lastIndexOf("<script", breadcrumbIdx);
            int endJson = detailHtml.indexOf("</script>", breadcrumbIdx);
            if (startJson != -1 && endJson != -1 && endJson > startJson) {
                String jsonLd = detailHtml.substring(startJson, endJson);
                
                ArrayList<String> names = new ArrayList<>();
                int searchStart = 0;
                while (true) {
                    int nameIdx = jsonLd.indexOf("\"name\":\"", searchStart);
                    if (nameIdx == -1) {
                        nameIdx = jsonLd.indexOf("\"name\": \"", searchStart);
                    }
                    if (nameIdx == -1) break;
                    
                    int valStart = jsonLd.indexOf("\"", nameIdx + 7);
                    if (valStart != -1) {
                        valStart += 1;
                        int valEnd = jsonLd.indexOf("\"", valStart);
                        if (valEnd != -1) {
                            String name = jsonLd.substring(valStart, valEnd).trim();
                            names.add(name);
                            searchStart = valEnd + 1;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                
                String parentCat = "";
                String subCat = "";
                
                if (names.size() >= 4) {
                    parentCat = names.get(1);
                    subCat = names.get(2);
                } else if (names.size() == 3) {
                    parentCat = names.get(1);
                    subCat = names.get(1);
                }
                
                if (!subCat.isEmpty()) {
                    category = subCat;
                    
                    if (region.isEmpty()) {
                        if ("韓劇".equals(subCat)) {
                            region = "韓國";
                        } else if ("日劇".equals(subCat)) {
                            region = "日本";
                        } else if ("美劇".equals(subCat) || "英劇".equals(subCat) || "歐美劇".equals(subCat)) {
                            region = "歐美";
                        } else if ("陸劇".equals(subCat) || "中劇".equals(subCat)) {
                            region = "大陸";
                        } else if ("台劇".equals(subCat)) {
                            region = "台灣";
                        } else if ("港劇".equals(subCat)) {
                            region = "香港";
                        } else if ("泰劇".equals(subCat)) {
                            region = "泰國";
                        } else if ("動漫".equals(subCat) || "日漫".equals(subCat)) {
                            region = "日本";
                        }
                    }
                }
            }
        }

        String actors = "";
        int actIdx = detailHtml.indexOf("演員:");
        if (actIdx == -1) {
            actIdx = detailHtml.indexOf("演員：");
        }
        if (actIdx != -1) {
            int start = detailHtml.indexOf("</strong>", actIdx);
            if (start == -1) {
                start = detailHtml.indexOf(">", actIdx);
            }
            if (start != -1) {
                if (detailHtml.charAt(start) == '>') {
                    start += 1;
                } else {
                    start += 9;
                }
                int end = detailHtml.indexOf("</p>", start);
                if (end != -1) {
                    actors = detailHtml.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                }
            }
        }

        String director = "";
        int dirIdx = detailHtml.indexOf("導演:");
        if (dirIdx == -1) {
            dirIdx = detailHtml.indexOf("導演：");
        }
        if (dirIdx != -1) {
            int start = detailHtml.indexOf("</strong>", dirIdx);
            if (start == -1) {
                start = detailHtml.indexOf(">", dirIdx);
            }
            if (start != -1) {
                if (detailHtml.charAt(start) == '>') {
                    start += 1;
                } else {
                    start += 9;
                }
                int end = detailHtml.indexOf("</p>", start);
                if (end != -1) {
                    director = detailHtml.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
                }
            }
        }

        // Self-healing fallback heuristics for year and region
        if (year.isEmpty()) {
            String title = "";
            int titleStart = detailHtml.indexOf("<title>");
            int titleEnd = detailHtml.indexOf("</title>");
            if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
                String rawTitle = detailHtml.substring(titleStart + 7, titleEnd).trim();
                int suffixIdx = rawTitle.indexOf("線上看");
                if (suffixIdx != -1) {
                    title = rawTitle.substring(0, suffixIdx).trim();
                } else {
                    title = rawTitle;
                }
            }

            String imageUrl = "";
            int imgIdx = detailHtml.indexOf("og:image\"");
            if (imgIdx != -1) {
                int contentIdx = detailHtml.indexOf("content=\"", imgIdx);
                if (contentIdx != -1 && contentIdx < imgIdx + 100) {
                    int end = detailHtml.indexOf("\"", contentIdx + 9);
                    if (end != -1) imageUrl = detailHtml.substring(contentIdx + 9, end);
                }
            }

            String note = "";
            int noteIdx = detailHtml.indexOf("狀態：</span>");
            if (noteIdx == -1) {
                noteIdx = detailHtml.indexOf("更新：</span>");
            }
            if (noteIdx != -1) {
                int start = detailHtml.indexOf("</span>", noteIdx) + 7;
                int end = detailHtml.indexOf("</li>", start);
                if (end != -1) note = detailHtml.substring(start, end).replaceAll("<[^>]*>", "").trim();
            }

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
            if (!note.isEmpty()) {
                java.util.regex.Matcher m = p.matcher(note);
                if (m.find()) {
                    year = m.group(1);
                }
            }
            if (year.isEmpty() && !title.isEmpty()) {
                java.util.regex.Matcher m = p.matcher(title);
                if (m.find()) {
                    year = m.group(1);
                }
            }
            if (year.isEmpty() && !imageUrl.isEmpty() && imageUrl.contains("/upload/vod/")) {
                int idx = imageUrl.indexOf("/upload/vod/");
                if (idx != -1 && imageUrl.length() >= idx + 16) {
                    String part = imageUrl.substring(idx + 12, idx + 16);
                    if (part.matches("\\d{4}")) {
                        year = part;
                    }
                }
            }
        }

        if (region.isEmpty()) {
            if (!actors.isEmpty()) {
                String firstPart = actors.substring(0, Math.min(actors.length(), 15));
                if (firstPart.contains("·") || firstPart.contains("•") || firstPart.matches(".*[a-zA-Z].*")) {
                    region = "歐美";
                }
            }
        }

        return new String[]{synopsis, playPath, region, year, actors, director, category};
    }

    public static ArrayList<Movie> parseRecommendations(String detailHtml) {
        ArrayList<Movie> list = new ArrayList<>();
        int idx = detailHtml.indexOf("swiper-wrapper");
        if (idx != -1) {
            String sliderHtml = detailHtml.substring(idx);
            int endIdx = sliderHtml.indexOf("</ul>");
            if (endIdx != -1) {
                sliderHtml = sliderHtml.substring(0, endIdx);
            }
            
            String[] slides = sliderHtml.split("<li");
            for (int i = 1; i < slides.length; i++) {
                String slide = slides[i];
                
                String id = "";
                int hrefIdx = slide.indexOf("href=\"/vod/");
                if (hrefIdx != -1) {
                    int start = hrefIdx + 11;
                    int end = slide.indexOf(".html\"", start);
                    if (end != -1) id = slide.substring(start, end);
                }
                if (id.isEmpty()) continue;
                
                String title = "";
                int titleIdx = slide.indexOf("title=\"");
                if (titleIdx != -1) {
                    int start = titleIdx + 7;
                    int end = slide.indexOf("\"", start);
                    if (end != -1) title = slide.substring(start, end);
                }
                
                String imageUrl = "";
                int imgIdx = slide.indexOf("data-background=\"");
                if (imgIdx == -1) {
                    imgIdx = slide.indexOf("data-original=\"");
                }
                if (imgIdx != -1) {
                    int start = imgIdx + 17;
                    if (slide.indexOf("data-original=\"") == imgIdx) start = imgIdx + 15;
                    int end = slide.indexOf("\"", start);
                    if (end != -1) imageUrl = slide.substring(start, end);
                } else {
                    int styleIdx = slide.indexOf("url('");
                    if (styleIdx != -1) {
                        int start = styleIdx + 5;
                        int end = slide.indexOf("'", start);
                        if (end != -1) imageUrl = slide.substring(start, end);
                    }
                }
                
                String note = "HD";
                int noteIdx = slide.indexOf("class=\"note");
                if (noteIdx != -1) {
                    int start = slide.indexOf(">", noteIdx) + 1;
                    int end = slide.indexOf("</span>", start);
                    if (end != -1) note = slide.substring(start, end).trim().replaceAll("<[^>]*>", "");
                }
                
                String subtitle = "";
                int subIdx = slide.indexOf("class=\"subtitle");
                if (subIdx != -1) {
                    int start = slide.indexOf(">", subIdx) + 1;
                    int end = slide.indexOf("</div>", start);
                    if (end != -1) subtitle = slide.substring(start, end).replace("&nbsp;", "").trim();
                }
                
                list.add(new Movie(id, title, imageUrl, note, subtitle));
            }
        }
        return list;
    }

    public static String parseM3U8Url(String playHtml) {
        String m3u8Url = "";
        String pdJson = extractBetween(playHtml, "var player_data=", "</script>", 0).trim();
        if (!pdJson.isEmpty()) {
            m3u8Url = extractBetween(pdJson, "\"url\":\"", "\"", 0);
            m3u8Url = m3u8Url.replace("\\/", "/");
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

    public static Movie parseMovieFromDetailPage(String id, String html) {
        String title = "";
        
        // 1. Try <title> tag first - extremely standard and reliable!
        int titleStart = html.indexOf("<title>");
        int titleEnd = html.indexOf("</title>");
        if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
            String rawTitle = html.substring(titleStart + 7, titleEnd).trim();
            int suffixIdx = rawTitle.indexOf("線上看");
            if (suffixIdx != -1) {
                title = rawTitle.substring(0, suffixIdx).trim();
            } else {
                title = rawTitle;
            }
        }

        // 2. Try <h1 class="text-overflow"> - Gimy's direct movie title header
        if (title.isEmpty()) {
            int h1Start = html.indexOf("<h1 class=\"text-overflow\">");
            if (h1Start != -1) {
                int h1End = html.indexOf("</h1>", h1Start);
                if (h1End != -1) {
                    title = html.substring(h1Start + 26, h1End).replaceAll("<[^>]*>", "").trim();
                }
            }
        }

        // 3. Try robust og:title parsing
        if (title.isEmpty()) {
            int ogIdx = html.indexOf("og:title\"");
            if (ogIdx != -1) {
                int contentIdx = html.indexOf("content=\"", ogIdx);
                if (contentIdx != -1 && contentIdx < ogIdx + 100) {
                    int end = html.indexOf("\"", contentIdx + 9);
                    if (end != -1) {
                        String rawTitle = html.substring(contentIdx + 9, end);
                        int suffixIdx = rawTitle.indexOf("線上看");
                        if (suffixIdx != -1) {
                            title = rawTitle.substring(0, suffixIdx).trim();
                        } else {
                            title = rawTitle;
                        }
                    }
                }
            }
        }

        // 4. Fallback placeholder
        if (title.isEmpty()) {
            title = "收藏影片 #" + id;
        }

        String imageUrl = "";
        int imgIdx = html.indexOf("og:image\"");
        if (imgIdx != -1) {
            int contentIdx = html.indexOf("content=\"", imgIdx);
            if (contentIdx != -1 && contentIdx < imgIdx + 100) {
                int end = html.indexOf("\"", contentIdx + 9);
                if (end != -1) imageUrl = html.substring(contentIdx + 9, end);
            }
        }
        if (imageUrl.isEmpty()) {
            imgIdx = html.indexOf("data-original=\"");
            if (imgIdx != -1) {
                int start = imgIdx + 15;
                int end = html.indexOf("\"", start);
                if (end != -1) imageUrl = html.substring(start, end);
            }
        }

        String note = "HD";
        int noteIdx = html.indexOf("狀態：</span>");
        if (noteIdx == -1) {
            noteIdx = html.indexOf("更新：</span>");
        }
        if (noteIdx != -1) {
            int start = html.indexOf("</span>", noteIdx) + 7;
            int end = html.indexOf("</li>", start);
            if (end != -1) note = html.substring(start, end).replaceAll("<[^>]*>", "").trim();
        }

        String subtitle = "";
        int subIdx = html.indexOf("主演：</span>");
        if (subIdx != -1) {
            int start = html.indexOf("</span>", subIdx) + 7;
            int end = html.indexOf("</li>", start);
            if (end != -1) subtitle = html.substring(start, end).replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
        }

        return new Movie(id, title, imageUrl, note, subtitle);
    }
}
