package com.ledgerline.scheduledtransfer;

import com.ledgerline.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scheduled-transfers")
@PreAuthorize("hasRole('CUSTOMER')")
public class ScheduledTransferController {
    private final ScheduledTransferService transfers;

    public ScheduledTransferController(ScheduledTransferService transfers) {
        this.transfers = transfers;
    }

    @GetMapping
    public List<ScheduledTransferDtos.Response> list(@AuthenticationPrincipal AppUser user) {
        return transfers.list(user);
    }

    @PostMapping
    public ScheduledTransferDtos.Response create(@AuthenticationPrincipal AppUser user,
                                                   @Valid @RequestBody ScheduledTransferDtos.CreateRequest request) {
        return transfers.create(user, request);
    }

    @PostMapping("/{id}/cancel")
    public ScheduledTransferDtos.Response cancel(@PathVariable UUID id, @AuthenticationPrincipal AppUser user) {
        return transfers.cancel(id, user);
    }
}
