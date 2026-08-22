package ru.poseidonnet.jet_movie_top_bot.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.poseidonnet.jet_movie_top_bot.service.PollsContainerService;
import ru.poseidonnet.jet_movie_top_bot.service.WaitArgsService;

@Slf4j
@RequiredArgsConstructor
@Component
public class LoadBackup implements Command {

    private final WaitArgsService waitArgsService;
    private final PollsContainerService pollsContainerService;

    @Value("${telegram.bot.admin-user-id:0}")
    private long adminUserId;

    @Override
    public void process(DefaultAbsSender sender, Update update, String commandArgs) throws Exception {
        Long userId = update.getMessage().getFrom().getId();

        if (adminUserId == 0 || userId != adminUserId) {
            log.warn("Unauthorized /loadbackup attempt from userId={}", userId);
            sendMessage(sender, update, "У вас нет прав для выполнения этой команды.");
            return;
        }

        String backup;
        if (commandArgs == null) {
            sendMessage(sender, update, "Backup?");
            backup = waitArgsService.waitForArgs(userId, 60);
        } else {
            backup = commandArgs;
        }
        pollsContainerService.loadBackup(backup);
    }

    @Override
    public String commandType() {
        return "loadbackup";
    }

}
