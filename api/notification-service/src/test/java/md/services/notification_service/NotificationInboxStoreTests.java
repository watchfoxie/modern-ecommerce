package md.services.notification_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import md.services.notification_service.service.NotificationInboxStore;

class NotificationInboxStoreTests {

	@Test
	void markProcessedIfNewExpiresEventIdsAfterTtl() throws Exception {
		NotificationInboxStore store = new NotificationInboxStore(Duration.ofMillis(10));

		assertThat(store.markProcessedIfNew("evt-1")).isTrue();
		assertThat(store.markProcessedIfNew("evt-1")).isFalse();

		Thread.sleep(25);
		store.cleanUpIdempotencyCache();

		assertThat(store.markProcessedIfNew("evt-1")).isTrue();
	}

}
