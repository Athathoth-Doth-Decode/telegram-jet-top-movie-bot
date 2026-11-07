package ru.poseidonnet.jet_movie_top_bot.bot;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.poseidonnet.jet_movie_top_bot.config.AppConfiguration;
import ru.poseidonnet.jet_movie_top_bot.service.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelegramPollingTest {
    // Exercise the real Telegram client and session with the application's proxy settings.
    // A closed loopback destination ensures requests can succeed only through the local proxy.
    // The fake update verifies that the managed Jackson version still decodes Telegram responses.
    @Test
    @Timeout(15)
    void pollingUsesConfiguredProxyAndDecodesUpdatesWithoutClearingWebhook() throws Exception {
        ServerSocket proxy = new ServerSocket();
        proxy.bind(new InetSocketAddress("127.0.0.1", 0));
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> tunnel = new AtomicReference<>();
        AtomicReference<Exception> proxyFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> serve(proxy, first, target, tunnel, proxyFailure));
        worker.setDaemon(true);
        worker.start();
        try {
            new ApplicationContextRunner().withUserConfiguration(AppConfiguration.class)
                    .withPropertyValues("telegram.bot.enabled=false", "telegram.bot.proxy.enabled=true",
                            "telegram.bot.proxy.host=127.0.0.1", "telegram.bot.proxy.type=HTTP",
                            "telegram.bot.proxy.port=" + proxy.getLocalPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        DefaultBotOptions options = context.getBean(DefaultBotOptions.class);
                        options.setBaseUrl("http://127.0.0.2:9/bot");
                        options.setGetUpdatesTimeout(1);
                        CountDownLatch received = new CountDownLatch(1);
                        AtomicReference<Update> actual = new AtomicReference<>();
                        JetTopMovieBot bot = new JetTopMovieBot("123:test-only", "test", 1, options,
                                mock(MessageProcessingService.class), mock(CommandService.class),
                                mock(WaitArgsService.class)) {
                            @Override
                            public void onUpdateReceived(Update update) {
                                actual.set(update);
                                received.countDown();
                            }
                        };
                        BotSession session = new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
                        try {
                            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
                            assertThat(actual.get().getMessage().getText()).isEqualTo("/ping");
                            assertThat(proxyFailure.get()).isNull();
                            assertThat(tunnel.get()).isEqualTo("CONNECT 127.0.0.2:9 HTTP/1.1");
                            assertThat(target.get()).isEqualTo("POST /bot123:test-only/getupdates HTTP/1.1");
                        } finally {
                            session.stop();
                        }
                    });
        } finally {
            proxy.close();
            worker.join(2000);
        }
    }
    // The Telegram 6 client uses a CONNECT tunnel even for a plain HTTP test URL.
    // This local server terminates that tunnel and supplies exactly one fake update.
    // It never forwards traffic, so no Telegram account or external service is contacted.
    private static void serve(ServerSocket proxy, AtomicBoolean first, AtomicReference<String> target,
                              AtomicReference<String> tunnel, AtomicReference<Exception> failure) {
        while (!proxy.isClosed()) {
            try (Socket socket = proxy.accept()) {
                socket.setSoTimeout(5000);
                var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                tunnel.compareAndSet(null, input.readLine());
                readHeaders(input);
                socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                target.compareAndSet(null, input.readLine());
                int length = readHeaders(input);
                char[] body = new char[length];
                int offset = 0;
                while (offset < length) {
                    int count = input.read(body, offset, length - offset);
                    if (count < 0) throw new IOException("Incomplete request");
                    offset += count;
                }
                String response = first.getAndSet(false) ? """
                        {"ok":true,"result":[{"update_id":101,"message":{"message_id":99,
                         "date":1,"chat":{"id":42,"type":"private"},"text":"/ping"}}]}
                        """ : "{\"ok\":true,\"result\":[]}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + bytes.length + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(bytes);
            } catch (Exception e) {
                if (!proxy.isClosed()) failure.compareAndSet(null, e);
            }
        }
    }

    private static int readHeaders(BufferedReader input) throws IOException {
        int length = 0;
        for (String line; (line = input.readLine()) != null && !line.isEmpty();) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        return length;
    }

}
