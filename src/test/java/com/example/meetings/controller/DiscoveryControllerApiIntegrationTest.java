package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiscoveryController.class)
@Import(SecurityConfig.class)
class DiscoveryControllerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DiscoveryService discoveryService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    void discoverSearchPopulatesResultsWhenAProviderIsConfigured() throws Exception {
        EventProvider provider = mock(EventProvider.class);
        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "tm-1", "Concert", "", Instant.parse("2026-07-01T20:00:00Z"), null, "https://example.test/event", "Arena");

        when(provider.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(provider));
        when(discoveryService.search("music")).thenReturn(List.of(event));

        mvc.perform(get("/discover")
            .with(user("ana"))
            .param("q", "music")
        )
            .andExpect(status().isOk())
            .andExpect(view().name("discover"))
            .andExpect(model().attribute("q", "music"))
            .andExpect(model().attribute("anyConfigured", true))
            .andExpect(model().attribute("results", List.of(event)));
    }

    @Test
    void copyParsesDiscoveredEventFormAndRedirectsToCalendar() throws Exception {
        User user = new User("ana", "ana@example.test", "hash");

        when(userService.requireByUsername("ana")).thenReturn(user);

        mvc.perform(post("/discover/copy")
            .with(user("ana"))
            .with(csrf())
            .param("source", "SeatGeek")
            .param("externalId", "123")
            .param("title", "Football Final")
            .param("description", "Championship")
            .param("start", "2026-07-03T21:30:00Z")
            .param("end", "")
            .param("url", "https://example.test/final")
            .param("venue", "Stadium")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/calendar"));

        verify(meetingService)
            .copyFromDiscovered(
                org.mockito.ArgumentMatchers.eq(user),
                argThat(event ->event.source().equals("SeatGeek") && event.externalId().equals("123") && event.title().equals("Football Final") && event.start().equals(Instant.parse("2026-07-03T21:30:00Z")) && event.end() == null && event.venue().equals("Stadium"))
            );
    }
}
