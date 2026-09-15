package com.showtime.catalog.web.dto;

import com.showtime.catalog.domain.Movie;
import java.util.UUID;

public record MovieResponse(UUID id, String title, int runtimeMinutes) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getRuntimeMinutes());
    }
}
