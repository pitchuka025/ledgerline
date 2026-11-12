package com.ledgerline.scheduledtransfer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ScheduledTransferPollingJob {
    private final ScheduledTransferExecutionService execution;

    public ScheduledTransferPollingJob(ScheduledTransferExecutionService execution) {
        this.execution = execution;
    }

    @Scheduled(fixedDelayString = "${ledgerline.scheduled-transfers.poll-interval:PT10S}")
    public void processDueTransfers() {
        execution.dueTransferIds(Instant.now()).forEach(execution::execute);
    }
}
