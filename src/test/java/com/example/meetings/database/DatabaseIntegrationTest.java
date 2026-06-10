package com.example.meetings.database;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    UserService.class,
    MeetingService.class,
    DatabaseIntegrationTest.TestConfig.class
})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:meetings-db-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
class DatabaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MeetingService meetingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Test
    void registerPersistsEncodedUserAndEnforcesUniqueUsername() {
        User user = userService.register("ana", "ana@example.test", "secret");

        assertThat(user.getId()).isNotNull();
        assertThat(userRepository.findByUsername("ana")).hasValueSatisfying(saved -> {
            assertThat(saved.getEmail()).isEqualTo("ana@example.test");
            assertThat(saved.getPasswordHash()).isNotEqualTo("secret");
            assertThat(passwordEncoder.matches("secret", saved.getPasswordHash())).isTrue();
            assertThat(saved.getIcalToken()).isNotBlank();
        });

        assertThatThrownBy(() -> userService.register("ana", "other@example.test", "secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Username already taken");
    }

    @Test
    void proposedMeetingPersistsOrganizerAndPendingInvitees() {
        User organizer = userService.register("ana", "ana@example.test", "secret");
        User invitee = userService.register("bruno", "bruno@example.test", "secret");

        Meeting meeting = meetingService.propose(organizer, "Planning", "Sprint planning", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), List.of("bruno"));

        assertThat(meeting.getId()).isNotNull();
        assertThat(meetingRepository.findById(meeting.getId())).hasValueSatisfying(saved -> {
            assertThat(saved.getTitle()).isEqualTo("Planning");
            assertThat(saved.getOrganizer().getUsername()).isEqualTo("ana");
            assertThat(saved.isConfirmed()).isFalse();
        });
        assertThat(participantRepository.findByUserAndStatus(invitee, InviteStatus.PENDING))
            .singleElement()
            .extracting(participant -> participant.getMeeting().getId())
            .isEqualTo(meeting.getId());
    }

    @Test
    void respondingDeclinedRemovesInviteFromInviteeCalendarQuery() {
        User organizer = userService.register("ana", "ana@example.test", "secret");
        User invitee = userService.register("bruno", "bruno@example.test", "secret");
        Meeting meeting = meetingService.propose(organizer, "Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), List.of("bruno"));

        assertThat(meetingService.calendarFor(invitee)).extracting(Meeting::getId).contains(meeting.getId());

        meetingService.respond(meeting.getId(), invitee, InviteStatus.DECLINED);

        assertThat(meetingService.calendarFor(invitee)).extracting(Meeting::getId).doesNotContain(meeting.getId());
        assertThat(meetingService.calendarFor(organizer)).extracting(Meeting::getId).contains(meeting.getId());
    }

    @Test
    void acceptingInviteMakesPersistedMeetingConfirmed() {
        User organizer = userService.register("ana", "ana@example.test", "secret");
        User invitee = userService.register("bruno", "bruno@example.test", "secret");
        Meeting meeting = meetingService.propose(organizer, "Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), List.of("bruno"));

        meetingService.respond(meeting.getId(), invitee, InviteStatus.ACCEPTED);

        Meeting saved = meetingRepository.findById(meeting.getId()).orElseThrow();
        assertThat(saved.getParticipants()).extracting(MeetingParticipant::getStatus).containsOnly(InviteStatus.ACCEPTED);
        assertThat(saved.isConfirmed()).isTrue();
    }

    @Test
    void copyFromDiscoveredPersistsAcceptedSingleParticipantMeeting() {
        User user = userService.register("ana", "ana@example.test", "secret");
        DiscoveredEvent event = new DiscoveredEvent("SeatGeek", "123", "Football Final", "Championship match", Instant.parse("2026-07-03T21:30:00Z"), null, "https://example.test/final", "National Stadium");

        Meeting meeting = meetingService.copyFromDiscovered(user, event);

        assertThat(meetingRepository.findById(meeting.getId())).hasValueSatisfying(saved -> {
            assertThat(saved.getTitle()).isEqualTo("Football Final");
            assertThat(saved.getEndTime()).isEqualTo(Instant.parse("2026-07-03T23:30:00Z"));
            assertThat(saved.getDescription()).contains("Championship match", "Source: SeatGeek");
            assertThat(saved.isConfirmed()).isTrue();
        });
    }
}
