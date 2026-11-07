package ru.poseidonnet.jet_movie_top_bot.kinopoisk.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;

import java.util.List;

@FeignClient(value = "kinopoisk-movie", url = "${kinopoisk.url}/v1.3/movie")
public interface MovieFeignClient {

    List<String> DAFAULT_FIELDS = List.of("id", "name", "year");

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    KinopoiskResponse findByName(@RequestHeader("X-API-Key") String apiKey,
                                 @RequestParam("selectFields") List<String> selectFields,
                                 @RequestParam("name") String name);

    @GetMapping
    KinopoiskResponse findByIds(@RequestHeader("X-API-Key") String apiKey,
                                 @RequestParam("selectFields") List<String> selectFields,
                                 @RequestParam("limit") int limit,
                                 @RequestParam("id") List<Integer> ids);


}
