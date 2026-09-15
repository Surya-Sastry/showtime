package com.showtime.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.showtime.catalog.domain.Movie;
import com.showtime.catalog.domain.Screen;
import com.showtime.catalog.domain.SeatDefinition;
import com.showtime.catalog.domain.Show;
import com.showtime.catalog.domain.Theater;
import com.showtime.catalog.repository.MovieRepository;
import com.showtime.catalog.repository.ScreenRepository;
import com.showtime.catalog.repository.SeatDefinitionRepository;
import com.showtime.catalog.repository.ShowRepository;
import com.showtime.catalog.repository.TheaterRepository;
import com.showtime.catalog.web.dto.CreateShowRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    private static final String SIGNING_KEY = "integration-test-signing-key-32-bytes-minimum!!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("catalog")
            .withUsername("catalog")
            .withPassword("catalog-test-password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("showtime.jwt.signing-key", () -> SIGNING_KEY);
        registry.add("grpc.server.port", () -> 0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TheaterRepository theaterRepository;

    @Autowired
    private ScreenRepository screenRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private SeatDefinitionRepository seatDefinitionRepository;

    @Autowired
    private ShowRepository showRepository;

    private Movie movie;
    private Screen screen;

    @BeforeEach
    void seedCatalog() {
        Theater theater = theaterRepository.save(new Theater("Downtown Cineplex"));
        screen = screenRepository.save(new Screen(theater, "Screen 1"));
        seatDefinitionRepository.save(new SeatDefinition(screen, "A1"));
        seatDefinitionRepository.save(new SeatDefinition(screen, "A2"));
        movie = movieRepository.save(new Movie("A Test Movie", 118));
    }

    @Test
    void browsingMoviesReturnsSeededMovie() throws Exception {
        mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("A Test Movie"));
    }

    @Test
    void creatingAShowThenBrowsingItByDateAndFetchingItsSeatMapWorks() throws Exception {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(7200);
        CreateShowRequest request = new CreateShowRequest(movie.getId(), screen.getId(), startsAt, endsAt, 1200);

        String managerToken = managerToken();

        String createResponse = mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String showId = objectMapper.readTree(createResponse).get("id").asText();

        LocalDate showDate = startsAt.atZone(ZoneOffset.UTC).toLocalDate();
        mockMvc.perform(get("/movies/" + movie.getId() + "/shows").param("date", showDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(showId));

        mockMvc.perform(get("/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void creatingAnOverlappingShowOnTheSameScreenIsRejected() throws Exception {
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(7200);
        showRepository.save(new Show(movie, screen, startsAt, endsAt, 1000));

        CreateShowRequest overlapping = new CreateShowRequest(
                movie.getId(), screen.getId(), startsAt.plusSeconds(600), endsAt.plusSeconds(600), 1200);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer " + managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapping)))
                .andExpect(status().isConflict());
    }

    @Test
    void creatingAShowWithoutTheManagerRoleIsForbidden() throws Exception {
        Instant startsAt = Instant.now().plusSeconds(3600);
        CreateShowRequest request = new CreateShowRequest(
                movie.getId(), screen.getId(), startsAt, startsAt.plusSeconds(7200), 1200);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private String managerToken() {
        return token("THEATER_MANAGER");
    }

    private String customerToken() {
        return token("CUSTOMER");
    }

    private String token(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SIGNING_KEY.getBytes());
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(java.util.UUID.randomUUID().toString())
                .claim("role", role)
                .issuer("showtime-identity")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
