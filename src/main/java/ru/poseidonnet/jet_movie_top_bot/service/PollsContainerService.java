package ru.poseidonnet.jet_movie_top_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollsContainerService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    @Value("${backup.path:}")
    private String backupPath;

    @Transactional
    public void importLegacyBackupIfNeeded() {
        if (backupPath == null || backupPath.isBlank()) {
            log.info("Legacy backup path is not configured, skipping import");
            return;
        }

        Long totalRows = jdbcTemplate.queryForObject("""
            select
                (select count(*) from movie_messages) +
                (select count(*) from movie_polls) +
                (select count(*) from will_view)
            """, Long.class);

        if (totalRows != null && totalRows > 0) {
            log.info("Database already contains {} rows, skipping legacy import", totalRows);
            return;
        }

        File file = new File(backupPath);
        if (!file.exists() || !file.isFile()) {
            log.warn("Legacy backup file not found at: {}", backupPath);
            return;
        }

        try {
            String json = Files.readString(file.toPath());
            Backup backup = MAPPER.readValue(json, Backup.class);
            replaceAllData(backup);
            log.info("Successfully imported legacy backup from {}", backupPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to import legacy backup from " + backupPath, e);
        }
    }

    @Transactional
    public void add(Integer movieId, Long userId, int pollRate, Integer messageId) {
        Integer currentRate = jdbcTemplate.query("""
                select poll_rate
                from movie_polls
                where movie_id = ? and user_id = ?
                """,
                rs -> rs.next() ? rs.getInt("poll_rate") : null,
                movieId, userId
        );

        if (currentRate != null && currentRate == pollRate) {
            jdbcTemplate.update("""
                delete from movie_polls
                where movie_id = ? and user_id = ?
                """, movieId, userId);
        } else {
            jdbcTemplate.update("""
                insert into movie_polls(movie_id, user_id, poll_rate)
                values (?, ?, ?)
                on conflict (movie_id, user_id)
                do update set poll_rate = excluded.poll_rate
                """, movieId, userId, pollRate);
        }

        insertMovieMessage(movieId, messageId);
    }

    public Map<Long, Integer> getMoviePolls(Integer movieId) {
        Map<Long, Integer> result = new HashMap<>();

        jdbcTemplate.query("""
                select user_id, poll_rate
                from movie_polls
                where movie_id = ?
                """,
                rs -> {
                    result.put(rs.getLong("user_id"), rs.getInt("poll_rate"));
                },
                movieId
        );

        return result;
    }

    public Set<Integer> getLinkedMessages(Integer movieId) {
        Set<Integer> result = new HashSet<>();

        jdbcTemplate.query("""
                select message_id
                from movie_messages
                where movie_id = ?
                """,
                rs -> {
                    result.add(rs.getInt("message_id"));
                },
                movieId
        );

        return result;
    }

    @Transactional
    @SneakyThrows
    public void loadBackup(String backupJson) {
        Backup backup = MAPPER.readValue(backupJson, Backup.class);
        replaceAllData(backup);
    }

    @SneakyThrows
    public String getBackup() {
        Backup backup = new Backup();

        jdbcTemplate.query("""
                select movie_id, message_id
                from movie_messages
                """,
                rs -> {
                    backup.getMovieMessages()
                            .computeIfAbsent(rs.getInt("movie_id"), k -> new HashSet<>())
                            .add(rs.getInt("message_id"));
                }
        );

        jdbcTemplate.query("""
                select movie_id, user_id, poll_rate
                from movie_polls
                """,
                rs -> {
                    backup.getPolls()
                            .computeIfAbsent(rs.getInt("movie_id"), k -> new HashMap<>())
                            .put(rs.getLong("user_id"), rs.getInt("poll_rate"));
                }
        );

        jdbcTemplate.query("""
                select user_id, movie_id
                from will_view
                """,
                rs -> {
                    backup.getWillView()
                            .computeIfAbsent(rs.getLong("user_id"), k -> new HashSet<>())
                            .add(rs.getInt("movie_id"));
                }
        );

        return MAPPER.writeValueAsString(backup);
    }

    public Map<Integer, Map<Long, Integer>> getPolls() {
        Map<Integer, Map<Long, Integer>> result = new HashMap<>();

        jdbcTemplate.query("""
                select movie_id, user_id, poll_rate
                from movie_polls
                """,
                rs -> {
                    result
                            .computeIfAbsent(rs.getInt("movie_id"), k -> new HashMap<>())
                            .put(rs.getLong("user_id"), rs.getInt("poll_rate"));
                }
        );

        return result;
    }

    @Transactional
    public void addWillView(long userId, int movieId, Integer messageId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1
                    from will_view
                    where user_id = ? and movie_id = ?
                )
                """,
                Boolean.class,
                userId, movieId
        );

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.update("""
                delete from will_view
                where user_id = ? and movie_id = ?
                """, userId, movieId);
        } else {
            jdbcTemplate.update("""
                insert into will_view(user_id, movie_id)
                values (?, ?)
                on conflict do nothing
                """, userId, movieId);
        }

        insertMovieMessage(movieId, messageId);
    }

    public Set<Integer> getWillView(long userId) {
        Set<Integer> result = new HashSet<>();

        jdbcTemplate.query("""
                select movie_id
                from will_view
                where user_id = ?
                """,
                rs -> {
                    result.add(rs.getInt("movie_id"));
                },
                userId
        );

        return result;
    }

    public int countWillView(int movieId) {
        Long count = jdbcTemplate.queryForObject("""
            select count(*)
            from will_view
            where movie_id = ?
            """, Long.class, movieId);

        return count != null ? count.intValue() : 0;
    }

    private void insertMovieMessage(Integer movieId, Integer messageId) {
        if (movieId == null || messageId == null) {
            return;
        }

        jdbcTemplate.update("""
            insert into movie_messages(movie_id, message_id)
            values (?, ?)
            on conflict do nothing
            """, movieId, messageId);
    }

    private void replaceAllData(Backup backup) {
        jdbcTemplate.update("delete from movie_messages");
        jdbcTemplate.update("delete from movie_polls");
        jdbcTemplate.update("delete from will_view");

        if (backup == null) {
            return;
        }

        Map<Integer, Set<Integer>> movieMessages =
                backup.getMovieMessages() != null ? backup.getMovieMessages() : Collections.emptyMap();

        Map<Integer, Map<Long, Integer>> polls =
                backup.getPolls() != null ? backup.getPolls() : Collections.emptyMap();

        Map<Long, Set<Integer>> willView =
                backup.getWillView() != null ? backup.getWillView() : Collections.emptyMap();

        movieMessages.forEach((movieId, messageIds) -> {
            if (messageIds == null) {
                return;
            }

            for (Integer messageId : messageIds) {
                if (movieId != null && messageId != null) {
                    jdbcTemplate.update("""
                        insert into movie_messages(movie_id, message_id)
                        values (?, ?)
                        on conflict do nothing
                        """, movieId, messageId);
                }
            }
        });

        polls.forEach((movieId, userRates) -> {
            if (userRates == null) {
                return;
            }

            userRates.forEach((userId, rate) -> {
                if (movieId != null && userId != null && rate != null) {
                    jdbcTemplate.update("""
                        insert into movie_polls(movie_id, user_id, poll_rate)
                        values (?, ?, ?)
                        on conflict (movie_id, user_id)
                        do update set poll_rate = excluded.poll_rate
                        """, movieId, userId, rate);
                }
            });
        });

        willView.forEach((userId, movieIds) -> {
            if (movieIds == null) {
                return;
            }

            for (Integer movieId : movieIds) {
                if (userId != null && movieId != null) {
                    jdbcTemplate.update("""
                        insert into will_view(user_id, movie_id)
                        values (?, ?)
                        on conflict do nothing
                        """, userId, movieId);
                }
            }
        });
    }

    @Data
    private static class Backup {
        private Map<Integer, Set<Integer>> movieMessages = new HashMap<>();
        private Map<Integer, Map<Long, Integer>> polls = new HashMap<>();
        private Map<Long, Set<Integer>> willView = new HashMap<>();
    }
}