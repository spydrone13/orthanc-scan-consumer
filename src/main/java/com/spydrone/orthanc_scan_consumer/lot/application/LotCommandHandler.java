package com.spydrone.orthanc_scan_consumer.lot.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;
import com.spydrone.orthanc_scan_consumer.lot.domain.StageRoutes;

/**
 * The write side for lots: each command loads the aggregate, calls one domain method and saves it.
 * Saving publishes the lot's domain events inside the same transaction (see {@link LotHistoryRecorder}).
 */
@Service
@Transactional
public class LotCommandHandler {

	private final LotRepository lots;
	private final StageRoutes routes;

	public LotCommandHandler(LotRepository lots, StageRoutes routes) {
		this.lots = lots;
		this.routes = routes;
	}

	public Lot handle(ApplyScan command) {
		Lot lot = lots.findById(command.lotId()).orElseGet(() -> Lot.firstScanned(command.lotId()));
		lot.applyScan(command.scan(), routes, command.at());
		return lots.save(lot);
	}

	/** @throws LotNotFoundException if there's no such lot */
	public Lot handle(ChangeLotStatus command) {
		Lot lot = existing(command.lotId());
		lot.changeStatus(command.status(), command.at());
		return lots.save(lot);
	}

	/** @throws LotNotFoundException if there's no such lot */
	public Lot handle(SetLotHold command) {
		Lot lot = existing(command.lotId());
		if (command.onHold()) {
			lot.placeOnHold(command.at());
		}
		else {
			lot.releaseHold(command.at());
		}
		return lots.save(lot);
	}

	private Lot existing(String lotId) {
		return lots.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
	}
}
