package com.gimytv.horror;

import java.util.ArrayList;
import java.util.Map;

public class MovieSorter {

    /**
     * Sorts the movie list, putting watchlist movies (state == 1 / 📝) at the very front
     * of the list, while keeping the relative order of other movies intact.
     *
     * @param movies The raw list of movies to sort.
     * @param listStates A map of movie ID to its playlist state (1 for watchlist).
     * @return A sorted list of movies.
     */
    public static ArrayList<Movie> sortMovies(ArrayList<Movie> movies, Map<String, Integer> listStates) {
        if (movies == null) {
            return new ArrayList<>();
        }
        ArrayList<Movie> sortedMovies = new ArrayList<>();
        ArrayList<Movie> normalMovies = new ArrayList<>();

        for (Movie m : movies) {
            if (m == null) continue;
            Integer state = listStates != null ? listStates.get(m.id) : null;
            if (state != null && state == 1) { // 待播清單 (📝)
                sortedMovies.add(m);
            } else {
                normalMovies.add(m);
            }
        }
        sortedMovies.addAll(normalMovies);
        return sortedMovies;
    }
}
