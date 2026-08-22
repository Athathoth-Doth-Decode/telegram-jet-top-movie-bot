package ru.poseidonnet.jet_movie_top_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.poseidonnet.jet_movie_top_bot.utils.ParseUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class MessageProcessingService {

    @Value("${telegram.bot.name}")
    private String botName;
    private final ButtonsService buttonsService;

    public void processMovieMessage(DefaultAbsSender sender, Message message) {
        Integer messageId = message.getMessageId();
        Long chatId = message.getChatId();
        Integer movieId = ParseUtils.getMovieId(message.getText());

        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message.getText());
        sendMessage.setReplyMarkup(makeButtons(movieId));
        try {
            sender.execute(deleteMessage);
            sender.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error processing movie message", e);
        }
    }

    private InlineKeyboardMarkup makeButtons(Integer movieId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            buttons.add(makeInlineKeyboardButton(String.valueOf(i), movieId));
        }
        buttonsService.reindexPollButtons(buttons, movieId);
        List<InlineKeyboardButton> willViewButton = List.of(makeWillViewButton(movieId));
        inlineKeyboardMarkup.setKeyboard(List.of(buttons, willViewButton));
        return inlineKeyboardMarkup;
    }

    private InlineKeyboardButton makeWillViewButton(Integer movieId) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Буду посмотреть");
        inlineKeyboardButton.setCallbackData("/willview@" + botName +" " + movieId);
        buttonsService.reindexWillViewButton(inlineKeyboardButton, movieId);
        return inlineKeyboardButton;
    }

    @NotNull
    private InlineKeyboardButton makeInlineKeyboardButton(String rate, Integer movieId) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(rate);
        inlineKeyboardButton.setCallbackData("/vote@" + botName +" " + rate + ";" + movieId);
        return inlineKeyboardButton;
    }

}
