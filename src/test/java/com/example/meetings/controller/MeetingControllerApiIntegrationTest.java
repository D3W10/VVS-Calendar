package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(MeetingController.class)
@Import(SecurityConfig.class)
class MeetingControllerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    @Test
    void newMeetingFormRequiresAuthentication() throws Exception {
        mvc.perform(get("/meetings/new"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void proposeParsesRequestAndRedirectsToCalendar() throws Exception {
        User organizer = new User("ana", "ana@example.test", "hash");

        when(userService.requireByUsername("ana")).thenReturn(organizer);

        mvc.perform(post("/meetings/new")
            .with(user("ana"))
            .with(csrf())
            .param("title", "Planning")
            .param("description", "Sprint planning")
            .param("start", "2026-06-10T10:00")
            .param("end", "2026-06-10T11:00")
            .param("invitees", "bruno, carla")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/calendar"));

        ZoneId zone = ZoneId.systemDefault();
        verify(meetingService).propose(
            eq(organizer),
            eq("Planning"),
            eq("Sprint planning"),
            eq(LocalDateTime.parse("2026-06-10T10:00").atZone(zone).toInstant()),
            eq(LocalDateTime.parse("2026-06-10T11:00").atZone(zone).toInstant()),
            eq(List.of("bruno", "carla"))
        );
    }

    @Test
    void proposeReturnsFormWithErrorWhenServiceRejectsRequest() throws Exception {
        User organizer = new User("ana", "ana@example.test", "hash");

        when(userService.requireByUsername("ana")).thenReturn(organizer);
        when(meetingService.propose(
            eq(organizer),
            eq("Planning"),
            eq(""),
            eq(LocalDateTime.parse("2026-06-10T11:00").atZone(ZoneId.systemDefault()).toInstant()),
            eq(LocalDateTime.parse("2026-06-10T10:00").atZone(ZoneId.systemDefault()).toInstant()),
            eq(List.of("bruno"))
        ))
            .thenThrow(new IllegalArgumentException("End time must be after start time"));

        mvc.perform(post("/meetings/new")
            .with(user("ana"))
            .with(csrf())
            .param("title", "Planning")
            .param("description", "")
            .param("start", "2026-06-10T11:00")
            .param("end", "2026-06-10T10:00")
            .param("invitees", "bruno")
        )
            .andExpect(status().isOk())
            .andExpect(view().name("propose"))
            .andExpect(model().attribute("error", "End time must be after start time"))
            .andExpect(model().attribute("title", "Planning"))
            .andExpect(model().attribute("invitees", "bruno"));
    }

    @Test
    void respondMapsAcceptActionToAcceptedStatus() throws Exception {
        User invitee = new User("bruno", "bruno@example.test", "hash");

        when(userService.requireByUsername("bruno")).thenReturn(invitee);

        mvc.perform(post("/meetings/15/respond")
            .with(user("bruno"))
            .with(csrf())
            .param("action", "accept")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/calendar"));

        verify(meetingService).respond(15L, invitee, InviteStatus.ACCEPTED);
    }
}
