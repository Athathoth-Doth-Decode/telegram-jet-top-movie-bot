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
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class AvgRate implements Command {

    private final PollsContainerService pollsContainerService;
    private final MovieLinkCacheService movieLinkCacheService;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) {
        Map<Integer, Map<Long, Integer>> polls = pollsContainerService.getPolls();
        List<Integer> avgRatedList = new ArrayList<>(polls.keySet().stream()
                .filter(k -> polls.get(k).size() > 1)
                .toList());
        Map<Integer, Float> avgResultsMap = polls.entrySet()
                .stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> (float) entry.getValue().values().stream().mapToInt(i -> i).sum() / entry.getValue().size()));

        avgRatedList.sort(Comparator.comparingDouble(o -> -avgResultsMap.get(o))
                .thenComparingInt(mid -> -polls.get(mid).size()));
        avgRatedList = avgRatedList.stream().limit(10).toList();

        Map<Integer, KinopoiskResponse.Movie> links = movieLinkCacheService.getByIds(avgRatedList);
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (int i = 0; i < avgRatedList.size(); i++) {
            int movieId = avgRatedList.get(i);
            InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
            KinopoiskResponse.Movie movie = links.get(movieId);
            inlineKeyboardButton.setText((i + 1) + ") " +
                    movie.getName() +
                    " (" + movie.getYear() + ")" +
                    "\nПроголосовало - " + polls.get(movieId).size()
                    + "\nРейтинг - " + avgResultsMap.get(movieId)
            );
            inlineKeyboardButton.setCallbackData("/addMovie " + movie.getId() + ";" + update.getMessage().getFrom().getId());
            buttons.add(List.of(inlineKeyboardButton));
        }
        sendButtons(sender, update, "Топ10 фильмов с наибольшим средним рейтингом\n", buttons);
    }

    @Override
    public String commandType() {
        return "avgrate";
    }

}
