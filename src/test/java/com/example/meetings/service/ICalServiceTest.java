package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalServiceTest {

    private final ICalService service = new ICalService();

    @Test
    void renderBuildsCalendarWithEscapedFieldsAndParticipantStatuses() {
        User organizer = new User("ana", "ana@example.test", "hash");
        User invitee = new User("bruno", "bruno@example.test", "hash");
        Meeting meeting = new Meeting("Planning, review", "Discuss; risks\nand scope", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), organizer);

        ReflectionTestUtils.setField(meeting, "id", 42L);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));

        String calendar = service.render(organizer, List.of(meeting));

        assertThat(calendar).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(calendar).contains("UID:meeting-42@meetings-app\r\n");
        assertThat(calendar).contains("DTSTART:20260610T100000Z\r\n");
        assertThat(calendar).contains("DTEND:20260610T110000Z\r\n");
        assertThat(calendar).contains("SUMMARY:Planning\\, review\r\n");
        assertThat(calendar).contains("DESCRIPTION:Discuss\\; risks\\nand scope\r\n");
        assertThat(calendar).contains("ATTENDEE;CN=bruno;PARTSTAT=NEEDS-ACTION:mailto:bruno@example.test\r\n");
        assertThat(calendar).contains("STATUS:TENTATIVE\r\n");
        assertThat(calendar).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void renderMarksMeetingConfirmedWhenAllParticipantsAccepted() {
        User organizer = new User("ana", "ana@example.test", "hash");
        Meeting meeting = new Meeting("Planning", "", Instant.parse("2026-06-10T10:00:00Z"), Instant.parse("2026-06-10T11:00:00Z"), organizer);

        ReflectionTestUtils.setField(meeting, "id", 43L);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));

        String calendar = service.render(organizer, List.of(meeting));

        assertThat(calendar).contains("STATUS:CONFIRMED\r\n");
    }
}
