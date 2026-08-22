package ru.poseidonnet.jet_movie_top_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class ButtonsService {

    private final PollsContainerService pollsContainerService;

    public void reindexPollButtons(List<InlineKeyboardButton> buttons, Integer movieId) {
        Map<Integer, AtomicInteger> count = new HashMap<>();
        Map<Long, Integer> moviePolls = pollsContainerService.getMoviePolls(movieId);
        if (moviePolls.isEmpty()) {
            return;
        }
        for (Integer pollRate : moviePolls.values()) {
            count.computeIfAbsent(pollRate, k -> new AtomicInteger(0)).incrementAndGet();
        }
        for (int i = 0; i < buttons.size(); i++) {
            AtomicInteger pollCount = count.get(i + 1);
            InlineKeyboardButton inlineKeyboardButton = buttons.get(i);
            String buttonText = String.valueOf(i + 1);
            if (!(pollCount == null || pollCount.get() == 0)) {
                buttonText += " (" + pollCount.get() + ")";
            }
            inlineKeyboardButton.setText(buttonText);
        }
    }

    public void changeButtons(DefaultAbsSender bot, Integer messageId, Long chatId, InlineKeyboardMarkup replyMarkup) {
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setReplyMarkup(replyMarkup);
        editMessageReplyMarkup.setMessageId(messageId);
        editMessageReplyMarkup.setChatId(chatId);
        try {
            bot.execute(editMessageReplyMarkup);
        } catch (Exception e) {
            log.error("Error on buttons rebuilding", e);
        }
    }

    public void reindexWillViewButton(InlineKeyboardButton button, int movieId) {
        String buttonText = button.getText();
        if (buttonText.contains("(")) {
            buttonText = buttonText.substring(0, buttonText.lastIndexOf(" ("));
        }
        int count = pollsContainerService.countWillView(movieId);
        if (count > 0) {
            buttonText += " (" + count + ")";
        }
        button.setText(buttonText);
    }

}
