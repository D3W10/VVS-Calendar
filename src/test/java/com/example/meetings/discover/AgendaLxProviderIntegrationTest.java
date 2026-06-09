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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AgendaLxProviderIntegrationTest {

    @Test
    void searchCallsAgendaLxAndMapsFutureOccurrences() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://www.agendalx.test/wp-json/agendalx/v1")
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; meetings-app/0.1; +http://localhost)");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgendaLxProvider provider = new AgendaLxProvider(builder.build());

        server.expect(once(), requestTo(containsString("/events")))
            .andExpect(requestTo(containsString("search=theatre")))
            .andExpect(requestTo(containsString("per_page=20")))
            .andExpect(header("User-Agent", containsString("meetings-app")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                [
                    {
                        "id": 55,
                        "title": { "rendered": "Theatre Evening" },
                        "description": [ "<p>One act</p>", "<strong>Free entry</strong>" ],
                        "occurences": [ "2026-07-04" ],
                        "string_times": "sab: 21h30",
                        "link": "https://agendalx.test/event/55",
                        "venue": { "99": { "name": "Teatro Municipal" } }
                    },
                    {
                        "id": 56,
                        "title": { "rendered": "Past Event" },
                        "occurences": [ "2024-01-01" ],
                        "string_times": "20h"
                    }
                ]
                """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("theatre");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("Agenda Cultural de Lisboa");
            assertThat(event.externalId()).isEqualTo("55");
            assertThat(event.title()).isEqualTo("Theatre Evening");
            assertThat(event.description()).contains("One act", "Free entry");
            assertThat(event.start()).isEqualTo(Instant.parse("2026-07-04T20:30:00Z"));
            assertThat(event.url()).isEqualTo("https://agendalx.test/event/55");
            assertThat(event.venue()).isEqualTo("Teatro Municipal");
        });
        server.verify();
    }

    @Test
    void searchUsesDefaultLisbonEveningTimeWhenTimeIsMissing() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("https://www.agendalx.test/wp-json/agendalx/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgendaLxProvider provider = new AgendaLxProvider(builder.build());

        server.expect(once(), requestTo(containsString("/events")))
            .andRespond(withSuccess("""
                [
                    {
                        "id": 57,
                        "title": { "rendered": "Open Air Cinema" },
                        "description": [],
                        "occurences": [ "2026-12-01" ],
                        "string_times": "",
                        "link": "https://agendalx.test/event/57"
                    }
                ]
                """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("cinema");

        assertThat(results).singleElement()
            .extracting(DiscoveredEvent::start)
            .isEqualTo(Instant.parse("2026-12-01T20:00:00Z"));
        server.verify();
    }
}
