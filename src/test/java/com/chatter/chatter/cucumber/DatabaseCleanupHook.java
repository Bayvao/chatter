package com.chatter.chatter.cucumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.Before;

public class DatabaseCleanupHook {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate rest;

    /**
     * Scenarios share one Spring context, and therefore one in-memory
     * database. Without this, a chat created by one scenario turns up in the
     * next one's history and unread counts.
     */
    @Before(order = 1)
    public void truncateAllTables() {
        // Child rows first: chat_participants has a real FK to chats.
        jdbcTemplate.execute("DELETE FROM chat.messages");
        jdbcTemplate.execute("DELETE FROM chat.chat_participants");
        jdbcTemplate.execute("DELETE FROM chat.chat_counters");
        jdbcTemplate.execute("DELETE FROM chat.chats");
        jdbcTemplate.execute("DELETE FROM app_user.users");
    }

    /**
     * The default {@code HttpURLConnection} client throws
     * "cannot retry due to server authentication" on a 401 instead of
     * returning the response, so tests asserting on 401 never see it. The
     * modern {@code java.net.http} client has no such behaviour. Applied to
     * the auto-configured template so it keeps its random-port base URI.
     */
    @Before(order = 0)
    public void useJdkHttpClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
