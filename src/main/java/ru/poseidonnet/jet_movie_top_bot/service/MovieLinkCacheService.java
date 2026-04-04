package ru.poseidonnet.jet_movie_top_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.api.MovieFeignClient;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static ru.poseidonnet.jet_movie_top_bot.kinopoisk.api.MovieFeignClient.DAFAULT_FIELDS;

@Slf4j
@RequiredArgsConstructor
@Service
public class MovieLinkCacheService {

    private final Cache<Integer, KinopoiskResponse.Movie> cache = new Cache2kBuilder<Integer, KinopoiskResponse.Movie>() {
    }
            .expireAfterWrite(5, TimeUnit.HOURS)
            .build();
    private final MovieFeignClient movieFeignClient;
    @Value("${kinopoisk.token}")
    private String apiKey;

    public Map<Integer, KinopoiskResponse.Movie> getByIds(List<Integer> ids) {
        log.info("Requested details for {} movie IDs", ids.size());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Integer> newIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> !cache.containsKey(id))
                .distinct()
                .toList();

//        List<Integer> newIds = ids.stream().filter(v -> !cache.containsKey(v)).toList();

        if (!newIds.isEmpty()) {
            int batchSize = 200;

            for (int i = 0; i < newIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, newIds.size());
                List<Integer> batch = newIds.subList(i, end);

                try {
                    log.info("Fetching batch from Kinopoisk: {} to {} (size: {})", i, end, batch.size());

                    KinopoiskResponse response = movieFeignClient.findByIds(apiKey, DAFAULT_FIELDS, batch.size(), batch);

                    if (response != null && response.getMovies() != null) {
                        response.getMovies().stream()
                                .filter(Objects::nonNull)
                                .forEach(m -> cache.put(m.getId(), m));
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch batch {} - {}: {}", i, end, e.getMessage());
                }
            }
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> {
                    boolean exists = cache.containsKey(id);
                    if (!exists) {
                        log.debug("Movie ID {} not found in Cache/API after all attempts", id);
                    }
                    return exists;
                })
                .collect(Collectors.toMap(
                        id -> id,
                        cache::get,
                        (existing, replacement) -> existing
                ));

    }


}
