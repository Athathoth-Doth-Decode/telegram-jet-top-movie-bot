package ru.poseidonnet.jet_movie_top_bot.command;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.poseidonnet.jet_movie_top_bot.service.ButtonsService;
import ru.poseidonnet.jet_movie_top_bot.service.PollsContainerService;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class CallbackCommandsTest {
    private final DefaultAbsSender sender = mock(DefaultAbsSender.class);
    private final PollsContainerService polls = mock(PollsContainerService.class);
    private final ButtonsService buttons = mock(ButtonsService.class);

    @Test
    void inaccessibleCallbacksDoNotModifyVotesOrSendMessages() throws Exception {
        Update update = callback(mock(InaccessibleMessage.class));
        new Vote(polls, buttons).process(sender, update, "5;123");
        new WillView(polls, buttons).process(sender, update, "123");
        new AddMovie(buttons).process(sender, update, "123;7");
        verifyNoInteractions(polls, buttons, sender);
    }

    @Test
    void accessibleVoteStillUpdatesVoteAndLinkedMessageButtons() throws Exception {
        Message message = message();
        when(polls.getLinkedMessages(123)).thenReturn(Set.of(99));
        new Vote(polls, buttons).process(sender, callback(message), "5;123");
        verify(polls).add(123, 7L, 5, 99);
        verify(buttons).reindexPollButtons(message.getReplyMarkup().getKeyboard().get(0), 123);
        verify(buttons).changeButtons(sender, 99, 42L, message.getReplyMarkup());
    }

    @Test
    void accessibleWillViewStillUpdatesListAndLinkedMessageButtons() throws Exception {
        Message message = message();
        when(polls.getLinkedMessages(123)).thenReturn(Set.of(99));
        new WillView(polls, buttons).process(sender, callback(message), "123");
        verify(polls).addWillView(7L, 123, 99);
        verify(buttons).reindexWillViewButton(message.getReplyMarkup().getKeyboard().get(1).get(0), 123);
        verify(buttons).changeButtons(sender, 99, 42L, message.getReplyMarkup());
    }

    private Update callback(MaybeInaccessibleMessage message) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        CallbackQuery callback = new CallbackQuery();
        callback.setFrom(user);
        callback.setMessage(message);
        Update update = new Update();
        update.setCallbackQuery(callback);
        return update;
    }

    private Message message() {
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(99);
        when(message.getChatId()).thenReturn(42L);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(new InlineKeyboardButton("5")),
                List.of(new InlineKeyboardButton("Will view"))));
        when(message.getReplyMarkup()).thenReturn(markup);
        return message;
    }
}
