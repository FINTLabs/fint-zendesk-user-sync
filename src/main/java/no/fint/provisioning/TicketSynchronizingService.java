package no.fint.provisioning;

import lombok.extern.slf4j.Slf4j;
import no.fint.ApplicationConfiguration;
import no.fint.provisioning.model.TicketStatus;
import no.fint.provisioning.model.TicketSynchronizationObject;
import no.fint.zendesk.RateLimiter;
import no.fint.zendesk.ZenDeskTicketService;
import no.fint.zendesk.model.ticket.Ticket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ConditionalOnProperty("fint.zendesk.tickets.enabled")
public class TicketSynchronizingService {

    @Autowired
    private BlockingQueue<TicketSynchronizationObject> ticketQueue;

    @Autowired
    private ApplicationConfiguration configuration;

    @Autowired
    private ZenDeskTicketService zenDeskTicketService;

    @Autowired
    private StatusCache statusCache;

    @Autowired
    private RateLimiter rateLimiter;

    private final AtomicBoolean running = new AtomicBoolean();

    @PostConstruct
    public void init() {
        start();
        log.info("FINT Zendesk ticket service enabled.");
    }

    @Scheduled(fixedDelayString = "${fint.zendesk.ticket.sync.rate:60000}")
    public void start() {
        if (running.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    synchronize();
                } catch (Exception e) {
                    log.info("Stopping due to: {}", e.getMessage());
                } finally {
                    running.set(false);
                }
            }, "TicketService").start();
        }
    }

    private void synchronize() throws InterruptedException {
        log.info("Starting ticket synchronization with {} pending tickets..", ticketQueue.size());
        do {
            TicketSynchronizationObject ticket = ticketQueue.poll(1, TimeUnit.MINUTES);

            if (ticket == null) continue;

            if (ticket.getAttempts().incrementAndGet() > configuration.getTicketSyncMaxRetryAttempts()) {
                log.debug("Unable to synchronize ticket after 10 retries.");
                statusCache.put(ticket.getUuid(), TicketStatus.builder().status(TicketStatus.Status.ERROR).build());
                continue;
            }

            try {
                Ticket ticketResponse = zenDeskTicketService.createTicket(ticket);
                statusCache.put(ticket.getUuid(), TicketStatus.builder()
                        .status(TicketStatus.Status.CREATED)
                        .ticket(ticketResponse)
                        .build()
                );
                log.info("Remaining: {}", rateLimiter.getRemaining());

            } catch (Exception e) {
                log.debug("Adding ticket back in queue for retry.", e);
                ticketQueue.put(ticket);
                break;
            }
        } while (rateLimiter.getRemaining() > 1);
        log.info("Pending tickets: {}", ticketQueue.size());
    }

}
