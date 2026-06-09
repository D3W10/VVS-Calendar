package com.example.meetings.controller;

import com.example.meetings.config.SecurityConfig;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    @Test
    void rootRedirectsToCalendar() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/calendar"));
    }

    @Test
    void registerCreatesUserAndRedirectsToLogin() throws Exception {
        mvc.perform(post("/register")
            .with(csrf())
            .param("username", "ana")
            .param("email", "ana@example.test")
            .param("password", "secret")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered"));

        verify(userService).register("ana", "ana@example.test", "secret");
    }

    @Test
    void registerShowsValidationErrorFromBusinessLayer() throws Exception {
        when(userService.register("ana", "ana@example.test", "secret"))
            .thenThrow(new IllegalArgumentException("Username already taken"));

        mvc.perform(post("/register")
            .with(csrf())
            .param("username", "ana")
            .param("email", "ana@example.test")
            .param("password", "secret")
        )
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "Username already taken"))
            .andExpect(model().attribute("username", "ana"))
            .andExpect(model().attribute("email", "ana@example.test"));
    }
}
