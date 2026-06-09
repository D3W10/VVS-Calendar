package com.example.meetings.discover;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeatGeekProviderIntegrationTest {

    @Test
    void searchCallsSeatGeekAndMapsEventsWithUtcDatetime() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.seatgeek.test/2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeatGeekProvider provider = new SeatGeekProvider("client-id", builder.build());

        server.expect(once(), requestTo(containsString("/events")))
            .andExpect(requestTo(containsString("q=football")))
            .andExpect(requestTo(containsString("per_page=20")))
            .andExpect(requestTo(containsString("client_id=client-id")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                    "events": [
                        {
                            "id": 123,
                            "title": "Football Final",
                            "short_title": "Final",
                            "datetime_utc": "2026-07-03T21:30:00",
                            "url": "https://seatgeek.test/events/123",
                            "description": "Championship match",
                            "venue": { "name": "National Stadium" }
                        },
                        {
                            "id": 124,
                            "title": "Broken Date",
                            "datetime_utc": "not-a-date"
                        }
                    ]
                }
            """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("football");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("SeatGeek");
            assertThat(event.externalId()).isEqualTo("123");
            assertThat(event.title()).isEqualTo("Football Final");
            assertThat(event.description()).isEqualTo("Championship match");
            assertThat(event.start()).isEqualTo(Instant.parse("2026-07-03T21:30:00Z"));
            assertThat(event.url()).isEqualTo("https://seatgeek.test/events/123");
            assertThat(event.venue()).isEqualTo("National Stadium");
        });
        server.verify();
    }

    @Test
    void searchDoesNotCallSeatGeekWithoutClientId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.seatgeek.test/2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeatGeekProvider provider = new SeatGeekProvider("", builder.build());

        assertThat(provider.search("football")).isEmpty();
        server.verify();
    }
}
