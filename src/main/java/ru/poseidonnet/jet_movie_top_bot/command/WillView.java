package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.service.ButtonsService;
import ru.poseidonnet.jet_movie_top_bot.service.PollsContainerService;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class WillView implements Command {

    private final PollsContainerService pollsContainerService;
    private final ButtonsService buttonsService;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) throws Exception {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long userId = callbackQuery.getFrom().getId();
        if (!(callbackQuery.getMessage() instanceof Message message)) {
            return;
        }
        int movieId = Integer.parseInt(commandArgs);
        Long chatId = message.getChatId();

        pollsContainerService.addWillView(userId, movieId, message.getMessageId());

        InlineKeyboardMarkup replyMarkup = message.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = replyMarkup.getKeyboard();
        if (keyboard.size() < 2) {
            return;
        }
        List<InlineKeyboardButton> buttons = keyboard.get(1);
        InlineKeyboardButton willViewButton = buttons.get(0);

        buttonsService.reindexWillViewButton(willViewButton, movieId);
        for (Integer linkedMessageId : pollsContainerService.getLinkedMessages(movieId)) {
            try {
                buttonsService.changeButtons(sender, linkedMessageId, chatId, replyMarkup);
            } catch (Exception e) {
                log.error("Error on rebuilding buttons", e);
            }
        }

    }

    @Override
    public String commandType() {
        return "willview";
    }

}
