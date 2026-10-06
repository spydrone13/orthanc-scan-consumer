package com.spydrone.orthanc_scan_consumer.lot.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spydrone.orthanc_scan_consumer.lot.application.ChangeLotStatus;
import com.spydrone.orthanc_scan_consumer.lot.application.LotCommandHandler;
import com.spydrone.orthanc_scan_consumer.lot.application.LotView;
import com.spydrone.orthanc_scan_consumer.lot.application.SetLotHold;

@RestController
@RequestMapping("/api/lots")
public class LotCommandController {

	private final LotCommandHandler commands;

	public LotCommandController(LotCommandHandler commands) {
		this.commands = commands;
	}

	@PutMapping("/{lotId}/status")
	public LotView updateStatus(@PathVariable String lotId, @RequestBody LotStatusUpdate update) {
		return LotView.of(commands.handle(new ChangeLotStatus(lotId, update.status(), Instant.now())));
	}

	@PutMapping("/{lotId}/hold")
	public LotView updateHold(@PathVariable String lotId, @RequestBody LotHoldUpdate update) {
		return LotView.of(commands.handle(new SetLotHold(lotId, update.onHold(), Instant.now())));
	}
}
