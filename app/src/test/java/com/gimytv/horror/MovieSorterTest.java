package com.gimytv.horror;

import java.util.ArrayList;
import java.util.HashMap;

public class MovieSorterTest {

    public static void runTests() {
        System.out.println("Executing MovieSorter Unit Tests...");

        testMovieSorterStandard();
        testMovieSorterEmptyAndNull();
        testMovieSorterOrderPreservation();

        System.out.println("  [PASS] All MovieSorter tests passed successfully!");
    }

    private static void testMovieSorterStandard() {
        ArrayList<Movie> rawList = new ArrayList<>();
        rawList.add(new Movie("1", "Movie A", "img", "HD", ""));
        rawList.add(new Movie("2", "Movie B", "img", "HD", ""));
        rawList.add(new Movie("3", "Movie C", "img", "HD", ""));
        rawList.add(new Movie("4", "Movie D", "img", "HD", ""));

        HashMap<String, Integer> mockStates = new HashMap<>();
        mockStates.put("1", 0); // normal
        mockStates.put("2", 1); // watchlist (📝)
        mockStates.put("3", 0); // normal
        mockStates.put("4", 1); // watchlist (📝)

        ArrayList<Movie> sorted = MovieSorter.sortMovies(rawList, mockStates);

        if (sorted == null || sorted.size() != 4) {
            throw new AssertionError("Sorted list size must be 4");
        }

        // Expected sorted order: ID 2, ID 4, ID 1, ID 3
        if (!"2".equals(sorted.get(0).id)) throw new AssertionError("Expected 1st movie to be ID 2 (watchlist)");
        if (!"4".equals(sorted.get(1).id)) throw new AssertionError("Expected 2nd movie to be ID 4 (watchlist)");
        if (!"1".equals(sorted.get(2).id)) throw new AssertionError("Expected 3rd movie to be ID 1 (normal)");
        if (!"3".equals(sorted.get(3).id)) throw new AssertionError("Expected 4th movie to be ID 3 (normal)");
    }

    private static void testMovieSorterEmptyAndNull() {
        // Test null input
        ArrayList<Movie> sortedNull = MovieSorter.sortMovies(null, null);
        if (sortedNull == null || sortedNull.size() != 0) {
            throw new AssertionError("Null list sorting should return empty list");
        }

        // Test empty list
        ArrayList<Movie> empty = new ArrayList<>();
        ArrayList<Movie> sortedEmpty = MovieSorter.sortMovies(empty, null);
        if (sortedEmpty == null || sortedEmpty.size() != 0) {
            throw new AssertionError("Empty list sorting should return empty list");
        }
    }

    private static void testMovieSorterOrderPreservation() {
        ArrayList<Movie> rawList = new ArrayList<>();
        rawList.add(new Movie("A", "Movie A", "img", "HD", ""));
        rawList.add(new Movie("B", "Movie B", "img", "HD", ""));
        rawList.add(new Movie("C", "Movie C", "img", "HD", ""));

        // No movies in watchlist
        HashMap<String, Integer> mockStates = new HashMap<>();
        mockStates.put("A", 2); // liked
        mockStates.put("B", 0); // normal
        mockStates.put("C", 3); // disliked

        ArrayList<Movie> sorted = MovieSorter.sortMovies(rawList, mockStates);

        // Order should be completely unchanged
        if (!"A".equals(sorted.get(0).id)) throw new AssertionError("Order not preserved for A");
        if (!"B".equals(sorted.get(1).id)) throw new AssertionError("Order not preserved for B");
        if (!"C".equals(sorted.get(2).id)) throw new AssertionError("Order not preserved for C");
    }
}
