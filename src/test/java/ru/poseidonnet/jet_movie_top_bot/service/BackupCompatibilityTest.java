package ru.poseidonnet.jet_movie_top_bot.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupCompatibilityTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PollsContainerService service = new PollsContainerService(jdbc);

    // Preserve the existing backup format when moving from Jackson 2 to Jackson 3.
    // A large Telegram user ID must retain its long value through JSON object keys.
    // Both exported backups and old files must restore the same votes and watch list.
    @Test
    void backupRoundTripPreservesMovieIdsVotesAndLargeUserIds() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getInt("movie_id")).thenReturn(123);
        when(row.getInt("message_id")).thenReturn(99);
        when(row.getInt("poll_rate")).thenReturn(5);
        when(row.getLong("user_id")).thenReturn(5000000007L);
        doAnswer(call -> {
            call.getArgument(1, RowCallbackHandler.class).processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class));
        String legacy = """
                {"movieMessages":{"123":[99]},"polls":{"123":{"5000000007":5}},
                 "willView":{"5000000007":[123]}}
                """;
        String exported = service.getBackup();
        var mapper = new JsonMapper();
        assertThat(mapper.readTree(exported)).isEqualTo(mapper.readTree(legacy));
        service.loadBackup(exported);
        assertRestored();
        clearInvocations(jdbc);
        service.loadBackup(legacy);
        assertRestored();
    }

    @Test
    void malformedBackupDoesNotDeleteExistingData() {
        assertThatThrownBy(() -> service.loadBackup("{broken"))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
        verifyNoInteractions(jdbc);
    }

    private void assertRestored() {
        verify(jdbc).update(contains("insert into movie_messages"), eq(123), eq(99));
        verify(jdbc).update(contains("insert into movie_polls"), eq(123), eq(5000000007L), eq(5));
        verify(jdbc).update(contains("insert into will_view"), eq(5000000007L), eq(123));
    }
}
