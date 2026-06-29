package com.gimytv.horror;

import java.util.ArrayList;

public class GimyParserTest {

    public static void runTests() {
        System.out.println("Executing GimyParser Unit Tests...");

        testParseMoviesFromHtml();
        testParseMoviesFromHtmlNewLayout();
        testParseMovieDetails();
        testParseMovieDetailsNewLayout();
        testParseM3U8Url();

        // New tests with actual webpage samples
        System.out.println("Executing Real Webpage Sample Extraction Unit Tests...");
        testActualSearchPageSample();
        testActualMovieDetailPageSample();
        testActualMoviePlayPageSample();
        testActualMovieListPageSample();
        testNewLayoutMovieDetailMissingMetadata();
        testKoreanDramaDetailFallback();
        test10MoviesIntegrationSuite();

        System.out.println("  [PASS] All GimyParser tests passed successfully!");
    }

    private static String readTestFile(String filename) {
        try {
            java.io.File file = new java.io.File("app/src/test/resources/test_samples/" + filename);
            if (!file.exists()) {
                file = new java.io.File("src/test/resources/test_samples/" + filename);
            }
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Failed to read test sample file: " + filename, e);
        }
    }

    private static void testActualSearchPageSample() {
        String html = readTestFile("search_results_sample.html");
        ArrayList<Movie> movies = GimyParser.parseMoviesFromHtml(html);

        if (movies == null) throw new AssertionError("Search movies list should not be null");
        if (movies.size() != 20) throw new AssertionError("Expected 20 movies from search sample, found: " + movies.size());

        // First movie: {"id": "432631", "title": "鬼女之家 鬼女の棲む家", "note": "更新至09集", "subtitle": "石田光"}
        Movie m1 = movies.get(0);
        if (!"432631".equals(m1.id)) throw new AssertionError("Expected search m1 ID '432631', got: " + m1.id);
        if (!"鬼女之家 鬼女の棲む家".equals(m1.title)) throw new AssertionError("Expected search m1 Title, got: " + m1.title);
        if (!"更新至09集".equals(m1.note)) throw new AssertionError("Expected search m1 Note, got: " + m1.note);
        if (!"石田光".equals(m1.subtitle)) throw new AssertionError("Expected search m1 Subtitle, got: " + m1.subtitle);

        // Third movie: {"id": "382489", "title": "魔鬼黑獄", "note": "正片"}
        Movie m3 = movies.get(2);
        if (!"382489".equals(m3.id)) throw new AssertionError("Expected search m3 ID '382489', got: " + m3.id);
        if (!"魔鬼黑獄".equals(m3.title)) throw new AssertionError("Expected search m3 Title '魔鬼黑獄', got: " + m3.title);
        if (!"正片".equals(m3.note)) throw new AssertionError("Expected search m3 Note '正片', got: " + m3.note);
    }

    private static void testActualMovieDetailPageSample() {
        String html = readTestFile("movie_detail_sample.html");
        String[] results = GimyParser.parseMovieDetails(html);

        if (results == null || results.length != 7) throw new AssertionError("Expected results of size 7");
        if (!"暫無簡介".equals(results[0])) {
            throw new AssertionError("Expected synopsis '暫無簡介' for rare movie without detail synopsis, got: " + results[0]);
        }
        if (!"/ep/382489-1-1.html".equals(results[1])) {
            throw new AssertionError("Expected play path '/ep/382489-1-1.html', got: " + results[1]);
        }
        if (!"歐美".equals(results[2])) {
            throw new AssertionError("Expected region '歐美', got: " + results[2]);
        }
        if (!"1993".equals(results[3])) {
            throw new AssertionError("Expected year '1993', got: " + results[3]);
        }
        if (!"布里吉特·尼爾森、保羅·科斯羅、金伯莉·凱茨、Kari Whitman、Jana Svandová、Lucie Benesová".equals(results[4])) {
            throw new AssertionError("Expected actors list, got: " + results[4]);
        }
        if (!"Lloyd A. Simandl".equals(results[5])) {
            throw new AssertionError("Expected director, got: " + results[5]);
        }
        if (!"劇情片".equals(results[6])) {
            throw new AssertionError("Expected category, got: " + results[6]);
        }
    }

    private static void testActualMoviePlayPageSample() {
        String html = readTestFile("movie_play_sample.html");
        String m3u8Url = GimyParser.parseM3U8Url(html);

        if (!"https://yzzy1.play-cdn16.com/20231101/25760_c9b17378/index.m3u8".equals(m3u8Url)) {
            throw new AssertionError("Expected real M3U8 streaming link, got: " + m3u8Url);
        }
    }

    private static void testActualMovieListPageSample() {
        String html = readTestFile("movie_list_sample.html");
        ArrayList<Movie> movies = GimyParser.parseMoviesFromHtml(html);

        if (movies == null) throw new AssertionError("Listing movies list should not be null");
        if (movies.size() != 36) throw new AssertionError("Expected 36 movies from genre listing sample, found: " + movies.size());

        // First movie: {"id": "256828", "title": "破墓", "note": "HD", "subtitle": "崔岷植,金高銀,柳海真,李到晛,全鎮基,洪瑞俊"}
        Movie m1 = movies.get(0);
        if (!"256828".equals(m1.id)) throw new AssertionError("Expected list m1 ID '256828', got: " + m1.id);
        if (!"破墓".equals(m1.title)) throw new AssertionError("Expected list m1 Title '破墓', got: " + m1.title);
        if (!"HD".equals(m1.note)) throw new AssertionError("Expected list m1 Note 'HD', got: " + m1.note);
        if (!"崔岷植,金高銀,柳海真,李到晛,全鎮基,洪瑞俊".equals(m1.subtitle)) throw new AssertionError("Expected list m1 Subtitle, got: " + m1.subtitle);
    }

    private static void testParseMoviesFromHtml() {
        String mockHtml = 
            "<html>" +
            "<body>" +
            "  <div class=\"box-video-list\">" +
            "    <ul>" +
            "      <li class=\"video-item\">" +
            "        <a href=\"/vod/12345.html\" title=\"破墓\">" +
            "          <img data-original=\"https://img.gimy.com/poster.jpg\" />" +
            "          <span class=\"note\">超清</span>" +
            "          <div class=\"subtitle\">張德、李華</div>" +
            "        </a>" +
            "      </li>" +
            "    </ul>" +
            "  </div>" +
            "</body>" +
            "</html>";

        ArrayList<Movie> movies = GimyParser.parseMoviesFromHtml(mockHtml);
        
        // Assertions
        if (movies == null) throw new AssertionError("Movies list should not be null");
        if (movies.size() != 1) throw new AssertionError("Expected 1 movie, found: " + movies.size());
        
        Movie m = movies.get(0);
        if (!"12345".equals(m.id)) throw new AssertionError("Expected ID '12345', got: " + m.id);
        if (!"破墓".equals(m.title)) throw new AssertionError("Expected Title '破墓', got: " + m.title);
        if (!"https://img.gimy.com/poster.jpg".equals(m.imageUrl)) throw new AssertionError("Expected ImageUrl, got: " + m.imageUrl);
        if (!"超清".equals(m.note)) throw new AssertionError("Expected Note '超清', got: " + m.note);
        if (!"張德、李華".equals(m.subtitle)) throw new AssertionError("Expected Subtitle, got: " + m.subtitle);
    }

    private static void testParseMovieDetails() {
        String mockDetailHtml = 
            "<html>" +
            "  <span class=\"details-content-all\">這是一部極度驚悚、讓人毛骨悚然的鬼片劇情介紹。</span>" +
            "  <a href=\"/ep/play-1-1\">第1集</a>" +
            "  <li><span>國家/地區：</span>台灣</li>" +
            "  <li><span>年代：</span>2023</li>" +
            "  <p><strong>演員:</strong>阿明、小華</p>" +
            "  <p><strong>導演:</strong>李導演</p>" +
            "</html>";

        String[] results = GimyParser.parseMovieDetails(mockDetailHtml);
        
        if (results == null || results.length != 7) throw new AssertionError("Expected results of size 7");
        if (!"這是一部極度驚悚、讓人毛骨悚然的鬼片劇情介紹。".equals(results[0])) {
            throw new AssertionError("Synopsis parsed incorrectly: " + results[0]);
        }
        if (!"/ep/play-1-1".equals(results[1])) {
            throw new AssertionError("Play path parsed incorrectly: " + results[1]);
        }
        if (!"台灣".equals(results[2])) {
            throw new AssertionError("Region parsed incorrectly: " + results[2]);
        }
        if (!"2023".equals(results[3])) {
            throw new AssertionError("Year parsed incorrectly: " + results[3]);
        }
        if (!"阿明、小華".equals(results[4])) {
            throw new AssertionError("Actors parsed incorrectly: " + results[4]);
        }
        if (!"李導演".equals(results[5])) {
            throw new AssertionError("Director parsed incorrectly: " + results[5]);
        }
        if (!"".equals(results[6])) {
            throw new AssertionError("Category parsed incorrectly: " + results[6]);
        }
    }

    private static void testParseMoviesFromHtmlNewLayout() {
        String mockHtml = 
            "<html>" +
            "<body>" +
            "  <div class=\"grid\">" +
            "    <article class=\"card\">" +
            "      <a href=\"/vod/67890.html\" class=\"card__thumb\" aria-label=\"新電影\">" +
            "        <img src=\"https://img.gimy.com/poster2.jpg\" />" +
            "        <span class=\"card__badge\">4K超清</span>" +
            "      </a>" +
            "      <a href=\"/vod/67890.html\" class=\"card__body\">" +
            "        <h3 class=\"card__title\">新電影</h3>" +
            "        <p class=\"card__meta\">主角A,主角B</p>" +
            "      </a>" +
            "    </article>" +
            "  </div>" +
            "</body>" +
            "</html>";

        ArrayList<Movie> movies = GimyParser.parseMoviesFromHtml(mockHtml);
        
        if (movies == null) throw new AssertionError("Movies list should not be null");
        if (movies.size() != 1) throw new AssertionError("Expected 1 movie, found: " + movies.size());
        
        Movie m = movies.get(0);
        if (!"67890".equals(m.id)) throw new AssertionError("Expected ID '67890', got: " + m.id);
        if (!"新電影".equals(m.title)) throw new AssertionError("Expected Title '新電影', got: " + m.title);
        if (!"https://img.gimy.com/poster2.jpg".equals(m.imageUrl)) throw new AssertionError("Expected ImageUrl, got: " + m.imageUrl);
        if (!"4K超清".equals(m.note)) throw new AssertionError("Expected Note '4K超清', got: " + m.note);
        if (!"主角A,主角B".equals(m.subtitle)) throw new AssertionError("Expected Subtitle, got: " + m.subtitle);
    }

    private static void testParseMovieDetailsNewLayout() {
        String mockDetailHtml = 
            "<html>" +
            "  <div class=\"desc-block\" id=\"desc\">" +
            "    <h2>劇情介紹</h2>" +
            "    <div>這是一個全新排版風格的影片劇情簡介內容。</div>" +
            "  </div>" +
            "  <a href=\"/ep/play-1-1\">第1集</a>" +
            "  <li><span>國家/地區：</span>韓國</li>" +
            "  <li><span>年代：</span>2024</li>" +
            "  <p><strong>演員：</strong>主角一、主角二</p>" +
            "  <p><strong>導演：</strong>總導演</p>" +
            "</html>";

        String[] results = GimyParser.parseMovieDetails(mockDetailHtml);
        
        if (results == null || results.length != 7) throw new AssertionError("Expected results of size 7");
        if (!"這是一個全新排版風格的影片劇情簡介內容。".equals(results[0])) {
            throw new AssertionError("New layout synopsis parsed incorrectly: " + results[0]);
        }
        if (!"/ep/play-1-1".equals(results[1])) {
            throw new AssertionError("New layout play path parsed incorrectly: " + results[1]);
        }
        if (!"韓國".equals(results[2])) {
            throw new AssertionError("New layout region parsed incorrectly: " + results[2]);
        }
        if (!"2024".equals(results[3])) {
            throw new AssertionError("New layout year parsed incorrectly: " + results[3]);
        }
        if (!"主角一、主角二".equals(results[4])) {
            throw new AssertionError("New layout actors parsed incorrectly: " + results[4]);
        }
        if (!"總導演".equals(results[5])) {
            throw new AssertionError("New layout director parsed incorrectly: " + results[5]);
        }
        if (!"".equals(results[6])) {
            throw new AssertionError("New layout category parsed incorrectly: " + results[6]);
        }
    }

    private static void testParseM3U8Url() {
        String mockPlayHtml = 
            "<html>" +
            "  <script>" +
            "    var player_data={\"url\":\"https:\\/\\/cdn.gimy.com\\/horror\\/index.m3u8\",\"url_next\":\"\"}" +
            "  </script>" +
            "</html>";

        String m3u8Url = GimyParser.parseM3U8Url(mockPlayHtml);
        if (!"https://cdn.gimy.com/horror/index.m3u8".equals(m3u8Url)) {
            throw new AssertionError("M3U8 URL parsed incorrectly: " + m3u8Url);
        }
    }

    private static void testNewLayoutMovieDetailMissingMetadata() {
        String html = readTestFile("test_detail_new_layout.html");
        String[] results = GimyParser.parseMovieDetails(html);

        if (results == null || results.length != 7) throw new AssertionError("Expected results of size 7");
        if (!"/ep/382489-1-1.html".equals(results[1])) {
            throw new AssertionError("Expected play path '/ep/382489-1-1.html', got: " + results[1]);
        }
        if (!"歐美".equals(results[2])) {
            throw new AssertionError("Expected heuristic region '歐美', got: " + results[2]);
        }
        if (!"2025".equals(results[3])) {
            throw new AssertionError("Expected heuristic year '2025', got: " + results[3]);
        }
        if (!"布里吉特·尼爾森、保羅·科斯羅、金伯莉·凱茨、Kari Whitman、Jana Svandová、Lucie Benesová".equals(results[4])) {
            throw new AssertionError("Expected actors list, got: " + results[4]);
        }
        if (!"Lloyd A. Simandl".equals(results[5])) {
            throw new AssertionError("Expected director, got: " + results[5]);
        }
        if (!"劇情片".equals(results[6])) {
            throw new AssertionError("Expected fallback category '劇情片', got: " + results[6]);
        }
    }

    private static void testKoreanDramaDetailFallback() {
        String html = readTestFile("test_detail_korean_drama.html");
        String[] results = GimyParser.parseMovieDetails(html);

        if (results == null || results.length != 7) throw new AssertionError("Expected results of size 7");
        if (!"韓國".equals(results[2])) {
            throw new AssertionError("Expected fallback region '韓國' for Korean drama, got: " + results[2]);
        }
        if (!"2026".equals(results[3])) {
            throw new AssertionError("Expected heuristic year '2026' for Korean drama, got: " + results[3]);
        }
        if (!"徐仁國、朴智賢、姜美娜、崔京勳、元圭彬、樸藝榮、金正英".equals(results[4])) {
            throw new AssertionError("Expected Korean drama actors list, got: " + results[4]);
        }
        if (!"趙恩呂".equals(results[5])) {
            throw new AssertionError("Expected Korean drama director, got: " + results[5]);
        }
        if (!"韓劇".equals(results[6])) {
            throw new AssertionError("Expected Korean drama category '韓劇', got: " + results[6]);
        }
    }

    private static void test10MoviesIntegrationSuite() {
        System.out.println("Executing 10 Movies Integration Verification Suite...");
        
        String[] ids = {
            "382489", "454690", "457135", "454773", "256828", 
            "444469", "443094", "454693", "454584", "443103"
        };
        
        String[] expectedCats = {
            "劇情片", "陸劇", "韓劇", "劇情片", "恐怖片",
            "恐怖片", "劇情片", "劇情片", "劇情片", "劇情片"
        };
        
        String[] expectedRegions = {
            "歐美", "大陸", "韓國", "", "",
            "", "", "歐美", "", ""
        };
        
        String[] expectedYears = {
            "2025", "2026", "2026", "2026", "2024",
            "2026", "1994", "2026", "2026", "2026"
        };
        
        String[] expectedPlayPaths = {
            "/ep/382489-1-1.html", "/ep/454690-1-1.html", "/ep/457135-1-1.html", "/ep/454773-1-1.html", "/ep/256828-1-1.html",
            "/ep/444469-1-1.html", "/ep/443094-1-1.html", "/ep/454693-1-1.html", "/ep/454584-1-1.html", "/ep/443103-1-1.html"
        };
        
        String[] expectedActorsStart = {
            "布里吉特·尼爾森", "李一桐、曾舜晞", "徐仁國、朴智賢", "張天愛、海清", "崔岷植、金高銀",
            "全智賢、具教煥", "吳彥祖、劉俊謙", "休·傑克曼、朱迪·科默", "陳善圭、孔明", "李思潼、王彥桐"
        };
        
        String[] expectedDirectors = {
            "Lloyd A. Simandl", "朱少傑", "趙恩呂", "翁子光", "張宰賢",
            "延尚昊", "梁樂民", "邁克爾·薩諾斯基", "樸奎泰", "藍鴻春"
        };

        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            String filename = "integration_test_samples/" + id + ".html";
            System.out.println("  Testing movie ID " + id + " (" + expectedCats[i] + ")...");
            
            String html = readTestFile(filename);
            String[] results = GimyParser.parseMovieDetails(html);
            
            if (results == null || results.length != 7) {
                throw new AssertionError("ID " + id + ": expected results size 7");
            }
            
            // 1. Play path assertion
            if (!expectedPlayPaths[i].equals(results[1])) {
                throw new AssertionError("ID " + id + ": expected play path '" + expectedPlayPaths[i] + "', got '" + results[1] + "'");
            }
            
            // 2. Category assertion
            if (!expectedCats[i].equals(results[6])) {
                throw new AssertionError("ID " + id + ": expected category '" + expectedCats[i] + "', got '" + results[6] + "'");
            }
            
            // 3. Mapped Region assertion
            if (!expectedRegions[i].equals(results[2])) {
                throw new AssertionError("ID " + id + ": expected mapped region '" + expectedRegions[i] + "', got '" + results[2] + "'");
            }
            
            // 3.5. Year assertion
            if (!expectedYears[i].equals(results[3])) {
                throw new AssertionError("ID " + id + ": expected year '" + expectedYears[i] + "', got '" + results[3] + "'");
            }
            
            // 4. Actors list prefix assertion
            if (!results[4].startsWith(expectedActorsStart[i])) {
                throw new AssertionError("ID " + id + ": expected actors starting with '" + expectedActorsStart[i] + "', got '" + results[4] + "'");
            }
            
            // 5. Director assertion
            if (!expectedDirectors[i].equals(results[5])) {
                throw new AssertionError("ID " + id + ": expected director '" + expectedDirectors[i] + "', got '" + results[5] + "'");
            }
            
            // 6. Synopsis should not be empty
            if (results[0] == null || results[0].isEmpty()) {
                throw new AssertionError("ID " + id + ": synopsis should not be empty");
            }
        }
        System.out.println("  [PASS] 10 Movies Integration Verification Suite passed successfully!");
    }
}
