package com.spydrone.orthanc_scan_consumer.lot.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/** @throws LotNotFoundException if there's no such lot */
	public LotView get(String lotId) {
		return lots.findView(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
	}

	/**
	 * The lot's scan history, newest first.
	 *
	 * @throws LotNotFoundException if there's no such lot
	 */
	public List<LotStageEventView> history(String lotId) {
		if (!lots.existsById(lotId)) {
			throw new LotNotFoundException(lotId);
		}
		return history.findByLotIdOrderByOccurredAtDesc(lotId).stream().map(LotStageEventView::of).toList();
	}
}
