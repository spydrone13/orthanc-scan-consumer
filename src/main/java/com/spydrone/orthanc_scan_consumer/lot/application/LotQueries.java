package com.spydrone.orthanc_scan_consumer.lot.application;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.history.LotStageEventRepository;

/** The read side for lots. Never changes anything. */
@Service
@Transactional(readOnly = true)
public class LotQueries {

	private final LotViewRepository lots;
	private final LotStageEventRepository history;

	LotQueries(LotViewRepository lots, LotStageEventRepository history) {
		this.lots = lots;
		this.history = history;
	}

	/** All lots, most recently updated first. */
	public List<LotView> list() {
		return lots.findAllNewestFirst();
	}

	public LotView get(String lotId) {
		return lots.findView(lotId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** The lot's scan history, newest first. */
	public List<LotStageEventView> history(String lotId) {
		if (!lots.existsById(lotId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return history.findByLotIdOrderByOccurredAtDesc(lotId).stream().map(LotStageEventView::of).toList();
	}
}
