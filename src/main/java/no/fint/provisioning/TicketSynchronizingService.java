package no.fint.provisioning;

import lombok.extern.slf4j.Slf4j;
import no.fint.ApplicationConfiguration;
import no.fint.provisioning.model.TicketSynchronizationObject;
import no.fint.zendesk.ZenDeskTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TicketSynchronizingService {

    @Autowired
    private BlockingQueue<TicketSynchronizationObject> ticketQueue;

    @Autowired
    private ApplicationConfiguration configuration;

    @Autowired
    private ZenDeskTicketService zenDeskTicketService;

    @Scheduled(fixedRateString = "${fint.zendesk.ticket.sync.rate:5000}")
    private void synchronize() throws InterruptedException {
        TicketSynchronizationObject ticket = ticketQueue.poll(1, TimeUnit.SECONDS);

        if (ticket == null) return;

        if (ticket.getAttempts().incrementAndGet() > configuration.getTicketSyncMaxRetryAttempts()) {
            log.debug("Unable to synchronize ticket after 10 retries.");
            return;
        }

        try {
            zenDeskTicketService.createTicket(ticket);
        } catch (Exception e) {
            log.debug("Adding ticket back in queue for retry.");
            ticketQueue.put(ticket);
        }

    }

}
