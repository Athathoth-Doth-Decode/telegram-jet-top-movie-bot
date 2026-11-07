package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.service.ButtonsService;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class AddMovie implements Command {

    @Value("${telegram.bot.name}")
    private String botName;
    private final ButtonsService buttonsService;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) throws Exception {
        if (commandArgs == null || commandArgs.isBlank()) {
            return;
        }
        String[] args = commandArgs.split(";");
        if (update.getCallbackQuery().getFrom().getId() != Long.parseLong(args[1])) {
            return;
        }
        if (!(update.getCallbackQuery().getMessage() instanceof Message message)) {
            return;
        }
        Long chatId = message.getChatId();

        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(message.getMessageId());
        sender.execute(deleteMessage);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        int movieId = Integer.parseInt(args[0]);
        sendMessage.setText("https://www.kinopoisk.ru/film/" + movieId);
        sendMessage.setReplyMarkup(makeButtons(movieId));
        sender.execute(sendMessage);
    }

    private InlineKeyboardMarkup makeButtons(Integer movieId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            buttons.add(makeInlineKeyboardButton(String.valueOf(i), movieId));
        }
        buttonsService.reindexPollButtons(buttons, movieId);
        InlineKeyboardButton willViewButton = makeWillViewButton(movieId);
        buttonsService.reindexWillViewButton(willViewButton, movieId);
        inlineKeyboardMarkup.setKeyboard(List.of(buttons, List.of(willViewButton)));
        return inlineKeyboardMarkup;
    }

    @NotNull
    private InlineKeyboardButton makeInlineKeyboardButton(String rate, Integer movieId) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(rate);
        inlineKeyboardButton.setCallbackData("/vote " + rate + ";" + movieId);
        return inlineKeyboardButton;
    }

    private InlineKeyboardButton makeWillViewButton(Integer movieId) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Буду посмотреть");
        inlineKeyboardButton.setCallbackData("/willview@" + botName +" " + movieId);
        return inlineKeyboardButton;
    }

    @Override
    public String commandType() {
        return "addmovie";
    }

}
