package ru.poseidonnet.jet_movie_top_bot.command;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.api.MovieFeignClient;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;
import ru.poseidonnet.jet_movie_top_bot.service.WaitArgsService;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FindTest {
    private final MovieFeignClient api = mock(MovieFeignClient.class);
    private final DefaultAbsSender sender = mock(DefaultAbsSender.class);
    private final Update update = mock(Update.class);
    private final Find command = new Find(mock(WaitArgsService.class), api);

    @BeforeEach
    void setUp() {
        Message message = mock(Message.class);
        User user = mock(User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(42L);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
    }

    @Test
    void successfulSearchSendsMovieButton() throws Exception {
        KinopoiskResponse.Movie movie = new KinopoiskResponse.Movie();
        movie.setId(123);
        movie.setName("Фильм");
        movie.setYear(2024);
        KinopoiskResponse response = new KinopoiskResponse();
        response.setTotal(1);
        response.setMovies(List.of(movie));
        when(api.findByName(isNull(), anyList(), eq("Фильм"))).thenReturn(response);

        command.process(sender, update, "Фильм");

        SendMessage sent = sentMessage();
        assertThat(sent.getChatId()).isEqualTo("42");
        assertThat(sent.getText()).isEqualTo("Найдены следующие фильмы");
        var keyboard = (InlineKeyboardMarkup) sent.getReplyMarkup();
        var button = keyboard.getKeyboard().get(0).get(0);
        assertThat(button.getText()).isEqualTo("1) Фильм (2024)");
        assertThat(button.getCallbackData()).isEqualTo("/addMovie 123;7");
    }

    @Test
    void emptySearchStillSendsNoResults() throws Exception {
        when(api.findByName(isNull(), anyList(), anyString())).thenReturn(new KinopoiskResponse());
        command.process(sender, update, "Нет такого фильма");
        assertThat(sentMessage().getText()).isEqualTo("Ничего не найдено");
    }

    @Test
    void exhaustedDnsFailureSendsUnavailableMessage() throws Exception {
        Request request = request();
        when(api.findByName(isNull(), anyList(), anyString())).thenThrow(new RetryableException(
                -1, "DNS failure", Request.HttpMethod.GET,
                new UnknownHostException("api.poiskkino.dev"), null, request));
        assertUnavailableMessage();
    }

    @Test
    void unauthorizedApiResponseAlsoSendsUnavailableMessage() throws Exception {
        Response response = Response.builder().status(401).reason("Unauthorized")
                .headers(Map.of()).request(request()).build();
        when(api.findByName(isNull(), anyList(), anyString()))
                .thenThrow(FeignException.errorStatus("findByName", response));
        assertUnavailableMessage();
    }

    private void assertUnavailableMessage() throws Exception {
        command.process(sender, update, "Фильм");
        SendMessage sent = sentMessage();
        assertThat(sent.getChatId()).isEqualTo("42");
        assertThat(sent.getText()).isEqualTo("Сервис поиска временно недоступен, попробуйте позже.");
        assertThat(sent.getReplyMarkup()).isNull();
        verify(api).findByName(isNull(), anyList(), eq("Фильм"));
    }

    private SendMessage sentMessage() throws Exception {
        ArgumentCaptor<SendMessage> message = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender).execute(message.capture());
        verifyNoMoreInteractions(sender);
        return message.getValue();
    }

    private Request request() {
        return Request.create(Request.HttpMethod.GET, "https://api.poiskkino.dev/v1.3/movie",
                Map.of(), (byte[]) null, null, null);
    }
}
