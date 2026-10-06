package com.spydrone.orthanc_scan_consumer.lot.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.spydrone.orthanc_scan_consumer.lot.domain.Lot;
import com.spydrone.orthanc_scan_consumer.lot.domain.LotRepository;

/**
 * The write side for lots: each command loads the aggregate, calls one domain method and saves it.
 * Saving publishes the lot's domain events inside the same transaction (see {@link LotHistoryRecorder}).
 */
@Service
@Transactional
public class LotCommandHandler {

	private final LotRepository lots;

	public LotCommandHandler(LotRepository lots) {
		this.lots = lots;
	}

	public Lot handle(ApplyScan command) {
		Lot lot = lots.findById(command.lotId()).orElseGet(() -> Lot.firstScanned(command.lotId()));
		lot.applyScan(command.scan(), command.at());
		return lots.save(lot);
	}

	public Lot handle(ChangeLotStatus command) {
		if (command.status() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
		}
		Lot lot = existing(command.lotId());
		lot.changeStatus(command.status(), command.at());
		return lots.save(lot);
	}

	public Lot handle(SetLotHold command) {
		if (command.onHold() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "onHold is required");
		}
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
		return lots.findById(lotId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown lot: " + lotId));
	}
}
