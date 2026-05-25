package com.gimytv.horror;

public class Movie {
    public String id;
    public String title;
    public String imageUrl;
    public String note;
    public String subtitle;

    public Movie(String id, String title, String imageUrl, String note, String subtitle) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.note = note;
        this.subtitle = subtitle;
    }
}
