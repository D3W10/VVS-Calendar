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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TicketmasterProviderIntegrationTest {

    @Test
    void searchCallsTicketmasterAndMapsEventsWithUsableStartTimes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://app.ticketmaster.test/discovery/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider provider = new TicketmasterProvider("api-key", "PT", builder.build());

        server.expect(once(), requestTo(containsString("/events.json")))
            .andExpect(requestTo(containsString("keyword=music")))
            .andExpect(requestTo(containsString("size=20")))
            .andExpect(requestTo(containsString("apikey=api-key")))
            .andExpect(requestTo(containsString("countryCode=PT")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                    "_embedded": {
                        "events": [
                            {
                                "id": "tm-1",
                                "name": "Music Night",
                                "url": "https://ticketmaster.test/events/tm-1",
                                "info": "Doors open early",
                                "dates": { "start": { "dateTime": "2026-07-01T20:00:00Z" } },
                                "_embedded": { "venues": [ { "name": "Lisbon Arena" } ] }
                            },
                            {
                                "id": "tm-2",
                                "name": "TBA Event",
                                "dates": { "start": { "localDate": "2026-07-02" } }
                            }
                        ]
                    }
                }
            """, MediaType.APPLICATION_JSON));

        List<DiscoveredEvent> results = provider.search("music");

        assertThat(results).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("Ticketmaster");
            assertThat(event.externalId()).isEqualTo("tm-1");
            assertThat(event.title()).isEqualTo("Music Night");
            assertThat(event.description()).isEqualTo("Doors open early");
            assertThat(event.start()).isEqualTo(Instant.parse("2026-07-01T20:00:00Z"));
            assertThat(event.end()).isNull();
            assertThat(event.url()).isEqualTo("https://ticketmaster.test/events/tm-1");
            assertThat(event.venue()).isEqualTo("Lisbon Arena");
        });
        server.verify();
    }

    @Test
    void searchReturnsEmptyWhenTicketmasterFails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://app.ticketmaster.test/discovery/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TicketmasterProvider provider = new TicketmasterProvider("api-key", "PT", builder.build());

        server.expect(once(), requestTo(containsString("/events.json"))).andRespond(withServerError());

        assertThat(provider.search("music")).isEmpty();
        server.verify();
    }
}
