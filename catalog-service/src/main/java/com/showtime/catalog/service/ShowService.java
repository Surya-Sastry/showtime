package com.showtime.catalog.service;

import com.showtime.catalog.domain.Movie;
import com.showtime.catalog.domain.Screen;
import com.showtime.catalog.domain.Show;
import com.showtime.catalog.exception.MovieNotFoundException;
import com.showtime.catalog.exception.ScheduleConflictException;
import com.showtime.catalog.exception.ScreenNotFoundException;
import com.showtime.catalog.exception.ShowNotFoundException;
import com.showtime.catalog.repository.MovieRepository;
import com.showtime.catalog.repository.ScreenRepository;
import com.showtime.catalog.repository.ShowRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;

    public ShowService(ShowRepository showRepository, MovieRepository movieRepository, ScreenRepository screenRepository) {
        this.showRepository = showRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
    }

    public List<Show> findByMovieAndDate(UUID movieId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = dayStart.plusSeconds(24 * 60 * 60);
        return showRepository.findByMovieIdAndDate(movieId, dayStart, dayEnd);
    }

    public Show getOrThrow(UUID showId) {
        return showRepository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    @Transactional
    public Show create(UUID movieId, UUID screenId, Instant startsAt, Instant endsAt, int ticketPriceCents) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        Screen screen = screenRepository.findWithTheaterById(screenId)
                .orElseThrow(() -> new ScreenNotFoundException(screenId));

        Show show = new Show(movie, screen, startsAt, endsAt, ticketPriceCents);
        try {
            // saveAndFlush forces the INSERT (and therefore the exclusion
            // constraint check) to run now, inside this try block. A plain
            // save() only schedules the insert; it wouldn't actually hit the
            // database until the transaction commits after this method
            // returns, by which point this catch could no longer help.
            return showRepository.saveAndFlush(show);
        } catch (DataIntegrityViolationException ex) {
            // The EXCLUDE USING gist constraint is the actual authority here;
            // this catch only turns its rejection into a clean 409 instead of
            // a raw SQL error leaking to the client.
            throw new ScheduleConflictException();
        }
    }
}
