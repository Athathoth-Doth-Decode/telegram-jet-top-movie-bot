package ru.poseidonnet.jet_movie_top_bot.kinopoisk.api;

import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class MovieFeignClientTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = MovieFeignClient.class)
    static class TestConfiguration {
    }

    @Test
    void retriesTransportFailuresAndDecodesResponseWithConfiguredTimeouts() {
        AtomicInteger attempts = new AtomicInteger();
        Client transport = (request, options) -> {
            assertThat(request.url()).startsWith("https://api.poiskkino.dev/v1.3/movie?");
            assertThat(request.url()).contains("name=Film");
            assertThat(request.headers().get("X-API-Key")).containsExactly("test-key");
            assertThat(options.connectTimeoutMillis()).isEqualTo(3000);
            assertThat(options.readTimeoutMillis()).isEqualTo(5000);
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) throw new UnknownHostException("temporary DNS failure");
            if (attempt == 2) throw new SocketTimeoutException("temporary timeout");
            return response(request, 200, Map.of("Content-Type", List.of("application/json")),
                    "{\"docs\":[{\"id\":123,\"name\":\"Film\",\"year\":2024}],\"total\":1}");
        };
        runner.withBean(Client.class, () -> transport).run(context -> {
            assertThat(context).hasNotFailed();
            var result = context.getBean(MovieFeignClient.class)
                    .findByName("test-key", MovieFeignClient.DAFAULT_FIELDS, "Film");
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getMovies()).hasSize(1);
            assertThat(result.getMovies().get(0).getId()).isEqualTo(123);
            assertThat(attempts).hasValue(3);
        });
    }

    @Test
    void stopsAfterThreeAttemptsAndResetsBudgetForNextCall() {
        AtomicInteger attempts = new AtomicInteger();
        Client transport = (request, options) -> {
            attempts.incrementAndGet();
            throw new UnknownHostException("temporary DNS failure");
        };
        runner.withBean(Client.class, () -> transport).run(context -> {
            var client = context.getBean(MovieFeignClient.class);
            assertThatThrownBy(() -> client.findByName("test-key", List.of("id"), "Film"))
                    .isInstanceOf(RetryableException.class).hasCauseInstanceOf(UnknownHostException.class);
            assertThat(attempts).hasValue(3);
            assertThatThrownBy(() -> client.findByIds("test-key", List.of("id"), 1, List.of(123)))
                    .isInstanceOf(RetryableException.class);
            assertThat(attempts).hasValue(6);
        });
    }

    @Test
    void doesNotRetryHttpErrorsEvenWithRetryAfter() {
        for (int status : List.of(401, 403, 429, 503)) {
            AtomicInteger attempts = new AtomicInteger();
            Client transport = (request, options) -> {
                attempts.incrementAndGet();
                return response(request, status, Map.of("Retry-After", List.of("0")), "unavailable");
            };
            runner.withBean(Client.class, () -> transport).run(context -> {
                var client = context.getBean(MovieFeignClient.class);
                assertThatThrownBy(() -> client.findByName("test-key", List.of("id"), "Film"))
                        .isInstanceOfSatisfying(FeignException.class, e -> assertThat(e.status()).isEqualTo(status));
                assertThat(attempts).hasValue(1);
            });
        }
    }

    @Test
    void doesNotRetryTlsFailures() {
        AtomicInteger attempts = new AtomicInteger();
        Client transport = (request, options) -> {
            attempts.incrementAndGet();
            throw new SSLHandshakeException("certificate validation failed");
        };
        runner.withBean(Client.class, () -> transport).run(context -> {
            assertThatThrownBy(() -> context.getBean(MovieFeignClient.class)
                    .findByName("test-key", List.of("id"), "Film"))
                    .isInstanceOf(RetryableException.class).hasCauseInstanceOf(SSLHandshakeException.class);
            assertThat(attempts).hasValue(1);
        });
    }

    @Test
    void doesNotRetryWritesOrInterruptedRequests() {
        RetryableException writeFailure = failure(Request.HttpMethod.POST);
        assertThatThrownBy(() -> new MovieApiRetryer().continueOrPropagate(writeFailure))
                .isSameAs(writeFailure);
        RetryableException readFailure = failure(Request.HttpMethod.GET);
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new MovieApiRetryer().continueOrPropagate(readFailure))
                    .isSameAs(readFailure);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private RetryableException failure(Request.HttpMethod method) {
        Request request = Request.create(method, "https://api.poiskkino.dev/v1.3/movie",
                Map.of(), (byte[]) null, null, null);
        return new RetryableException(-1, "network failure", method, new IOException(), null, request);
    }

    private Response response(Request request, int status, Map<String, java.util.Collection<String>> headers,
                              String body) {
        return Response.builder().status(status).reason("test response").request(request)
                .headers(headers).body(body, StandardCharsets.UTF_8).build();
    }
}
