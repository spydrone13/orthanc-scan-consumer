package com.spydrone.orthanc_scan_consumer.lot.events;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * A lot event waiting to be published, or already published (kept for a while, then deleted). Written in
 * the transaction that changed the lot, so an event exists exactly when its change was committed.
 */
@Entity
@Table(name = "lot_outbox_events")
public class OutboxEvent {

	/** Increasing; publish order and the message's sequence. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, length = 36)
	private String eventId;
	private String lotId;
	@Column(nullable = false)
	private String routingKey;
	/** The {@link LotEventMessage} as JSON, without its sequence. */
	@Lob
	@Column(nullable = false)
	private String payload;
	@Column(nullable = false)
	private Instant createdAt;
	/** Null until the broker has confirmed it. */
	private Instant publishedAt;

	protected OutboxEvent() {
	}

	OutboxEvent(String eventId, String lotId, String routingKey, String payload, Instant createdAt) {
		this.eventId = eventId;
		this.lotId = lotId;
		this.routingKey = routingKey;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	void markPublished(Instant at) {
		this.publishedAt = at;
	}

	public Long getId() {
		return id;
	}

	public String getEventId() {
		return eventId;
	}

	public String getLotId() {
		return lotId;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
