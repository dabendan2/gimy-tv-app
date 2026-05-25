package com.gimytv.horror;

import java.util.ArrayList;

public class GimyParserTest {

    public static void runTests() {
        System.out.println("Executing GimyParser Unit Tests...");

        testParseMoviesFromHtml();
        testParseMovieDetails();
        testParseM3U8Url();

        System.out.println("  [PASS] All GimyParser tests passed successfully!");
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
            "</html>";

        String[] results = GimyParser.parseMovieDetails(mockDetailHtml);
        
        if (results == null || results.length != 2) throw new AssertionError("Expected results of size 2");
        if (!"這是一部極度驚悚、讓人毛骨悚然的鬼片劇情介紹。".equals(results[0])) {
            throw new AssertionError("Synopsis parsed incorrectly: " + results[0]);
        }
        if (!"/ep/play-1-1".equals(results[1])) {
            throw new AssertionError("Play path parsed incorrectly: " + results[1]);
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
}
