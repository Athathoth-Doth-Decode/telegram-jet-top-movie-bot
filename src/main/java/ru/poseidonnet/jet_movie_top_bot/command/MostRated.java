package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.kinopoisk.model.KinopoiskResponse;
import ru.poseidonnet.jet_movie_top_bot.service.MovieLinkCacheService;
import ru.poseidonnet.jet_movie_top_bot.service.PollsContainerService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class MostRated implements Command {

    private final PollsContainerService pollsContainerService;
    private final MovieLinkCacheService movieLinkCacheService;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) {
        Map<Integer, Map<Long, Integer>> polls = pollsContainerService.getPolls();
        List<Integer> mostRatedList = new ArrayList<>(polls.keySet().stream().toList());
        mostRatedList.sort(Comparator.comparingInt(o -> -polls.get(o).size()));
        mostRatedList = mostRatedList.stream().limit(10).toList();
        Map<Integer, KinopoiskResponse.Movie> links = movieLinkCacheService.getByIds(mostRatedList);
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (int i = 0; i < mostRatedList.size(); i++) {
            Integer movieId = mostRatedList.get(i);
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            KinopoiskResponse.Movie movie = links.get(movieId);
            inlineKeyboardButton.setText((i + 1) + ") " +
                    movie.getName() +
                    " (" + movie.getYear() + ")" +
                    "\nПроголосовало - " + polls.get(movieId).size()
            );
            inlineKeyboardButton.setCallbackData("/addMovie " + movie.getId() + ";" + update.getMessage().getFrom().getId());
            buttons.add(List.of(inlineKeyboardButton));
        }
        sendButtons(sender, update, "Топ10 наиболее оцениваемых фильмов\n", buttons);
    }

    @Override
    public String commandType() {
        return "mostrated";
    }

}
