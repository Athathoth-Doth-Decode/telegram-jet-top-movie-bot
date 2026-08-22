package ru.poseidonnet.jet_movie_top_bot.bot;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.poseidonnet.jet_movie_top_bot.service.CommandService;
import ru.poseidonnet.jet_movie_top_bot.service.MessageProcessingService;
import ru.poseidonnet.jet_movie_top_bot.service.WaitArgsService;
import ru.poseidonnet.jet_movie_top_bot.utils.ParseUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class JetTopMovieBot extends TelegramLongPollingBot {

    private final String name;
    private final MessageProcessingService messageProcessingService;
    private final CommandService commandService;
    private final WaitArgsService waitArgsService;
    private final ExecutorService executorPool;


    public JetTopMovieBot(@Value("${telegram.bot.token}") String token,
                          @Value("${telegram.bot.name}") String name,
                          @Value("${telegram.bot.poolSize:50}") int poolSize,
                          DefaultBotOptions options,
                          MessageProcessingService messageProcessingService,
                          CommandService commandService,
                          WaitArgsService waitArgsService) {
        super(options, token);
        this.name = name;
        this.messageProcessingService = messageProcessingService;
        this.commandService = commandService;
        this.waitArgsService = waitArgsService;
        executorPool = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void onUpdateReceived(Update update) {
        executorPool.execute(() -> processUpdate(update));
    }

    private void processUpdate(Update update) {
        try {
            String messageText = null;
            if (update.getCallbackQuery() != null) {
                messageText = update.getCallbackQuery().getData();
                messageText = transformCallbackMessage(update, messageText);
            }
            Message message = update.getMessage();
            if (message != null) {
                messageText = message.getText();
                if (messageText == null) {
                    return;
                }
                Long userId = update.getMessage().getFrom().getId();
                if (waitArgsService.isWaiting(userId)) {
                    waitArgsService.completeFuture(userId, messageText);
                    return;
                }
                if (messageText.contains("kinopoisk.ru/film/") || messageText.contains("kinopoisk.ru/series/")) {
                    messageProcessingService.processMovieMessage(this, message);
                    return;
                }
            }
            if (messageText != null && messageText.startsWith("/")) {
                int commandIndex = messageText.length();
                if (messageText.indexOf("@") > 0) {
                    commandIndex = messageText.indexOf("@");
                } else if (messageText.indexOf(" ") > 0) {
                    commandIndex = messageText.indexOf(" ");
                }
                String command = messageText.substring(1, commandIndex);
                String commandArgs = null;
                if (messageText.indexOf(" ") > 0) {
                    commandArgs = messageText.substring(messageText.indexOf(" ") + 1).trim();
                }
                commandService.processCommand(this, update, command, commandArgs);
                return;
            }
        } catch (Exception e) {
            log.error("Error on processing update", e);
        }
    }

    @NotNull
    private String transformCallbackMessage(Update update, String messageText) {
        if (messageText.startsWith("vote:")) {
            Integer movieId = ParseUtils.getMovieId(update.getCallbackQuery().getMessage().getText());
            messageText = "/vote " + messageText.substring("vote:".length()) + ";" + movieId;
        }
        if (messageText.startsWith("willview:")) {
            Integer movieId = ParseUtils.getMovieId(update.getCallbackQuery().getMessage().getText());
            messageText = "/willview " + movieId;
        }
        return messageText;
    }

    @Override
    public String getBotUsername() {
        return name;
    }

}
