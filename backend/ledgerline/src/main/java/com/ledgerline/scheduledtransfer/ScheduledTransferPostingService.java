package com.ledgerline.scheduledtransfer;

import com.ledgerline.ledger.LedgerService;
import com.ledgerline.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ScheduledTransferPostingService {
    private final LedgerService ledger;

    public ScheduledTransferPostingService(LedgerService ledger) {
        this.ledger = ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void post(AppUser owner, UUID transferId, UUID sourceAccountId, String destinationAccountNumber,
                     BigDecimal amount) {
        ledger.transfer(owner, sourceAccountId, destinationAccountNumber, amount,
                "Scheduled transfer " + transferId, "scheduled-transfer:" + transferId);
    }
}
