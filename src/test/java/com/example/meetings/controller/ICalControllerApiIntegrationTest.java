package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ICalController.class)
@Import(SecurityConfig.class)
class ICalControllerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private ICalService icalService;

    @Test
    void feedReturnsTextCalendarForValidPublicToken() throws Exception {
        User user = new User("ana", "ana@example.test", "hash");
        Meeting meeting = new Meeting("Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), user);
        String calendar = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n";

        when(userRepository.findByIcalToken("public-token")).thenReturn(Optional.of(user));
        when(meetingService.calendarFor(user)).thenReturn(List.of(meeting));
        when(icalService.render(user, List.of(meeting))).thenReturn(calendar);

        mvc.perform(get("/ical/public-token.ics"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/calendar;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"meetings.ics\""))
            .andExpect(content().bytes(calendar.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void feedReturnsNotFoundForUnknownToken() throws Exception {
        when(userRepository.findByIcalToken("missing")).thenReturn(Optional.empty());

        mvc.perform(get("/ical/missing.ics")).andExpect(status().isNotFound());
    }
}
