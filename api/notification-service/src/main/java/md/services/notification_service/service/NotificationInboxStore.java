package md.services.notification_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import md.services.notification_service.domain.NotificationRecord;
import md.services.notification_service.domain.OrderCreatedEvent;

@Component
public class NotificationInboxStore {

	private static final int MAX_ENTRIES = 50;

	private final Deque<NotificationRecord> recentNotifications = new ConcurrentLinkedDeque<>();
	private final Deque<NotificationRecord> deadLetterNotifications = new ConcurrentLinkedDeque<>();
	private final Cache<String, Boolean> processedEventIds;

	public NotificationInboxStore() {
		this(Duration.ofHours(24));
	}

	public NotificationInboxStore(Duration idempotencyTtl) {
		this.processedEventIds = Caffeine.newBuilder()
				.expireAfterWrite(idempotencyTtl)
				.maximumSize(100_000)
				.build();
	}

	public boolean wasProcessed(String eventId) {
		return processedEventIds.getIfPresent(eventId) != null;
	}

	public boolean markProcessedIfNew(String eventId) {
		return processedEventIds.asMap().putIfAbsent(eventId, true) == null;
	}

	public void cleanUpIdempotencyCache() {
		processedEventIds.cleanUp();
	}

	public void recordDelivered(OrderCreatedEvent event, String status, String details) {
		addEntry(recentNotifications, new NotificationRecord(
				event.eventId(),
				event.orderId(),
				event.customerEmail(),
				status,
				details,
				Instant.now()));
	}

	public void recordDeadLetter(OrderCreatedEvent event, String details) {
		addEntry(deadLetterNotifications, new NotificationRecord(
				event.eventId(),
				event.orderId(),
				event.customerEmail(),
				"DEAD_LETTER",
				details,
				Instant.now()));
	}

	public void recordMalformedDeadLetter(String payload, String details) {
		String eventId = "malformed-" + Integer.toUnsignedString(payload == null ? 0 : payload.hashCode(), 16);
		addEntry(deadLetterNotifications, new NotificationRecord(
				eventId,
				null,
				null,
				"DEAD_LETTER_MALFORMED",
				details,
				Instant.now()));
	}

	public void recordDuplicate(OrderCreatedEvent event, String details) {
		addEntry(recentNotifications, new NotificationRecord(
				event.eventId(),
				event.orderId(),
				event.customerEmail(),
				"DUPLICATE_IGNORED",
				details,
				Instant.now()));
	}

	public List<NotificationRecord> recentNotifications() {
		return new ArrayList<>(recentNotifications);
	}

	public List<NotificationRecord> deadLetterNotifications() {
		return new ArrayList<>(deadLetterNotifications);
	}

	private void addEntry(Deque<NotificationRecord> target, NotificationRecord entry) {
		target.addFirst(entry);
		while (target.size() > MAX_ENTRIES) {
			target.pollLast();
		}
	}

}
