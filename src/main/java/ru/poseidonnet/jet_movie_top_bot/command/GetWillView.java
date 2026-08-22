package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;
import ru.poseidonnet.jet_movie_top_bot.service.MovieLinkCacheService;
import ru.poseidonnet.jet_movie_top_bot.service.PollsContainerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class GetWillView implements Command {

    private final PollsContainerService pollsContainerService;

    private final MovieLinkCacheService movieLinkCacheService;
    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) throws Exception {
        Long userId = update.getMessage().getFrom().getId();
        List<Integer> willView = new ArrayList<>(pollsContainerService.getWillView(userId));
        if (willView.isEmpty()) {
            sendMessage(sender, update, "Вы не добавили ни одного фильма.");
            return;
        }
        Map<Integer, KinopoiskResponse.Movie> links = movieLinkCacheService.getByIds(willView);
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (int i = 0 ; i < willView.size(); i++) {
            int movieId = willView.get(i);
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            KinopoiskResponse.Movie movie = links.get(movieId);
            inlineKeyboardButton.setText((i + 1) + ") "+ movie.getName() + " (" + movie.getYear() + ")");
            inlineKeyboardButton.setCallbackData("/addMovie " + movie.getId() + ";" + update.getMessage().getFrom().getId());
            buttons.add(List.of(inlineKeyboardButton));
        }
        sendButtons(sender, update, "Ваши избранные фильмы:\n", buttons);
    }

    @Override
    public String commandType() {
        return "getwillview";
    }
}
