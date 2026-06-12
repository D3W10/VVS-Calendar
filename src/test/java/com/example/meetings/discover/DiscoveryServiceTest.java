package com.example.meetings.discover;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryServiceTest {

    @Test
    void searchReturnsEmptyForBlankQueryWithoutCallingProviders() {
        EventProvider provider = mock(EventProvider.class);
        DiscoveryService service = new DiscoveryService(List.of(provider));

        assertThat(service.search("  ")).isEmpty();

        verify(provider, never()).isConfigured();
        verify(provider, never()).search(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchOnlyUsesConfiguredProvidersDeduplicatesByUrlAndSortsByStart() {
        EventProvider configured = mock(EventProvider.class);
        EventProvider disabled = mock(EventProvider.class);
        DiscoveredEvent later = event("tm", "1", "Later", "https://example.test/same", "2026-07-01T20:00:00Z");
        DiscoveredEvent duplicate = event("sg", "2", "Duplicate", "https://example.test/same", "2026-07-01T19:00:00Z");
        DiscoveredEvent earlier = event("tm", "3", "Earlier", "https://example.test/earlier", "2026-07-01T18:00:00Z");

        when(configured.isConfigured()).thenReturn(true);
        when(configured.search("music")).thenReturn(List.of(later, duplicate, earlier));
        when(disabled.isConfigured()).thenReturn(false);

        DiscoveryService service = new DiscoveryService(List.of(configured, disabled));

        assertThat(service.search("music")).containsExactly(earlier, later);
        verify(disabled, never()).search(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchFallsBackToSourceAndExternalIdWhenUrlIsMissing() {
        EventProvider provider = mock(EventProvider.class);
        DiscoveredEvent first = event("agenda", "1", "First", null, "2026-07-01T18:00:00Z");
        DiscoveredEvent duplicate = event("agenda", "1", "Duplicate", null, "2026-07-01T19:00:00Z");
        DiscoveredEvent differentSource = event("other", "1", "Other", null, "2026-07-01T20:00:00Z");

        when(provider.isConfigured()).thenReturn(true);
        when(provider.search("talk")).thenReturn(List.of(first, duplicate, differentSource));

        DiscoveryService service = new DiscoveryService(List.of(provider));

        assertThat(service.search("talk")).containsExactly(first, differentSource);
    }

    @Test
    void searchFallsBackToSourceAndExternalIdWhenUrlIsBlank() {
        EventProvider provider = mock(EventProvider.class);
        DiscoveredEvent first = event("agenda", "1", "First", "", "2026-07-01T18:00:00Z");
        DiscoveredEvent second = event("agenda", "2", "Second", "", "2026-07-01T19:00:00Z");
        DiscoveredEvent duplicate = event("agenda", "1", "Duplicate", "", "2026-07-01T20:00:00Z");

        when(provider.isConfigured()).thenReturn(true);
        when(provider.search("talk")).thenReturn(List.of(first, second, duplicate));

        DiscoveryService service = new DiscoveryService(List.of(provider));

        assertThat(service.search("talk")).containsExactly(first, second);
    }

    private static DiscoveredEvent event(String source, String externalId, String title, String url, String start) {
        return new DiscoveredEvent(source, externalId, title, "", Instant.parse(start), null, url, "Venue");
    }
}
