package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingServiceTest {

    private MeetingRepository meetingRepository;
    private MeetingParticipantRepository participantRepository;
    private UserRepository userRepository;
    private MeetingService service;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        participantRepository = mock(MeetingParticipantRepository.class);
        userRepository = mock(UserRepository.class);
        service = new MeetingService(meetingRepository, participantRepository, userRepository);
    }

    @Test
    void proposeCreatesMeetingWithOrganizerAcceptedAndUniquePendingInvitees() {
        User organizer = user("ana");
        User bruno = user("bruno");
        User carla = user("carla");
        Instant start = Instant.parse("2026-06-10T10:00:00Z");
        Instant end = Instant.parse("2026-06-10T11:00:00Z");

        when(userRepository.findByUsername("bruno")).thenReturn(Optional.of(bruno));
        when(userRepository.findByUsername("carla")).thenReturn(Optional.of(carla));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting meeting = service.propose(organizer, "Planning", "Sprint planning", start, end, List.of(" bruno ", "carla", "bruno", "", "ana"));

        assertThat(meeting.getTitle()).isEqualTo("Planning");
        assertThat(meeting.getStartTime()).isEqualTo(start);
        assertThat(meeting.getEndTime()).isEqualTo(end);
        assertThat(meeting.getOrganizer()).isSameAs(organizer);
        assertThat(meeting.getParticipants())
            .extracting(participant -> participant.getUser().getUsername(), MeetingParticipant::getStatus)
            .containsExactlyInAnyOrder(Tuple.tuple("ana", InviteStatus.ACCEPTED), Tuple.tuple("bruno", InviteStatus.PENDING), Tuple.tuple("carla", InviteStatus.PENDING));

        verify(userRepository).findByUsername("bruno");
        verify(userRepository).findByUsername("carla");
        verify(meetingRepository).save(meeting);
    }

    @Test
    void proposeRejectsInvalidTimeRangeBeforeSaving() {
        Instant start = Instant.parse("2026-06-10T10:00:00Z");

        assertThatThrownBy(() -> service.propose(user("ana"), "Planning", "", start, start, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End time must be after start time");

        verify(meetingRepository, never()).save(any());
    }

    @Test
    void proposeRejectsUnknownInvitee() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose(user("ana"), "Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), List.of("missing")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown invitee: missing");

        verify(meetingRepository, never()).save(any());
    }

    @Test
    void respondOnlyAllowsAcceptedOrDeclinedStatuses() {
        assertThatThrownBy(() -> service.respond(10L, user("ana"), InviteStatus.PENDING))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Response must be ACCEPTED or DECLINED");

        verify(participantRepository, never()).findByMeetingIdAndUserId(any(), any());
    }

    @Test
    void respondUpdatesExistingParticipantStatus() {
        User invitee = user("bruno");
        ReflectionTestUtils.setField(invitee, "id", 7L);
        MeetingParticipant participant = new MeetingParticipant(null, invitee, InviteStatus.PENDING);

        when(participantRepository.findByMeetingIdAndUserId(15L, 7L)).thenReturn(Optional.of(participant));

        service.respond(15L, invitee, InviteStatus.ACCEPTED);

        assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void copyFromDiscoveredDefaultsMissingEndAndBuildsSourceDescription() {
        User user = user("ana");
        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "abc", "Concert", "Live show", Instant.parse("2026-07-01T20:00:00Z"), null, "https://example.com/event", "Arena");
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting meeting = service.copyFromDiscovered(user, event);

        assertThat(meeting.getTitle()).isEqualTo("Concert");
        assertThat(meeting.getEndTime()).isEqualTo(Instant.parse("2026-07-01T22:00:00Z"));
        assertThat(meeting.getDescription()).contains("Live show", "Venue: Arena", "Source: Ticketmaster");
        assertThat(meeting.getParticipants())
            .singleElement()
            .satisfies(participant -> {
                assertThat(participant.getUser()).isSameAs(user);
                assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
            });
        assertThat(meeting.isConfirmed()).isTrue();
    }

    @Test
    void calendarForIcalTokenFindsUserBeforeLoadingCalendar() {
        User user = user("ana");
        Meeting meeting = new Meeting("Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), user);

        when(userRepository.findByIcalToken("token")).thenReturn(Optional.of(user));
        when(meetingRepository.findCalendarMeetings(user)).thenReturn(List.of(meeting));
        assertThat(service.calendarForIcalToken("token")).containsExactly(meeting);
    }

    private static User user(String username) {
        return new User(username, username + "@example.test", "hash");
    }
}
