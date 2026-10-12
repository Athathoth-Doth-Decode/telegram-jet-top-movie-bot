package ru.poseidonnet.jet_movie_top_bot.kinopoisk.api;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;

import java.io.IOException;
import javax.net.ssl.SSLException;

/** Retries only safe movie reads after transport failures, up to three attempts. */
public class MovieApiRetryer extends Retryer.Default {

    public MovieApiRetryer() {
        super(250, 1000, 3);
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (e.request().httpMethod() != Request.HttpMethod.GET
                || !(e.getCause() instanceof IOException)
                || e.getCause() instanceof SSLException
                || Thread.currentThread().isInterrupted()) {
            throw e;
        }
        super.continueOrPropagate(e);
    }

    @Override
    public Retryer clone() {
        return new MovieApiRetryer();
    }
}
