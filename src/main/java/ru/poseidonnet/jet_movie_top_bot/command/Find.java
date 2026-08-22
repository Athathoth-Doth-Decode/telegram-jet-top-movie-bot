package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.api.MovieFeignClient;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;
import ru.poseidonnet.jet_movie_top_bot.service.MovieLinkCacheService;
import ru.poseidonnet.jet_movie_top_bot.service.WaitArgsService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class Find implements Command {

    @Value("${kinopoisk.token}")
    private String apiKey;
    private final WaitArgsService waitArgsService;
    private final MovieFeignClient movieFeignClient;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) throws Exception {

        if (commandArgs == null || commandArgs.isBlank()) {
            sendMessage(sender, update, "Введите название фильма");
            commandArgs = waitArgsService.waitForArgs(update.getMessage().getFrom().getId(), 60);
        }

        KinopoiskResponse byName = movieFeignClient.findByName(apiKey, MovieFeignClient.DAFAULT_FIELDS, commandArgs);
        log.debug("Kinopoisk search result: {}", byName);
        if (byName.getTotal() == 0) {
            sendMessage(sender, update, "Ничего не найдено");
            return;
        }
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId());
        List<KinopoiskResponse.Movie> movies = byName.getMovies().stream().limit(10).toList();

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (int i = 0 ; i < movies.size(); i++) {
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            KinopoiskResponse.Movie movie = movies.get(i);
            inlineKeyboardButton.setText((i + 1) + ") "+ movie.getName() + " (" + movie.getYear() + ")");
            inlineKeyboardButton.setCallbackData("/addMovie " + movie.getId() + ";" + update.getMessage().getFrom().getId());
            buttons.add(List.of(inlineKeyboardButton));
        }
        inlineKeyboardMarkup.setKeyboard(buttons);
        sendMessage.setText("Найдены следующие фильмы");
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        sender.execute(sendMessage);
    }

    @Override
    public String commandType() {
        return "find";
    }

}
