package com.showtime.catalog.web;

import com.showtime.catalog.repository.MovieRepository;
import com.showtime.catalog.service.ShowService;
import com.showtime.catalog.web.dto.MovieResponse;
import com.showtime.catalog.web.dto.ShowResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MovieController {

    private final MovieRepository movieRepository;
    private final ShowService showService;

    public MovieController(MovieRepository movieRepository, ShowService showService) {
        this.movieRepository = movieRepository;
        this.showService = showService;
    }

    @GetMapping("/movies")
    public Page<MovieResponse> browseMovies(Pageable pageable) {
        return movieRepository.findAllBy(pageable).map(MovieResponse::from);
    }

    @GetMapping("/movies/{movieId}/shows")
    public List<ShowResponse> showsForMovie(
            @PathVariable("movieId") UUID movieId,
            @RequestParam("date") LocalDate date) {
        return showService.findByMovieAndDate(movieId, date).stream().map(ShowResponse::from).toList();
    }
}
